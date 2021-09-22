package com.opendata.chatbot.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Messages {
    private String id;
    private String type;
    private String text;
    private String filename;
    private String fileSize;
    private String title;
    private String address;
    private String latitude;
    private String longitude;
    private String packageId;
    private String stickerId;
}