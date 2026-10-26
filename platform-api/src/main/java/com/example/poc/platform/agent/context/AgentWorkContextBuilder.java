package com.example.poc.platform.agent.context;

import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.scenario.ScenarioDraftService;
import com.example.poc.platform.agent.scenario.ScenarioDslCodec;
import com.example.poc.platform.agent.suite.SuiteDraftService;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ScenarioDraftEntity;
import com.example.poc.platform.persistence.repo.AgentEventRepository;
import com.example.poc.platform.persistence.repo.ScenarioDraftRepository;
import com.example.poc.platform.persistence.repo.SuiteDraftRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Rebuilds bounded work state from durable resources, without replaying tool side effects. */
@Component
public class AgentWorkContextBuilder {
    private static final int MAX_CHARACTERS = 12_000;
    private static final int MAX_DRAFTS = 8;
    private final ScenarioDraftRepository scenarioDrafts;
    private final SuiteDraftRepository suiteDrafts;
    private final AgentEventRepository events;
    private final ScenarioDraftService scenarioService;
    private final SuiteDraftService suiteService;
    private final JsonCodec json;
    private final AgentContextRedactor redactor;
    private final ScenarioDslCodec codec;

    public AgentWorkContextBuilder(
            ScenarioDraftRepository scenarioDrafts, SuiteDraftRepository suiteDrafts, AgentEventRepository events,
            ScenarioDraftService scenarioService, SuiteDraftService suiteService,
            JsonCodec json, AgentContextRedactor redactor, ScenarioDslCodec codec) {
        this.scenarioDrafts = scenarioDrafts;
        this.suiteDrafts = suiteDrafts;
        this.events = events;
        this.scenarioService = scenarioService;
        this.suiteService = suiteService;
        this.json = json;
        this.redactor = redactor;
        this.codec = codec;
    }

    public AgentContextItem build(AgentTaskRecord task) {
        return build(task, 0);
    }

    public AgentContextItem build(AgentTaskRecord task, int page) {
        Map<String, Object> state = new LinkedHashMap<>();
        List<Map<String, Object>> drafts = new ArrayList<>();
        state.put("drafts", drafts);
        state.put("recentFailures", recentEvents(task.id(), "tool.failed"));
        state.put("recentPauses", recentEvents(task.id(), "task.paused"));
        state.put("confirmedSaves", recentEvents(task.id(), "decision.scenario_saved"));
        var candidates = scenarioDrafts
                .findByAgentTaskIdAndProjectIdAndCreatedByAndStatusInOrderByUpdatedAtDesc(
                        task.id(), task.projectId(), task.createdBy(), List.of("ACTIVE", "PUBLISHED"),
                        PageRequest.of(page, MAX_DRAFTS));
        state.put("page", page);
        state.put("hasMore", candidates.hasNext());
        boolean truncated = candidates.hasNext();
        for (ScenarioDraftEntity entity : candidates) {
            if (!inScope(task, entity)) continue;
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("draftId", entity.getId());
            summary.put("type", "SCENARIO_DRAFT");
            summary.put("status", entity.getStatus());
            summary.put("revision", entity.getRevision());
            summary.put("sourceRevision", entity.getSourceRevision());
            summary.put("scenarioId", entity.getScenarioId());
            if ("ACTIVE".equals(entity.getStatus())) {
                try {
                    var draft = scenarioService.get(entity.getId());
                    summary.put("name", text(codec.parse(draft.content()).getName()));
                    summary.put("steps", draft.steps().stream().limit(12).map(step -> Map.of(
                            "id", step.id(), "index", step.index(), "name", text(step.name()))).toList());
                    summary.put("stepCount", draft.steps().size());
                    summary.put("validation", compactValidation(draft.validation()));
                } catch (IllegalArgumentException exception) {
                    summary.put("readError", text(exception.getMessage()));
                }
            }
            drafts.add(summary);
        }
        if ("SUITE".equals(task.targetType())) {
            var entity = suiteDrafts.findFirstByAgentTaskIdAndStatusOrderByUpdatedAtDesc(task.id(), "ACTIVE").orElse(null);
            if (entity != null && task.projectId().equals(entity.getProjectId())
                    && task.targetId().equals(entity.getSuiteId()) && task.createdBy().equals(entity.getCreatedBy())) {
                var draft = suiteService.get(entity.getId());
                drafts.add(new LinkedHashMap<>(Map.of(
                        "type", "SUITE_DRAFT", "draftId", draft.id(), "revision", draft.revision(),
                        "sourceRevision", draft.sourceRevision(), "status", draft.status(),
                        "entryCount", draft.document().entries().size(), "validation", compactValidation(draft.validation()))));
            }
        }
        // Trim detail, never the IDs needed to read and reuse an already-created Draft.
        for (Map<String, Object> summary : drafts) {
            if (json.write(state).length() <= MAX_CHARACTERS - 1_000) break;
            summary.remove("steps");
            if (summary.get("validation") instanceof Map<?, ?> validation) {
                summary.put("validation", Map.of("valid", validation.get("valid"), "issueCount", validation.get("issueCount")));
            }
            truncated = true;
        }
        state.put("truncated", truncated);
        // Names, validation messages and tool messages are data, even inside a platform-generated snapshot.
        @SuppressWarnings("unchecked")
        Map<String, Object> safe = (Map<String, Object>) redactor.redact(state);
        return new AgentContextItem(30, "TASK_WORK_STATE", "TASK", task.id(), "v1", "UNTRUSTED_BUSINESS_CONTENT", safe);
    }

    private boolean inScope(AgentTaskRecord task, ScenarioDraftEntity draft) {
        if ("SUITE".equals(task.targetType())) return task.targetId().equals(draft.getSourceSuiteId());
        return "SCENARIO".equals(task.targetType()) && task.targetId().equals(draft.getScenarioId());
    }

    private Map<String, Object> compactValidation(Object validation) {
        Map<String, Object> value = json.readObjectMap(json.write(validation));
        List<?> issues = (List<?>) value.getOrDefault("issues", List.of());
        List<Object> compact = issues.stream().limit(5).map(issue -> {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) issue).forEach((key, item) -> result.put(String.valueOf(key), text(item)));
            return (Object) result;
        }).toList();
        return Map.of("valid", value.get("valid"), "issueCount", issues.size(), "issues", compact);
    }

    private List<Map<String, Object>> recentEvents(String taskId, String type) {
        List<Map<String, Object>> recent = new ArrayList<>();
        for (var event : events.findTop3ByTaskIdAndEventTypeOrderBySequenceDesc(taskId, type)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sequence", event.getSequence());
            result.put("eventType", type);
            Map<String, Object> payload = json.readObjectMap(event.getPayloadJson());
            for (String field : List.of("turnId", "toolName", "code", "message", "path", "commandIndex",
                    "pauseReason", "internalCode", "draftId", "scenarioId", "addedToSuite", "suiteId")) {
                Object value = payload.get(field);
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    result.put(field, value instanceof String ? text(value) : value);
                }
            }
            recent.add(result);
            if (json.write(recent).length() > 1_200) {
                recent.remove(recent.size() - 1);
                break;
            }
        }
        return recent;
    }

    private String text(Object value) {
        String result = Objects.toString(value, "");
        return result.length() <= 200 ? result : result.substring(0, 200) + "…";
    }
}
