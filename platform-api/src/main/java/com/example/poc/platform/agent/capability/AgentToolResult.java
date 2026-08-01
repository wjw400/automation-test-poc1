package com.example.poc.platform.agent.capability;

import com.example.poc.platform.agent.runtime.AgentArtifactOutput;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record AgentToolResult(
        Map<String, Object> output,
        List<AgentArtifactOutput> artifacts) {
    public AgentToolResult {
        output = output == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(output));
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public static AgentToolResult read(Map<String, Object> output) {
        return new AgentToolResult(output, List.of());
    }
}
