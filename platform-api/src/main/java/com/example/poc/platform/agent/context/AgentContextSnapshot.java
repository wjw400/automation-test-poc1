package com.example.poc.platform.agent.context;

import com.example.poc.platform.agent.runtime.AgentArtifactOutput;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentContextSnapshot(
        String id,
        String taskId,
        String turnId,
        String projectId,
        String targetType,
        String targetId,
        Instant createdAt,
        List<AgentContextItem> items) {
    public AgentContextSnapshot {
        items = List.copyOf(items);
    }

    public AgentArtifactOutput toArtifact() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("snapshotId", id);
        content.put("turnId", turnId);
        content.put("projectId", projectId);
        content.put("targetType", targetType);
        content.put("targetId", targetId);
        content.put("createdAt", createdAt.toString());
        content.put("items", items);
        return new AgentArtifactOutput(
                "CONTEXT_SNAPSHOT", targetType, targetId, null, "READY", content);
    }
}
