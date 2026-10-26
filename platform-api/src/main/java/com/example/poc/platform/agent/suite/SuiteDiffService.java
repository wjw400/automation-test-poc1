package com.example.poc.platform.agent.suite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SuiteDiffService {
    public SuiteDraftDiff diff(SuiteDraftDocument base, SuiteDraftDocument current) {
        List<SuiteDraftDiff.DiffItem> items = new ArrayList<>();
        compare("/name", null, base.name(), current.name(), items); compare("/description", null, base.description(), current.description(), items); compare("/status", null, base.status(), current.status(), items);
        Map<String, SuiteDraftDocument.Entry> before = map(base.entries()); Map<String, SuiteDraftDocument.Entry> after = map(current.entries());
        Set<String> ids = new LinkedHashSet<>(before.keySet()); ids.addAll(after.keySet());
        for (String id : ids) { String path = "/entries/" + id; if (!before.containsKey(id)) items.add(new SuiteDraftDiff.DiffItem(SuiteDraftDiff.ChangeType.ADD, path, id, null, after.get(id))); else if (!after.containsKey(id)) items.add(new SuiteDraftDiff.DiffItem(SuiteDraftDiff.ChangeType.REMOVE, path, id, before.get(id), null)); else { int beforeIndex = base.entries().indexOf(before.get(id)); int afterIndex = current.entries().indexOf(after.get(id)); if (beforeIndex != afterIndex) items.add(new SuiteDraftDiff.DiffItem(SuiteDraftDiff.ChangeType.MOVE, path, id, beforeIndex, afterIndex)); compare(path, id, before.get(id), after.get(id), items); } }
        return new SuiteDraftDiff(items);
    }
    private Map<String, SuiteDraftDocument.Entry> map(List<SuiteDraftDocument.Entry> entries) { Map<String, SuiteDraftDocument.Entry> map = new LinkedHashMap<>(); entries.forEach(entry -> map.put(entry.id(), entry)); return map; }
    private void compare(String path, String entryId, Object before, Object after, List<SuiteDraftDiff.DiffItem> items) { if (!Objects.equals(before, after)) items.add(new SuiteDraftDiff.DiffItem(SuiteDraftDiff.ChangeType.CHANGE, path, entryId, before, after)); }
}
