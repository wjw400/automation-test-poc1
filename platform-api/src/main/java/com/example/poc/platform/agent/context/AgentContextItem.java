package com.example.poc.platform.agent.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AgentContextItem(
        int priority,
        String type,
        String scope,
        String targetId,
        String revision,
        String trustLevel,
        Map<String, Object> content) {
    public AgentContextItem {
        content = content == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(content));
    }
}
