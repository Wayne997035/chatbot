package com.opendata.chatbot.controller;

import com.opendata.chatbot.dao.User;
import com.opendata.chatbot.service.LineService;
import com.opendata.chatbot.service.OpenDataCwb;
import com.opendata.chatbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class WebController {

    private final LineService lineServiceImpl;
    private final UserService userServiceImpl;
    private final OpenDataCwb openDataCwbImpl;

    /*
     * LineBot WebHook 驗證回訊息
     */
    @PostMapping("/webHook")
    public ResponseEntity<String> webHook(@RequestBody String requestBody,
                                          @RequestHeader("X-Line-Signature") String line_headers) {
        log.debug("WebHook request received");
        return lineServiceImpl.WebHook(requestBody, line_headers);
    }

    @GetMapping(value = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<User> getAllUser() {
        return userServiceImpl.getAllUsers();
    }

    @GetMapping(value = "/openDataUpdate", produces = MediaType.APPLICATION_JSON_VALUE)
    public void openDataUpdate() {
        openDataCwbImpl.cityCwb();
    }
}
