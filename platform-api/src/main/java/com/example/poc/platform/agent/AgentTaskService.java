package com.example.poc.platform.agent;

import com.example.poc.platform.agent.runtime.AgentArtifactOutput;
import com.example.poc.platform.agent.runtime.AgentConversationMessage;
import com.example.poc.platform.agent.runtime.AgentRuntimeEvent;
import com.example.poc.platform.agent.runtime.AgentRuntimeException;
import com.example.poc.platform.agent.runtime.AgentRuntimeRegistry;
import com.example.poc.platform.agent.runtime.AgentTurnRequest;
import com.example.poc.platform.agent.runtime.AgentTurnResult;
import com.example.poc.platform.agent.context.AgentContextReference;
import com.example.poc.platform.agent.context.AgentContextSelection;
import com.example.poc.platform.agent.context.AgentContextSnapshot;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.AgentArtifactEntity;
import com.example.poc.platform.persistence.entity.AgentEventEntity;
import com.example.poc.platform.persistence.entity.AgentMessageEntity;
import com.example.poc.platform.persistence.entity.AgentTaskEntity;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.RunEntity;
import com.example.poc.platform.persistence.entity.SuiteEntity;
import com.example.poc.platform.persistence.entity.TestKnowledgeEntity;
import com.example.poc.platform.persistence.repo.AgentArtifactRepository;
import com.example.poc.platform.persistence.repo.AgentEventRepository;
import com.example.poc.platform.persistence.repo.AgentMessageRepository;
import com.example.poc.platform.persistence.repo.AgentTaskRepository;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.RunRepository;
import com.example.poc.platform.persistence.repo.SuiteRepository;
import com.example.poc.platform.persistence.repo.TestKnowledgeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

@Service
public class AgentTaskService {
    private static final long DEFAULT_TOKEN_BUDGET = 4_096;

    private final AgentTaskRepository taskRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentEventRepository eventRepository;
    private final AgentArtifactRepository artifactRepository;
    private final ProjectRepository projectRepository;
    private final CaseRepository caseRepository;
    private final SuiteRepository suiteRepository;
    private final ApiOperationRepository operationRepository;
    private final RunRepository runRepository;
    private final TestKnowledgeRepository knowledgeRepository;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final AgentProperties properties;
    private final JsonCodec jsonCodec;
    private final AgentAuditService auditService;
    private final com.example.poc.platform.agent.context.AgentContextRedactor redactor;
    private final ApplicationEventPublisher eventPublisher;

    public AgentTaskService(
            AgentTaskRepository taskRepository,
            AgentMessageRepository messageRepository,
            AgentEventRepository eventRepository,
            AgentArtifactRepository artifactRepository,
            ProjectRepository projectRepository,
            CaseRepository caseRepository,
            SuiteRepository suiteRepository,
            ApiOperationRepository operationRepository,
            RunRepository runRepository,
            TestKnowledgeRepository knowledgeRepository,
            AgentRuntimeRegistry runtimeRegistry,
            AgentProperties properties,
            JsonCodec jsonCodec,
            AgentAuditService auditService,
            com.example.poc.platform.agent.context.AgentContextRedactor redactor,
            ApplicationEventPublisher eventPublisher) {
        this.taskRepository = taskRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.artifactRepository = artifactRepository;
        this.projectRepository = projectRepository;
        this.caseRepository = caseRepository;
        this.suiteRepository = suiteRepository;
        this.operationRepository = operationRepository;
        this.runRepository = runRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.runtimeRegistry = runtimeRegistry;
        this.properties = properties;
        this.jsonCodec = jsonCodec;
        this.auditService = auditService;
        this.redactor = redactor;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgentTaskRecord create(String projectId, @Valid CreateAgentTaskRequest request) {
        return create(projectId, defaultIfBlank(request.createdBy(), AgentRequestIdentity.LOCAL_USER), request);
    }

    @Transactional
    public AgentTaskRecord create(String projectId, String actorId, @Valid CreateAgentTaskRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new NoSuchElementException("Project not found: " + projectId);
        }
        validateTarget(projectId, request.targetType(), request.targetId());
        AgentContextSelection context = request.context() == null
                ? AgentContextSelection.empty()
                : request.context();
        validateContext(projectId, request.targetType(), request.targetId(), context);
        String runtimeType = normalize(defaultIfBlank(request.runtimeType(), properties.getDefaultRuntime()));
        var runtime = runtimeRegistry.require(runtimeType);
        String modelId = defaultIfBlank(request.modelId(), runtime.capabilities().defaultModel());

        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setProjectId(projectId);
        entity.setTitle(request.title().trim());
        entity.setStatus("READY");
        entity.setTargetType(normalizeNullable(request.targetType()));
        entity.setTargetId(blankToNull(request.targetId()));
        entity.setPermissionMode("DRAFT_ONLY");
        entity.setRuntimeType(runtime.type());
        entity.setModelId(modelId);
        entity.setCreatedBy(actorId);
        entity.setIterationCount(0);
        entity.setTokenBudget(request.tokenBudget() == null ? DEFAULT_TOKEN_BUDGET : request.tokenBudget());
        entity.setContextJson(jsonCodec.write(context));
        entity = taskRepository.saveAndFlush(entity);
        appendEvent(entity, "task.created", Map.of(
                "targetType", entity.getTargetType() == null ? "" : entity.getTargetType(),
                "targetId", entity.getTargetId() == null ? "" : entity.getTargetId(),
                "permissionMode", entity.getPermissionMode(),
                "runtimeType", entity.getRuntimeType(),
                "modelId", entity.getModelId() == null ? "" : entity.getModelId()));
        return toTaskRecord(entity);
    }

    @Transactional(readOnly = true)
    public List<AgentTaskRecord> list(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NoSuchElementException("Project not found: " + projectId);
        }
        return taskRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(this::toTaskRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentTaskRecord> list(String projectId, String actorId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NoSuchElementException("Project not found: " + projectId);
        }
        return taskRepository.findByProjectIdAndCreatedByOrderByUpdatedAtDesc(projectId, actorId).stream()
                .map(this::toTaskRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentTaskRecord get(String taskId) { return toTaskRecord(requireTask(taskId)); }

    @Transactional(readOnly = true)
    public AgentTaskRecord get(String taskId, String actorId) {
        return toTaskRecord(requireOwnedTask(taskId, actorId));
    }

    @Transactional
    public AgentTaskRecord rename(String taskId, String title) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        task.setTitle(title.trim());
        taskRepository.saveAndFlush(task);
        appendEvent(task, "task.updated", Map.of("title", task.getTitle()));
        return toTaskRecord(task);
    }

    @Transactional
    public AgentTaskRecord rename(String taskId, String actorId, String title) {
        AgentTaskEntity task = requireOwnedTaskForUpdate(taskId, actorId);
        task.setTitle(title.trim());
        taskRepository.saveAndFlush(task);
        appendEvent(task, "task.updated", Map.of("title", task.getTitle()));
        return toTaskRecord(task);
    }

    @Transactional(readOnly = true)
    public List<AgentMessageRecord> messages(String taskId) {
        requireTask(taskId);
        return messageRepository.findByTaskIdOrderBySequenceAsc(taskId).stream()
                .map(this::toMessageRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentMessageRecord> messages(String taskId, String actorId) {
        requireOwnedTask(taskId, actorId);
        return messageRepository.findByTaskIdOrderBySequenceAsc(taskId).stream()
                .map(this::toMessageRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentEventRecord> events(String taskId, long afterSequence) {
        requireTask(taskId);
        return eventRepository.findByTaskIdAndSequenceGreaterThanOrderBySequenceAsc(taskId, afterSequence).stream()
                .map(this::toEventRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentEventRecord> events(String taskId, String actorId, long afterSequence) {
        requireOwnedTask(taskId, actorId);
        return eventRepository.findByTaskIdAndSequenceGreaterThanOrderBySequenceAsc(taskId, afterSequence).stream()
                .map(this::toEventRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentArtifactRecord> artifacts(String taskId) {
        requireTask(taskId);
        return artifactRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toArtifactRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentArtifactRecord> artifacts(String taskId, String actorId) {
        requireOwnedTask(taskId, actorId);
        return artifactRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toArtifactRecord)
                .toList();
    }

    @Transactional
    public void delete(String taskId) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        if ("RUNNING".equals(task.getStatus())) {
            throw new AgentTaskConflictException("TASK_ALREADY_RUNNING", "A running Agent Task cannot be deleted");
        }
        artifactRepository.deleteByTaskId(taskId);
        eventRepository.deleteByTaskId(taskId);
        messageRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);
        taskRepository.flush();
    }

    @Transactional
    public void delete(String taskId, String actorId) {
        AgentTaskEntity task = requireOwnedTaskForUpdate(taskId, actorId);
        if ("RUNNING".equals(task.getStatus())) {
            throw new AgentTaskConflictException("TASK_ALREADY_RUNNING", "A running Agent Task cannot be deleted");
        }
        appendEvent(task, "task.deleted", Map.of("retainedAudit", true));
        artifactRepository.deleteByTaskId(taskId);
        eventRepository.deleteByTaskId(taskId);
        messageRepository.deleteByTaskId(taskId);
        taskRepository.delete(task);
        taskRepository.flush();
    }

    @Transactional
    public void recordDecision(String taskId, String actorId, String decision, Map<String, Object> details) {
        AgentTaskEntity task = requireOwnedTaskForUpdate(taskId, actorId);
        String normalized = normalize(decision);
        Set<String> allowed = Set.of(
                "DRAFT_APPLIED_TO_EDITOR", "DRAFT_REJECTED", "SCENARIO_SAVED",
                "FEEDBACK_POSITIVE", "FEEDBACK_NEGATIVE");
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported Agent decision: " + decision);
        }
        String eventType = normalized.startsWith("FEEDBACK_")
                ? "feedback." + normalized.substring("FEEDBACK_".length()).toLowerCase(Locale.ROOT)
                : "decision." + normalized.toLowerCase(Locale.ROOT);
        appendEvent(task, eventType, details == null ? Map.of() : details);
    }

    @Transactional
    public StartedTurn beginTurn(String taskId, String turnId, String content) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        if ("RUNNING".equals(task.getStatus())) {
            throw new AgentTaskConflictException("TASK_ALREADY_RUNNING", "Agent Task already has a running turn");
        }
        if (task.getIterationCount() >= properties.getMaxTurns()) {
            throw new AgentBudgetExceededException("Agent Task reached its maximum turn count");
        }
        runtimeRegistry.require(task.getRuntimeType());

        AgentMessageEntity message = appendMessage(task, "USER", content);
        task.setStatus("RUNNING");
        task.setIterationCount(task.getIterationCount() + 1);
        task.setCompletedAt(null);
        taskRepository.saveAndFlush(task);
        appendEvent(task, "task.started", Map.of(
                "turnId", turnId,
                "iteration", task.getIterationCount(),
                "runtimeType", task.getRuntimeType(),
                "modelId", task.getModelId(),
                "messageId", message.getId()));

        List<AgentConversationMessage> history = messageRepository.findByTaskIdOrderBySequenceAsc(taskId).stream()
                .map(entity -> new AgentConversationMessage(entity.getRole(), text(entity.getContentJson())))
                .toList();
        return new StartedTurn(
                toTaskRecord(task),
                new AgentTurnRequest(taskId, turnId, task.getModelId(), task.getTokenBudget(), history));
    }

    @Transactional
    public void recordRuntimeEvent(String taskId, String turnId, AgentRuntimeEvent runtimeEvent) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        if (!"RUNNING".equals(task.getStatus())) return;
        if ("artifact.output".equals(runtimeEvent.type())) {
            Object value = runtimeEvent.payload().get("artifact");
            if (!(value instanceof AgentArtifactOutput output)) {
                throw new IllegalArgumentException("Runtime artifact event is invalid");
            }
            AgentArtifactEntity artifact = appendArtifact(task, output);
            appendEvent(task, "artifact.created", Map.of(
                    "turnId", turnId,
                    "artifactId", artifact.getId(),
                    "artifactType", artifact.getArtifactType(),
                    "origin", "tool"));
            return;
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>(runtimeEvent.payload());
        payload.put("turnId", turnId);
        appendEvent(task, runtimeEvent.type(), payload);
    }

    @Transactional
    public void recordTurnPreparation(
            String taskId,
            String turnId,
            AgentContextSnapshot snapshot,
            Map<String, String> skillVersions) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        if (!"RUNNING".equals(task.getStatus())) {
            throw new AgentTaskConflictException("TASK_NOT_RUNNING", "Agent Task is not running");
        }
        AgentArtifactEntity artifact = appendArtifact(task, snapshot.toArtifact());
        appendEvent(task, "context.snapshot.created", Map.of(
                "turnId", turnId,
                "snapshotId", snapshot.id(),
                "artifactId", artifact.getId(),
                "sourceCount", snapshot.items().size(),
                "sources", snapshot.items().stream().map(item -> Map.of(
                        "type", item.type(),
                        "targetId", item.targetId() == null ? "" : item.targetId(),
                        "revision", item.revision() == null ? "" : item.revision(),
                        "trustLevel", item.trustLevel())).toList()));
        appendEvent(task, "agent.configuration", Map.of(
                "turnId", turnId,
                "promptVersion", "test-agent-system-v1",
                "toolsetVersion", "scenario-tools-v1",
                "runtimeType", task.getRuntimeType(),
                "modelId", task.getModelId() == null ? "" : task.getModelId(),
                "skills", skillVersions));
        for (Map.Entry<String, String> skill : skillVersions.entrySet()) {
            appendEvent(task, "skill.loaded", Map.of(
                    "turnId", turnId,
                    "skillName", skill.getKey(),
                    "skillVersion", skill.getValue(),
                    "source", "trusted-repository"));
        }
    }

    @Transactional
    public AgentTaskRecord completeTurn(String taskId, String turnId, AgentTurnResult result) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        if (!"RUNNING".equals(task.getStatus())) return toTaskRecord(task);

        AgentMessageEntity assistant = appendMessage(task, "ASSISTANT", result.content());
        appendEvent(task, "message.completed", Map.of(
                "turnId", turnId,
                "messageId", assistant.getId(),
                "messageSequence", assistant.getSequence()));
        for (AgentArtifactOutput output : result.artifacts()) {
            AgentArtifactEntity artifact = appendArtifact(task, output);
            appendEvent(task, "artifact.created", Map.of(
                    "turnId", turnId,
                    "artifactId", artifact.getId(),
                    "artifactType", artifact.getArtifactType()));
        }
        task.setStatus("COMPLETED");
        task.setCompletedAt(Instant.now());
        taskRepository.saveAndFlush(task);
        appendEvent(task, "task.completed", Map.of(
                "turnId", turnId,
                "promptTokens", result.promptTokens(),
                "completionTokens", result.completionTokens()));
        return toTaskRecord(task);
    }

    @Transactional
    public AgentTaskRecord failTurn(
            String taskId,
            String turnId,
            String code,
            String message,
            boolean retryable) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        if (!"RUNNING".equals(task.getStatus())) return toTaskRecord(task);
        task.setStatus("FAILED");
        task.setCompletedAt(Instant.now());
        taskRepository.saveAndFlush(task);
        appendEvent(task, "task.failed", Map.of(
                "turnId", turnId,
                "code", code,
                "message", message,
                "retryable", retryable));
        return toTaskRecord(task);
    }

    @Transactional
    public AgentTaskRecord cancelTurn(String taskId, String turnId) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        if (!"RUNNING".equals(task.getStatus())) return toTaskRecord(task);
        task.setStatus("CANCELLED");
        task.setCompletedAt(Instant.now());
        taskRepository.saveAndFlush(task);
        appendEvent(task, "task.cancelled", Map.of("turnId", turnId));
        return toTaskRecord(task);
    }

    @Transactional
    public AgentTaskRecord exhaustTurnBudget(String taskId) {
        AgentTaskEntity task = requireTaskForUpdate(taskId);
        if ("RUNNING".equals(task.getStatus())) return toTaskRecord(task);
        task.setStatus("FAILED");
        task.setCompletedAt(Instant.now());
        taskRepository.saveAndFlush(task);
        appendEvent(task, "task.failed", Map.of(
                "turnId", "budget",
                "code", "TURN_BUDGET_EXCEEDED",
                "message", "Agent Task reached its maximum model call count",
                "retryable", false));
        return toTaskRecord(task);
    }

    @Transactional
    public void recoverInterruptedTasks() {
        List<String> ids = taskRepository.findByStatus("RUNNING").stream().map(AgentTaskEntity::getId).toList();
        for (String id : ids) {
            AgentTaskEntity task = requireTaskForUpdate(id);
            if (!"RUNNING".equals(task.getStatus())) continue;
            task.setStatus("FAILED");
            task.setCompletedAt(Instant.now());
            taskRepository.saveAndFlush(task);
            appendEvent(task, "task.failed", Map.of(
                    "turnId", "recovery",
                    "code", "SERVICE_RESTARTED",
                    "message", "Agent turn was interrupted by a service restart",
                    "retryable", true));
        }
    }

    private AgentMessageEntity appendMessage(AgentTaskEntity task, String role, String content) {
        AgentMessageEntity message = new AgentMessageEntity();
        message.setTaskId(task.getId());
        message.setSequence(messageRepository.countByTaskId(task.getId()) + 1);
        message.setRole(role);
        String safeText = redactor.redactText(content);
        message.setContentJson(jsonCodec.write(Map.of("text", safeText)));
        message = messageRepository.saveAndFlush(message);
        auditService.record(task.getId(), task.getProjectId(), task.getCreatedBy(),
                "message." + role.toLowerCase(Locale.ROOT),
                Map.of("sequence", message.getSequence(), "text", safeText));
        return message;
    }

    private AgentEventEntity appendEvent(AgentTaskEntity task, String type, Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, Object> safePayload = (Map<String, Object>) redactor.redact(payload);
        AgentEventEntity event = new AgentEventEntity();
        event.setTaskId(task.getId());
        event.setSequence(eventRepository.countByTaskId(task.getId()) + 1);
        event.setEventType(type);
        event.setPayloadJson(jsonCodec.write(safePayload));
        event = eventRepository.saveAndFlush(event);
        auditService.record(task.getId(), task.getProjectId(), task.getCreatedBy(), type, safePayload);
        eventPublisher.publishEvent(new AgentEventCommitted(toEventRecord(event)));
        return event;
    }

    private AgentArtifactEntity appendArtifact(AgentTaskEntity task, AgentArtifactOutput output) {
        AgentArtifactEntity artifact = new AgentArtifactEntity();
        artifact.setTaskId(task.getId());
        artifact.setArtifactType(output.artifactType());
        artifact.setTargetType(output.targetType());
        artifact.setTargetId(output.targetId());
        artifact.setBaseRevision(output.baseRevision());
        artifact.setStatus(defaultIfBlank(output.status(), "READY"));
        Object safeContent = redactor.redact(output.content());
        artifact.setContentJson(jsonCodec.write(safeContent));
        artifact = artifactRepository.saveAndFlush(artifact);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("artifactId", artifact.getId());
        auditPayload.put("artifactType", artifact.getArtifactType());
        auditPayload.put("targetType", artifact.getTargetType());
        auditPayload.put("targetId", artifact.getTargetId());
        auditPayload.put("baseRevision", artifact.getBaseRevision());
        auditPayload.put("status", artifact.getStatus());
        auditPayload.put("content", safeContent);
        auditService.record(task.getId(), task.getProjectId(), task.getCreatedBy(),
                "artifact.persisted", auditPayload);
        return artifact;
    }

    private void validateTarget(String projectId, String targetType, String targetId) {
        String type = normalizeNullable(targetType);
        String id = blankToNull(targetId);
        if (type == null && id == null) return;
        if (type == null || id == null) {
            throw new IllegalArgumentException("targetType and targetId must be provided together");
        }
        switch (type) {
            case "SCENARIO" -> {
                CaseEntity scenario = caseRepository.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Scenario not found: " + id));
                if (!projectId.equals(scenario.getProjectId())) throw outsideProject();
            }
            case "SUITE" -> {
                SuiteEntity suite = suiteRepository.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Test Suite not found: " + id));
                if (!projectId.equals(suite.getProjectId())) throw outsideProject();
            }
            default -> throw new IllegalArgumentException("Unsupported Agent targetType: " + type);
        }
    }

    private void validateContext(
            String projectId,
            String targetType,
            String targetId,
            AgentContextSelection context) {
        for (AgentContextReference reference : context.included()) {
            requireContextInProject(projectId, reference, true);
        }
        for (AgentContextReference reference : context.excluded()) {
            if ("SCENARIO".equals(reference.type()) && reference.id().equals(targetId)) {
                throw new IllegalArgumentException("The target Scenario cannot be removed from Agent context");
            }
            if (!"OPERATION".equals(reference.type()) && !"TEST_KNOWLEDGE".equals(reference.type())) {
                throw new IllegalArgumentException("Only automatic Operation or Test Knowledge context can be excluded");
            }
            requireContextInProject(projectId, reference, false);
        }
        for (AgentContextReference included : context.included()) {
            if (context.excluded().stream().anyMatch(excluded -> excluded.key().equals(included.key()))) {
                throw new IllegalArgumentException("Context cannot be both included and excluded: " + included.key());
            }
        }
        if (targetType != null && "SCENARIO".equals(normalizeNullable(targetType)) && targetId != null
                && context.included().stream().anyMatch(reference ->
                        "SCENARIO".equals(reference.type()) && targetId.equals(reference.id()))) {
            throw new IllegalArgumentException("The target Scenario is already included automatically");
        }
    }

    private void requireContextInProject(String projectId, AgentContextReference reference, boolean included) {
        switch (reference.type()) {
            case "SCENARIO" -> {
                CaseEntity scenario = caseRepository.findById(reference.id())
                        .orElseThrow(() -> new NoSuchElementException("Scenario not found: " + reference.id()));
                requireSameProject(projectId, scenario.getProjectId());
            }
            case "SUITE" -> {
                SuiteEntity suite = suiteRepository.findById(reference.id())
                        .orElseThrow(() -> new NoSuchElementException("Test Suite not found: " + reference.id()));
                requireSameProject(projectId, suite.getProjectId());
            }
            case "OPERATION" -> {
                ApiOperationEntity operation = operationRepository.findById(reference.id())
                        .orElseThrow(() -> new NoSuchElementException("Operation not found: " + reference.id()));
                requireSameProject(projectId, operation.getProjectId());
            }
            case "EXECUTION" -> {
                RunEntity run = runRepository.findById(reference.id())
                        .orElseThrow(() -> new NoSuchElementException("Execution not found: " + reference.id()));
                requireSameProject(projectId, run.getProjectId());
            }
            case "TEST_KNOWLEDGE" -> {
                if (included) throw new IllegalArgumentException("Test Knowledge is matched automatically");
                TestKnowledgeEntity knowledge = knowledgeRepository.findById(reference.id())
                        .orElseThrow(() -> new NoSuchElementException("Test Knowledge not found: " + reference.id()));
                requireSameProject(projectId, knowledge.getProjectId());
            }
            default -> throw new IllegalArgumentException("Unsupported Agent context type: " + reference.type());
        }
    }

    private void requireSameProject(String expectedProjectId, String actualProjectId) {
        if (!expectedProjectId.equals(actualProjectId)) throw outsideProject();
    }

    private IllegalArgumentException outsideProject() {
        return new IllegalArgumentException("Agent target belongs to another Project");
    }

    private AgentTaskEntity requireTask(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Agent Task not found: " + id));
    }

    private AgentTaskEntity requireTaskForUpdate(String id) {
        return taskRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NoSuchElementException("Agent Task not found: " + id));
    }

    private AgentTaskEntity requireOwnedTask(String id, String actorId) {
        AgentTaskEntity task = requireTask(id);
        requireOwner(task, actorId);
        return task;
    }

    private AgentTaskEntity requireOwnedTaskForUpdate(String id, String actorId) {
        AgentTaskEntity task = requireTaskForUpdate(id);
        requireOwner(task, actorId);
        return task;
    }

    private void requireOwner(AgentTaskEntity task, String actorId) {
        if (!task.getCreatedBy().equals(actorId)) {
            throw new SecurityException("Agent Task belongs to another user");
        }
    }

    private AgentTaskRecord toTaskRecord(AgentTaskEntity entity) {
        return new AgentTaskRecord(
                entity.getId(), entity.getProjectId(), entity.getTitle(), entity.getStatus(),
                entity.getTargetType(), entity.getTargetId(), entity.getPermissionMode(),
                entity.getRuntimeType(), entity.getModelId(), entity.getCreatedBy(),
                entity.getIterationCount(), entity.getTokenBudget(), context(entity.getContextJson()), revision(entity.getRevision()),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCompletedAt());
    }

    private AgentContextSelection context(String value) {
        return jsonCodec.read(value, new TypeReference<>() {}, AgentContextSelection.empty());
    }

    private AgentMessageRecord toMessageRecord(AgentMessageEntity entity) {
        return new AgentMessageRecord(
                entity.getId(), entity.getTaskId(), entity.getSequence(), entity.getRole(),
                jsonCodec.readObjectMap(entity.getContentJson()), entity.getCreatedAt());
    }

    private AgentEventRecord toEventRecord(AgentEventEntity entity) {
        return new AgentEventRecord(
                entity.getId(), entity.getTaskId(), entity.getSequence(), entity.getEventType(),
                jsonCodec.readObjectMap(entity.getPayloadJson()), entity.getCreatedAt());
    }

    private AgentArtifactRecord toArtifactRecord(AgentArtifactEntity entity) {
        return new AgentArtifactRecord(
                entity.getId(), entity.getTaskId(), entity.getArtifactType(), entity.getTargetType(),
                entity.getTargetId(), entity.getBaseRevision(), entity.getStatus(),
                jsonCodec.readObjectMap(entity.getContentJson()), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String text(String contentJson) {
        Object value = jsonCodec.readObjectMap(contentJson).get("text");
        return value == null ? "" : String.valueOf(value);
    }

    private long revision(Long value) { return value == null ? 0 : value; }
    private String normalize(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : normalize(value); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public record CreateAgentTaskRequest(
            @NotBlank String title,
            String targetType,
            String targetId,
            String runtimeType,
            String modelId,
            String createdBy,
            @Min(1) @Max(1000000) Long tokenBudget,
            AgentContextSelection context) {}

    public record AgentTaskRecord(
            String id, String projectId, String title, String status,
            String targetType, String targetId, String permissionMode,
            String runtimeType, String modelId, String createdBy,
            int iterationCount, long tokenBudget, AgentContextSelection context, long revision,
            Instant createdAt, Instant updatedAt, Instant completedAt) {}

    public record AgentMessageRecord(
            String id, String taskId, long sequence, String role,
            Map<String, Object> content, Instant createdAt) {}

    public record AgentEventRecord(
            String id, String taskId, long sequence, String eventType,
            Map<String, Object> payload, Instant createdAt) {}

    public record AgentArtifactRecord(
            String id, String taskId, String artifactType, String targetType,
            String targetId, Long baseRevision, String status,
            Map<String, Object> content, Instant createdAt, Instant updatedAt) {}

    public record StartedTurn(AgentTaskRecord task, AgentTurnRequest runtimeRequest) {}
}
