package com.example.poc.platform.agent.scenario;

import com.example.poc.platform.agent.scenario.ScenarioDraftDiff.ChangeType;
import com.example.poc.platform.agent.scenario.ScenarioDraftDiff.DiffItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ScenarioDiffService {
    private final ScenarioDslCodec codec;

    public ScenarioDiffService(ScenarioDslCodec codec) {
        this.codec = codec;
    }

    public ScenarioDraftDiff diff(ScenarioDocument base, ScenarioDocument current) {
        List<DiffItem> items = new ArrayList<>();
        Map<String, Object> baseRoot = objectMap(codec.toGeneric(base.scenario()));
        Map<String, Object> currentRoot = objectMap(codec.toGeneric(current.scenario()));
        baseRoot.remove("steps");
        currentRoot.remove("steps");
        compare("", null, baseRoot, currentRoot, items);

        Map<String, Object> baseSteps = stepsById(base);
        Map<String, Object> currentSteps = stepsById(current);
        Set<String> allStepIds = new LinkedHashSet<>(base.stepIds());
        allStepIds.addAll(current.stepIds());
        for (String stepId : allStepIds) {
            String path = "/steps/" + pointer(stepId);
            boolean inBase = baseSteps.containsKey(stepId);
            boolean inCurrent = currentSteps.containsKey(stepId);
            if (!inBase) {
                items.add(new DiffItem(ChangeType.ADD, path, stepId, null, currentSteps.get(stepId)));
                continue;
            }
            if (!inCurrent) {
                items.add(new DiffItem(ChangeType.REMOVE, path, stepId, baseSteps.get(stepId), null));
                continue;
            }
            int beforeIndex = base.stepIds().indexOf(stepId);
            int afterIndex = current.stepIds().indexOf(stepId);
            if (beforeIndex != afterIndex) {
                items.add(new DiffItem(ChangeType.MOVE, path, stepId, beforeIndex, afterIndex));
            }
            compare(path, stepId, baseSteps.get(stepId), currentSteps.get(stepId), items);
        }
        return new ScenarioDraftDiff(items);
    }

    private Map<String, Object> stepsById(ScenarioDocument document) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < document.stepIds().size(); index++) {
            result.put(document.stepIds().get(index), codec.toGeneric(document.scenario().getSteps().get(index)));
        }
        return result;
    }

    private void compare(
            String path,
            String stepId,
            Object before,
            Object after,
            List<DiffItem> items) {
        if (Objects.equals(before, after)) {
            return;
        }
        if (before instanceof Map<?, ?> beforeMap && after instanceof Map<?, ?> afterMap) {
            Set<String> fields = new LinkedHashSet<>();
            beforeMap.keySet().forEach(key -> fields.add(String.valueOf(key)));
            afterMap.keySet().forEach(key -> fields.add(String.valueOf(key)));
            for (String field : fields) {
                boolean inBefore = beforeMap.containsKey(field);
                boolean inAfter = afterMap.containsKey(field);
                String fieldPath = path + "/" + pointer(field);
                if (!inBefore) {
                    items.add(new DiffItem(ChangeType.ADD, fieldPath, stepId, null, afterMap.get(field)));
                } else if (!inAfter) {
                    items.add(new DiffItem(ChangeType.REMOVE, fieldPath, stepId, beforeMap.get(field), null));
                } else {
                    compare(fieldPath, stepId, beforeMap.get(field), afterMap.get(field), items);
                }
            }
            return;
        }
        if (before instanceof List<?> beforeList && after instanceof List<?> afterList) {
            int size = Math.max(beforeList.size(), afterList.size());
            for (int index = 0; index < size; index++) {
                String itemPath = path + "/" + index;
                if (index >= beforeList.size()) {
                    items.add(new DiffItem(ChangeType.ADD, itemPath, stepId, null, afterList.get(index)));
                } else if (index >= afterList.size()) {
                    items.add(new DiffItem(ChangeType.REMOVE, itemPath, stepId, beforeList.get(index), null));
                } else {
                    compare(itemPath, stepId, beforeList.get(index), afterList.get(index), items);
                }
            }
            return;
        }
        items.add(new DiffItem(ChangeType.CHANGE, path.isEmpty() ? "/" : path, stepId, before, after));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return new LinkedHashMap<>((Map<String, Object>) value);
    }

    private String pointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
