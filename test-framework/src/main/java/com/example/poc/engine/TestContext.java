package com.example.poc.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class TestContext {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public void put(String key, Object value) {
        values.put(key, value);
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(values);
    }
}
