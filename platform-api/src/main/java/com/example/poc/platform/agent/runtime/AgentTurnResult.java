package com.example.poc.platform.agent.runtime;

import java.util.List;

public record AgentTurnResult(
        String content,
        long promptTokens,
        long completionTokens,
        List<AgentArtifactOutput> artifacts) {
    public AgentTurnResult {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }
}
