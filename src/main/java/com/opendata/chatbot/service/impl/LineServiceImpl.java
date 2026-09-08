package com.opendata.chatbot.service.impl;

import com.opendata.chatbot.dao.User;
import com.opendata.chatbot.dao.WeatherForecastDto;
import com.opendata.chatbot.entity.EventWrapper;
import com.opendata.chatbot.entity.Messages;
import com.opendata.chatbot.entity.ReplyMessage;
import com.opendata.chatbot.service.*;
import com.opendata.chatbot.util.HeadersUtil;
import com.opendata.chatbot.util.JsonConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineServiceImpl implements LineService {

    @Value("${spring.line.channelSecret}")
    private String channelSecret;

    @Value("${spring.line.channelToken}")
    private String channelToken;

    @Value("${spring.line.replyUrl}")
    private String replyUrl;

    private final AesECB aesECBImpl;
    private final UserService userServiceImpl;
    private final HeadersUtil headersUtil;
    private final RestTemplate restTemplate;
    private final OpenDataCwb openDataCwbImpl;
    private final WeatherForecastService weatherForecastServiceImpl;

    @Override
    public ResponseEntity<String> WebHook(String requestBody, String line_headers) {
        // 開執��緒去處理使用者訊息，先 return Line Http 200 訊息
        CompletableFuture.runAsync(() -> {
            // 驗證line傳過來的訊息
            if (validateLineHeader(requestBody, line_headers)) {
                log.info("驗證成功");
                replyMessage(requestBody);
            } else {
                throw new RuntimeException("validateLineHeader line_headers validate Error");
            }
        }).exceptionally(ex -> {
            // [F1-02] 原本例外會被 CompletableFuture 保存後永不輸出，一行 log 都沒有
            log.error("[F1-02] WebHook async processing failed: {}", ex.getMessage(), ex);
            return null;
        });
        return new ResponseEntity<>(HttpStatus.OK);
    }

    // [F1-02] 三處 restTemplate.exchange 共用：一律記錄 status；失敗（非 2xx）時連 body 一起記，
    // body 截斷到 500 字元以內避免噴 log
    private void logReplyStatus(String context, ResponseEntity<String> response) {
        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("[F1-02] {} status={}", context, response.getStatusCode());
        } else {
            var body = response.getBody();
            var truncatedBody = (body != null && body.length() > 500) ? body.substring(0, 500) : body;
            log.warn("[F1-02] {} status={}, body={}", context, response.getStatusCode(), truncatedBody);
        }
    }

    @Override
    public boolean validateLineHeader(String requestBody, String lineHeaders) {
        log.debug("lineHeaders = {}", lineHeaders);
        var secret = aesECBImpl.aesDecrypt(channelSecret);
        var key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] source = requestBody.getBytes(StandardCharsets.UTF_8);
            var signature = Base64.getEncoder().encodeToString(mac.doFinal(source));
            if (signature.equals(lineHeaders)) {
                return true;
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("validateLineHeader : {}", e.getMessage(), e);
        }
        return false;
    }

    @Override
    public void replyMessage(String requestBody) {
        // 回訊息 URL
        var url = new String(Base64.getDecoder().decode(replyUrl), StandardCharsets.UTF_8);
        //送出參數
        var headers = headersUtil.setHeaders();
        var messagesList = new LinkedList<Messages>();
        var eventWrapper = JsonConverter.toObject(requestBody, EventWrapper.class);
        log.trace("eventWrapper = {}", eventWrapper);

        // 取出User Event 的 資料，後續打API使用
        eventWrapper.getEvents().forEach(event -> {
            try {
                var userId = event.getSource().getUserId();
                log.info("event = {}", event);

                // 開執行序去存 User 資料 DB
                CompletableFuture.runAsync(() -> {
                    if (userServiceImpl.getUserById(userId) == null) {
                        var user = new User();
                        user.setId(userId);
                        user.setCreateTime(LocalDateTime.now());
                        userServiceImpl.saveUser(user);
                    }
                }).exceptionally(ex -> {
                    // [F1-02] 存 user 是旁路，失敗不得影響回覆流程，只記錄
                    log.error("[F1-02] save user async failed, userId={}: {}", userId, ex.getMessage(), ex);
                    return null;
                });

                if (event.getMessage() != null && event.getMessage().getType().equals("text")) {
                    replyWeatherForecast(event.getMessage().getText(), event.getReplyToken());
                } else if (event.getMessage() != null && event.getMessage().getType().equals("location")) {
                    var address = event.getMessage().getAddress();
                    String city = "";
                    String dist = "";
                    if (address.contains("市") && address.contains("區")) {
                        city = address.substring(address.indexOf("市") - 2, address.indexOf("市") + 1);
                        dist = address.substring(address.indexOf("市") + 1, address.indexOf("區") + 1);
                    } else if (address.contains("縣")) {
                        city = address.substring(address.indexOf("縣") - 2, address.indexOf("縣") + 1);
                        if (address.contains("市")) {
                            dist = address.substring(address.indexOf("縣") + 1, address.indexOf("市") + 1);
                        } else if (address.contains("鄉")) {
                            dist = address.substring(address.indexOf("縣") + 1, address.indexOf("鄉") + 1);
                        } else if (address.contains("鎮")) {
                            dist = address.substring(address.indexOf("縣") + 1, address.indexOf("鎮") + 1);
                        }
                    }
                    if (city.isEmpty() || dist.isEmpty()) {
                        // [F1-03] 三類都取不出可用地址：無市無縣／有市無區且無縣／有縣但無市鄉鎮，
                        // 不再落入原本的縣分支做 substring 越界，改回可辨識訊息
                        var messages = new Messages();
                        messages.setType("text");
                        messages.setText("無法解析地址，請直接輸入地區名稱");
                        messagesList.add(messages);
                        var response = restTemplate.exchange(url, HttpMethod.POST,
                                new HttpEntity<>(JsonConverter.toJsonString(new ReplyMessage(event.getReplyToken(), messagesList)), headers), String.class);
                        logReplyStatus("replyMessage(address parse failed)", response);
                    } else {
                        replyWeatherLocation(city, dist, event.getReplyToken());
                    }
                } else {
                    // [F1-03] event.getMessage() 為 null（postback／貼圖等無 message 欄位的事件）
                    // 或型別無法辨識，沿用既有「無法解析內容」文案與流程
                    var messages = new Messages();
                    messages.setType("text");
                    messages.setText("無法解析內容");
                    messagesList.add(messages);
                    var response = restTemplate.exchange(url, HttpMethod.POST,
                            new HttpEntity<>(JsonConverter.toJsonString(new ReplyMessage(event.getReplyToken(), messagesList)), headers), String.class);
                    logReplyStatus("replyMessage(unrecognized)", response);
                }
            } catch (Exception e) {
                // [F1-02] 單一 event 處理失敗不得拖垮同批其他 event，記錄後繼續處理下一個
                log.error("[F1-02] process single LINE event failed: {}", e.getMessage(), e);
            }
        });

    }

    @Override
    public ResponseEntity<String> replyWeatherForecast(String dist, String replyToken) {
        // 回訊息 URL
        var url = new String(Base64.getDecoder().decode(replyUrl), StandardCharsets.UTF_8);
        //送出參數
        var headers = headersUtil.setHeaders();
        var messagesList = new LinkedList<Messages>();
        var low = weatherForecastServiceImpl.findByDistrict(dist);

        log.info("findByDistrict dist='{}', result size={}", dist, low.size());

        if (low.isEmpty()) {
            var messages = new Messages();
            messages.setType("text");
            messages.setText("無法解析輸入內容，請輸入地區。Ex: 士林區、羅東鎮、礁溪鄉、宜蘭市等");
            messagesList.add(messages);
        } else {
            low.forEach(openData -> {
                var messages = weatherForecastLineMessageReply(openData);
                messagesList.add(messages);
            });
        }

        var replyMessage = new ReplyMessage(replyToken, messagesList);
        var response = restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(JsonConverter.toJsonString(replyMessage), headers),
                String.class);
        logReplyStatus("replyWeatherForecast", response);
        // [F1-01] 快取寫入搬到回覆送出之後，Redis 故障不再拖累或中斷回覆
        weatherForecastServiceImpl.cacheDistrict(dist, low);
        return response;
    }

    @Override
    public ResponseEntity<String> replyWeatherLocation(String city, String dist, String replyToken) {
        // 回訊息 URL
        var url = new String(Base64.getDecoder().decode(replyUrl), StandardCharsets.UTF_8);
        //送出參數
        var headers = headersUtil.setHeaders();
        var messagesList = new LinkedList<Messages>();
        var openData = weatherForecastServiceImpl.findByDistrictAndCity(dist, city);

        // [F1-05] findByDistrictAndCity 回 null 是合法路徑（查無資料），不可再交給
        // weatherForecastLineMessageReply（其內部會呼叫 openData.getWeatherForecast() 而 NPE）
        if (openData != null) {
            var messages = weatherForecastLineMessageReply(openData);
            messagesList.add(messages);
        } else {
            var messages = new Messages();
            messages.setType("text");
            messages.setText("查不到該地區的天氣資料，請換一個地區試試");
            messagesList.add(messages);
        }

        var replyMessage = new ReplyMessage(replyToken, messagesList);

        var response = restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(JsonConverter.toJsonString(replyMessage), headers), String.class);
        logReplyStatus("replyWeatherLocation", response);

        if (openData != null) {
            // [F1-01] 快取寫入搬到回覆送出之後，Redis 故障不再拖累或中斷回覆
            weatherForecastServiceImpl.cacheDistrictAndCity(dist, city, openData);
        }
        return response;
    }

    @Override
    public Messages weatherForecastLineMessageReply(WeatherForecastDto openData) {
        var messages = new Messages();
        var msg = new StringBuilder();
        messages.setType("text");
        openData.getWeatherForecast().forEach(wf -> {
            switch (wf.getElementName()) {
                case "PoP12h", "PoP6h", "RH" ->
                    msg.append(wf.getDescription()).append(" : ").append(wf.getValue()).append("%").append("\n");
                case "Wx", "CI", "WeatherDescription", "WS", "WD" ->
                    msg.append(wf.getDescription()).append(" : ").append(wf.getValue()).append("\n");
                case "AT", "T", "Td" ->
                    msg.append(wf.getDescription()).append(" : ").append(wf.getValue()).append("\u2103").append("\n");
                default -> { }
            }
            messages.setText((openData.getCity() + " " + openData.getDistrict() + "\n天氣預報:\n" + msg));
        });
        return messages;
    }

    @Override
    public ResponseEntity<String> pushMessage(String json) {
        var headers = headersUtil.setHeaders();
        return null;
    }
}
