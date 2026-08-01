package com.example.poc.platform.agent.runtime;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record AgentArtifactOutput(
        String artifactType,
        String targetType,
        String targetId,
        Long baseRevision,
        String status,
        Map<String, Object> content) {
    public AgentArtifactOutput {
        content = content == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(content));
    }
}
