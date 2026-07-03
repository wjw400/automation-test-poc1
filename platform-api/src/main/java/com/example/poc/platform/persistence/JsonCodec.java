package com.example.poc.platform.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonCodec {
    private final ObjectMapper objectMapper;

    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize JSON value", e);
        }
    }

    public List<String> readStringList(String json) {
        return read(json, new TypeReference<>() {}, List.of());
    }

    public Map<String, String> readStringMap(String json) {
        return read(json, new TypeReference<>() {}, Map.of());
    }

    public Map<String, Object> readObjectMap(String json) {
        return read(json, new TypeReference<>() {}, Map.of());
    }

    public <T> T read(String json, TypeReference<T> type, T defaultValue) {
        try {
            return json == null || json.isBlank() ? defaultValue : objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON value", e);
        }
    }
}
