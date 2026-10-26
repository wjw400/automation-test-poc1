package com.example.poc.platform.agent.suite;

import java.util.List;

public record SuiteDraftDiff(List<DiffItem> items) {
    public SuiteDraftDiff { items = items == null ? List.of() : List.copyOf(items); }
    public record DiffItem(ChangeType type, String path, String entryId, Object before, Object after) {}
    public enum ChangeType { ADD, REMOVE, MOVE, CHANGE }
}
