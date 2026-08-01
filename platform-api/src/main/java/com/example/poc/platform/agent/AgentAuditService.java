package com.example.poc.platform.agent;

import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.AgentAuditEventEntity;
import com.example.poc.platform.persistence.repo.AgentAuditEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentAuditService {
    private final AgentAuditEventRepository repository;
    private final AgentContextRedactor redactor;
    private final JsonCodec jsonCodec;

    public AgentAuditService(
            AgentAuditEventRepository repository,
            AgentContextRedactor redactor,
            JsonCodec jsonCodec) {
        this.repository = repository;
        this.redactor = redactor;
        this.jsonCodec = jsonCodec;
    }

    @Transactional
    public void record(
            String taskId,
            String projectId,
            String actorId,
            String eventType,
            Map<String, Object> payload) {
        AgentAuditEventEntity entity = new AgentAuditEventEntity();
        entity.setTaskId(taskId);
        entity.setProjectId(projectId);
        entity.setActorId(actorId);
        entity.setEventType(eventType);
        entity.setPayloadJson(jsonCodec.write(redactedMap(payload)));
        repository.saveAndFlush(entity);
    }

    @Transactional(readOnly = true)
    public List<AgentAuditRecord> list(String taskId, String actorId) {
        return repository.findByTaskIdAndActorIdOrderByCreatedAtAsc(taskId, actorId).stream()
                .map(this::toRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentAuditSummary summary(String projectId, String actorId) {
        List<AgentAuditEventEntity> events = repository.findByProjectIdAndActorIdOrderByCreatedAtAsc(projectId, actorId);
        Set<String> tasks = events.stream()
                .filter(event -> "task.created".equals(event.getEventType()))
                .map(AgentAuditEventEntity::getTaskId)
                .collect(java.util.stream.Collectors.toSet());
        long completed = count(events, "task.completed");
        long failed = count(events, "task.failed");
        long applied = count(events, "decision.draft_applied_to_editor");
        long saved = count(events, "decision.scenario_saved");
        long positive = count(events, "feedback.positive");
        long negative = count(events, "feedback.negative");
        long validationPassed = countValidation(events, "VALID");
        long validationFailed = countValidation(events, "INVALID");
        long manuallyModified = events.stream()
                .filter(event -> "decision.scenario_saved".equals(event.getEventType()))
                .map(event -> jsonCodec.readObjectMap(event.getPayloadJson()).get("manuallyModified"))
                .filter(Boolean.TRUE::equals)
                .count();
        return new AgentAuditSummary(
                tasks.size(), completed, failed, validationPassed, validationFailed,
                applied, saved, manuallyModified, positive, negative,
                ratio(completed, tasks.size()),
                ratio(validationFailed, validationPassed + validationFailed),
                ratio(saved, completed));
    }

    private long count(List<AgentAuditEventEntity> events, String type) {
        return events.stream().filter(event -> type.equals(event.getEventType())).count();
    }

    private long countValidation(List<AgentAuditEventEntity> events, String status) {
        return events.stream()
                .filter(event -> "artifact.persisted".equals(event.getEventType()))
                .map(event -> jsonCodec.readObjectMap(event.getPayloadJson()))
                .filter(payload -> "SCENARIO_VALIDATION".equals(payload.get("artifactType")))
                .filter(payload -> status.equals(payload.get("status")))
                .count();
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> redactedMap(Map<String, Object> payload) {
        Object redacted = redactor.redact(payload == null ? Map.of() : payload);
        return redacted instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private AgentAuditRecord toRecord(AgentAuditEventEntity entity) {
        return new AgentAuditRecord(
                entity.getId(), entity.getTaskId(), entity.getProjectId(), entity.getActorId(),
                entity.getEventType(), jsonCodec.readObjectMap(entity.getPayloadJson()), entity.getCreatedAt());
    }

    public record AgentAuditRecord(
            String id, String taskId, String projectId, String actorId,
            String eventType, Map<String, Object> payload, Instant createdAt) {}

    public record AgentAuditSummary(
            long taskCount,
            long completedCount,
            long failedCount,
            long validationPassedCount,
            long validationFailedCount,
            long appliedCount,
            long savedCount,
            long manuallyModifiedCount,
            long positiveFeedbackCount,
            long negativeFeedbackCount,
            double successRate,
            double validationFailureRate,
            double adoptionRate) {}
}
