package com.example.poc.platform.agent.runtime;

import java.util.List;

public record AgentRuntimeCapabilities(
        boolean available,
        boolean streaming,
        boolean structuredOutput,
        boolean toolCalling,
        String defaultModel,
        List<String> models) {}
