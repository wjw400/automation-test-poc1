package com.example.poc.platform.agent.runtime;

public record AgentRuntimeCapabilities(
        boolean available,
        boolean streaming,
        boolean structuredOutput,
        boolean toolCalling,
        String defaultModel) {}
