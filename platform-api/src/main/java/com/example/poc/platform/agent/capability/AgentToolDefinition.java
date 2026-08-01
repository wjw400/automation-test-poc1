package com.example.poc.platform.agent.capability;

public record AgentToolDefinition(
        String name,
        String description,
        String inputSchema,
        String outputSchema,
        String category,
        String sideEffect,
        long timeoutMillis,
        int maxOutputCharacters) {}
