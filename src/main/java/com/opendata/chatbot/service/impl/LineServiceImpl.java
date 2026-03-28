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
        });
        return new ResponseEntity<>(HttpStatus.OK);
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
            });

            if (event.getMessage().getType().equals("text")) {
                replyWeatherForecast(event.getMessage().getText(), event.getReplyToken());
            } else if (event.getMessage().getType().equals("location")) {
                var address = event.getMessage().getAddress();
                String city = "";
                String dist = "";
                if (address.contains("市") && address.contains("區")) {
                    city = address.substring(address.indexOf("市") - 2, address.indexOf("市") + 1);
                    dist = address.substring(address.indexOf("市") + 1, address.indexOf("區") + 1);
                } else {
                    city = address.substring(address.indexOf("縣") - 2, address.indexOf("縣") + 1);
                    if (address.contains("市")) {
                        dist = address.substring(address.indexOf("縣") + 1, address.indexOf("市") + 1);
                    } else if (address.contains("鄉")) {
                        dist = address.substring(address.indexOf("縣") + 1, address.indexOf("鄉") + 1);
                    } else if (address.contains("鎮")) {
                        dist = address.substring(address.indexOf("縣") + 1, address.indexOf("鎮") + 1);
                    }
                }
                replyWeatherLocation(city, dist, event.getReplyToken());
            } else {
                var messages = new Messages();
                messages.setType("text");
                messages.setText("無法解析內容");
                messagesList.add(messages);
                restTemplate.exchange(url, HttpMethod.POST,
                        new HttpEntity<>(JsonConverter.toJsonString(new ReplyMessage(event.getReplyToken(), messagesList)), headers), String.class);
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
        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(JsonConverter.toJsonString(replyMessage), headers),
                String.class);
    }

    @Override
    public ResponseEntity<String> replyWeatherLocation(String city, String dist, String replyToken) {
        // 回訊息 URL
        var url = new String(Base64.getDecoder().decode(replyUrl), StandardCharsets.UTF_8);
        //送出參數
        var headers = headersUtil.setHeaders();
        var messagesList = new LinkedList<Messages>();
        var openData = weatherForecastServiceImpl.findByDistrictAndCity(dist, city);
        // 單比結果
        var messages = weatherForecastLineMessageReply(openData);

        messagesList.add(messages);

        var replyMessage = new ReplyMessage(replyToken, messagesList);

        return restTemplate.exchange(url, HttpMethod.POST,
                new HttpEntity<>(JsonConverter.toJsonString(replyMessage), headers), String.class);
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
