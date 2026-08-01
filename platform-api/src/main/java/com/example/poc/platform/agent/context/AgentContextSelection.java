package com.example.poc.platform.agent.context;

import java.util.LinkedHashMap;
import java.util.List;

public record AgentContextSelection(
        List<AgentContextReference> included,
        List<AgentContextReference> excluded) {
    public AgentContextSelection {
        included = distinct(included);
        excluded = distinct(excluded);
    }

    public static AgentContextSelection empty() {
        return new AgentContextSelection(List.of(), List.of());
    }

    public boolean includes(String type, String id) {
        return included.stream().anyMatch(reference -> reference.type().equals(type) && reference.id().equals(id));
    }

    public boolean excludes(String type, String id) {
        return excluded.stream().anyMatch(reference -> reference.type().equals(type) && reference.id().equals(id));
    }

    private static List<AgentContextReference> distinct(List<AgentContextReference> references) {
        if (references == null || references.isEmpty()) return List.of();
        LinkedHashMap<String, AgentContextReference> result = new LinkedHashMap<>();
        references.forEach(reference -> result.put(reference.key(), reference));
        return List.copyOf(result.values());
    }
}
