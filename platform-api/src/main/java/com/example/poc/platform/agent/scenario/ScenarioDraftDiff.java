package com.example.poc.platform.agent.scenario;

import java.util.List;

public record ScenarioDraftDiff(List<DiffItem> items) {
    public ScenarioDraftDiff {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record DiffItem(ChangeType type, String path, String stepId, Object before, Object after) {}

    public enum ChangeType {
        ADD,
        REMOVE,
        MOVE,
        CHANGE
    }
}
