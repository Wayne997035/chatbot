package com.opendata.chatbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonConverter {
    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.findAndRegisterModules();
    }

    public static <T> String toJsonString(T obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON serialization failed: {}", e.getMessage(), e);
            return null;
        }
    }

    public static <T> T toObject(String json, Class<T> obj) {
        try {
            return MAPPER.readValue(json, obj);
        } catch (Exception e) {
            log.error("JSON deserialization failed: {}", e.getMessage(), e);
            return null;
        }
    }

    // 轉Array (Map) 物件
    public static <T> T toArrayObject(String json, TypeReference<T> valueTypeRef) {
        try {
            return MAPPER.readValue(json, valueTypeRef);
        } catch (Exception e) {
            log.error("JSON array deserialization failed: {}", e.getMessage(), e);
            return null;
        }
    }
}
