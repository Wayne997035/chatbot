package com.opendata.chatbot.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.opendata.chatbot.dao.WeatherForecastDto;
import com.opendata.chatbot.service.AesECB;
import com.opendata.chatbot.service.OpenDataCwb;
import com.opendata.chatbot.service.UserService;
import com.opendata.chatbot.service.WeatherForecastService;
import com.opendata.chatbot.util.HeadersUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F1-02（記錄 + 隔離）、F1-01c（回覆順序）、F1-03（null message / 地址越界）、
 * F1-05（查無資料防護）—— 涵蓋 LineServiceImpl。
 *
 * [F1-04] 這 7 支測試被 test 任務實際跑過（非 build.gradle 舊設定下的 SKIPPED / NO-SOURCE），
 * 是 F1-04 兩層 exclude + useJUnitPlatform() 生效的直接證據。
 */
@ExtendWith(MockitoExtension.class)
class LineServiceImplTest {

    @Mock
    private AesECB aesECB;
    @Mock
    private UserService userService;
    @Mock
    private HeadersUtil headersUtil;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private OpenDataCwb openDataCwb;
    @Mock
    private WeatherForecastService weatherForecastService;

    private LineServiceImpl newTarget() {
        var target = new LineServiceImpl(aesECB, userService, headersUtil, restTemplate, openDataCwb, weatherForecastService);
        ReflectionTestUtils.setField(target, "replyUrl",
                Base64.getEncoder().encodeToString("http://example.invalid/reply".getBytes(StandardCharsets.UTF_8)));
        return target;
    }

    private void waitForErrorLog(ListAppender<ILoggingEvent> appender, String fragment, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean found = appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.ERROR
                            && e.getFormattedMessage() != null
                            && e.getFormattedMessage().contains(fragment));
            if (found) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ie);
            }
        }
        fail("等待 " + timeoutMillis + "ms 內未出現含 '" + fragment + "' 的 ERROR log，實際擷取到: " + appender.list);
    }

    // [F1-01c] 回覆順序：LINE reply 的 HTTP 呼叫必須發生在 Redis 寫入（cacheDistrict）之前。
    @Test
    void replyWeatherForecast_exchangeCalledBeforeCacheWrite() {
        var target = newTarget();
        when(headersUtil.setHeaders()).thenReturn(new HttpHeaders());
        var dto = new WeatherForecastDto();
        dto.setCity("台北市");
        dto.setDistrict("士林區");
        dto.setWeatherForecast(List.of());
        var mongoList = List.of(dto);
        when(weatherForecastService.findByDistrict("士林區")).thenReturn(mongoList);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        target.replyWeatherForecast("士林區", "reply-token-1");

        InOrder order = inOrder(restTemplate, weatherForecastService);
        order.verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
        order.verify(weatherForecastService).cacheDistrict(eq("士林區"), eq(mongoList));
    }

    // [F1-02a] :55 的 CompletableFuture.runAsync 原本沒接 exceptionally，例外被吞得一行 log 都沒有。
    // validateLineHeader 簽章比對失敗 → :61 的 RuntimeException → 應該產生一行 ERROR log。
    // 因為跑在 ForkJoinPool.commonPool()，用有上限的輪詢等待（不引入 Awaitility，手寫 poll）。
    @Test
    void webHook_invalidSignature_logsErrorAsynchronously() {
        var target = newTarget();
        when(aesECB.aesDecrypt(any())).thenReturn("test-secret");

        Logger logger = (Logger) LoggerFactory.getLogger(LineServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = target.WebHook("{}", "definitely-wrong-signature");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            waitForErrorLog(appender, "WebHook async processing failed", 3000);
        } finally {
            logger.detachAppender(appender);
        }
    }

    // [F1-02b] :97 forEach 隔離：一批兩個 event，第一個處理時拋例外（source 缺失 → NPE），
    // 第二個仍然收到回覆 —— 斷言 exchange 恰被呼叫 1 次（只有第二個 event 送出）。
    @Test
    void replyMessage_firstEventThrows_secondEventStillReplies() {
        var target = newTarget();
        when(headersUtil.setHeaders()).thenReturn(new HttpHeaders());
        var dto = new WeatherForecastDto();
        dto.setCity("台北市");
        dto.setDistrict("士林區");
        dto.setWeatherForecast(List.of());
        when(weatherForecastService.findByDistrict("士林區")).thenReturn(List.of(dto));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String json = """
                {
                  "destination": "dest",
                  "events": [
                    {
                      "type": "message",
                      "replyToken": "token-1",
                      "message": { "type": "text", "text": "士林區" }
                    },
                    {
                      "type": "message",
                      "replyToken": "token-2",
                      "source": { "type": "user", "userId": "U2" },
                      "message": { "type": "text", "text": "士林區" }
                    }
                  ]
                }
                """;

        assertDoesNotThrow(() -> target.replyMessage(json));

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
    }

    // [F1-03a] event.getMessage() 為 null（postback 事件）不再 NPE，回覆沿用既有「無法解析內容」文案。
    @Test
    void replyMessage_postbackEventWithoutMessage_repliesUnrecognizedFallback() {
        var target = newTarget();
        when(headersUtil.setHeaders()).thenReturn(new HttpHeaders());
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String json = """
                {
                  "destination": "dest",
                  "events": [
                    {
                      "type": "postback",
                      "replyToken": "token-1",
                      "source": { "type": "user", "userId": "U1" },
                      "postback": { "data": "action=confirm" }
                    }
                  ]
                }
                """;

        assertDoesNotThrow(() -> target.replyMessage(json));

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
        assertThat(String.valueOf(captor.getValue().getBody())).contains("無法解析內容");
    }

    // [F1-03b] 地址「台北市」（有市無區、且無縣）—— 最容易漏的一類：原本 :117 的 && 讓它落進
    // :121 的縣分支做 substring(-3,0) 越界。斷言送出的內容，不是只斷言不拋例外：
    // 因為即使還原修復，例外也會被 F1-02 的 per-event try/catch 吞掉不外露；
    // 只有「有沒有送出正確回覆內容」才抓得到這支要守的東西真的變紅（AC4 revert-to-red）。
    @Test
    void replyMessage_locationAddressCityOnlyNoDistrict_repliesAddressParseFallback() {
        var target = newTarget();
        when(headersUtil.setHeaders()).thenReturn(new HttpHeaders());
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String json = """
                {
                  "destination": "dest",
                  "events": [
                    {
                      "type": "message",
                      "replyToken": "token-1",
                      "source": { "type": "user", "userId": "U1" },
                      "message": { "type": "location", "address": "台北市" }
                    }
                  ]
                }
                """;

        target.replyMessage(json);

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
        assertThat(String.valueOf(captor.getValue().getBody())).contains("無法解析地址");
        verify(weatherForecastService, never()).findByDistrictAndCity(any(), any());
    }

    // [F1-03c] 地址不含「市」也不含「縣」——同樣不再 substring 越界，回覆位址解析失敗訊息。
    @Test
    void replyMessage_locationAddressNoCityNoCounty_repliesAddressParseFallback() {
        var target = newTarget();
        when(headersUtil.setHeaders()).thenReturn(new HttpHeaders());
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        String json = """
                {
                  "destination": "dest",
                  "events": [
                    {
                      "type": "message",
                      "replyToken": "token-1",
                      "source": { "type": "user", "userId": "U1" },
                      "message": { "type": "location", "address": "測試路100號" }
                    }
                  ]
                }
                """;

        target.replyMessage(json);

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(String.class));
        assertThat(String.valueOf(captor.getValue().getBody())).contains("無法解析地址");
        verify(weatherForecastService, never()).findByDistrictAndCity(any(), any());
    }

    // [F1-05a] findByDistrictAndCity 回 null 是合法路徑（查無資料），replyWeatherLocation 不得
    // 把 null 交給 weatherForecastLineMessageReply（否則 openData.getWeatherForecast() NPE）。
    @Test
    void replyWeatherLocation_noMongoData_repliesNoDataFallbackWithoutNpe() {
        var target = newTarget();
        when(headersUtil.setHeaders()).thenReturn(new HttpHeaders());
        when(weatherForecastService.findByDistrictAndCity("不存在區", "台北市")).thenReturn(null);
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        var response = assertDoesNotThrow(() -> target.replyWeatherLocation("台北市", "不存在區", "token-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(captor.getValue().getBody())).contains("查不到該地區的天氣資料");
        verify(weatherForecastService, never()).cacheDistrictAndCity(any(), any(), any());
    }
}
