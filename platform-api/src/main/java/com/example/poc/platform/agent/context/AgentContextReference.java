package com.example.poc.platform.agent.context;

import java.util.Locale;

public record AgentContextReference(String type, String id) {
    public AgentContextReference {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Context type is required");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Context id is required");
        type = type.trim().toUpperCase(Locale.ROOT);
        id = id.trim();
    }

    public String key() { return type + ":" + id; }
}
