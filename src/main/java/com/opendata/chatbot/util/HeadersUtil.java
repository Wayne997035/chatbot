package com.opendata.chatbot.util;

import com.opendata.chatbot.service.AesECB;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeadersUtil {

    @Value("${spring.line.channelToken}")
    private String channelToken;

    private final AesECB aesECBImpl;

    public HttpHeaders setHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", "Bearer " + aesECBImpl.aesDecrypt(channelToken));
        return headers;
    }
}
