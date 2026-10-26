package com.example.poc.platform.agent.suite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SuiteDraftDocument(String name, String description, String status, List<Entry> entries) {
    public SuiteDraftDocument {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        status = status == null ? "ACTIVE" : status;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public record Entry(
            String id,
            String scenarioId,
            String displayName,
            boolean enabled,
            Map<String, Object> variableOverrides,
            String environmentId,
            String profileId) {
        public Entry {
            variableOverrides = variableOverrides == null
                    ? Map.of()
                    : Map.copyOf(new LinkedHashMap<>(variableOverrides));
        }
    }

    public SuiteDraftDocument copy() {
        List<Entry> copied = new ArrayList<>();
        for (Entry entry : entries) {
            copied.add(new Entry(entry.id(), entry.scenarioId(), entry.displayName(), entry.enabled(),
                    entry.variableOverrides(), entry.environmentId(), entry.profileId()));
        }
        return new SuiteDraftDocument(name, description, status, copied);
    }
}
