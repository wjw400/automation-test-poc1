package com.example.poc.platform.agent.runtime;

import java.util.Map;

public record AgentRuntimeEvent(String type, Map<String, Object> payload) {
    public AgentRuntimeEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static AgentRuntimeEvent artifact(AgentArtifactOutput artifact) {
        return new AgentRuntimeEvent("artifact.output", Map.of("artifact", artifact));
    }
}
