package com.example.poc.platform.agent.scenario;

import com.example.poc.engine.ApiStep;
import com.example.poc.engine.ApiTestCase;
import com.example.poc.engine.OperationSpec;
import com.example.poc.platform.agent.AgentRequestIdentity;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.ScenarioDraftEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.ScenarioDraftRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScenarioDraftService {
    private final ProjectRepository projectRepository;
    private final CaseRepository caseRepository;
    private final ScenarioDraftRepository draftRepository;
    private final ApiOperationRepository operationRepository;
    private final JsonCodec jsonCodec;
    private final ScenarioDslCodec codec;
    private final ScenarioCommandDecoder commandDecoder;
    private final ScenarioCommandEngine commandEngine;
    private final ScenarioDraftValidator validator;
    private final ScenarioDiffService diffService;

    public ScenarioDraftService(
            ProjectRepository projectRepository,
            CaseRepository caseRepository,
            ScenarioDraftRepository draftRepository,
            ApiOperationRepository operationRepository,
            JsonCodec jsonCodec,
            ScenarioDslCodec codec,
            ScenarioCommandDecoder commandDecoder,
            ScenarioCommandEngine commandEngine,
            ScenarioDraftValidator validator,
            ScenarioDiffService diffService) {
        this.projectRepository = projectRepository;
        this.caseRepository = caseRepository;
        this.draftRepository = draftRepository;
        this.operationRepository = operationRepository;
        this.jsonCodec = jsonCodec;
        this.codec = codec;
        this.commandDecoder = commandDecoder;
        this.commandEngine = commandEngine;
        this.validator = validator;
        this.diffService = diffService;
    }

    @Transactional
    public ScenarioDraftRecord create(String projectId, @Valid CreateScenarioDraftRequest request) {
        return create(projectId, request, AgentRequestIdentity.LOCAL_USER, null);
    }

    @Transactional
    public ScenarioDraftRecord create(
            String projectId,
            @Valid CreateScenarioDraftRequest request,
            String actorId,
            String agentTaskId) {
        if (agentTaskId != null) {
            ScenarioDraftEntity existing = draftRepository
                    .findFirstByAgentTaskIdAndStatusOrderByUpdatedAtDesc(agentTaskId, "ACTIVE")
                    .orElse(null);
            if (existing != null) {
                if (!projectId.equals(existing.getProjectId()) || !request.scenarioId().equals(existing.getScenarioId())) {
                    throw new SecurityException("Agent Task cannot reuse a Draft for another target");
                }
                return toRecord(existing, currentDocument(existing));
            }
        }
        requireProject(projectId);
        CaseEntity scenario = requireScenario(request.scenarioId());
        requireScenarioProject(scenario, projectId);
        if ("DELETED".equalsIgnoreCase(scenario.getStatus())) {
            throw new IllegalArgumentException("Cannot create a Draft from a deleted Scenario");
        }

        ApiTestCase parsed = codec.parse(scenario.getContent());
        List<String> stepIds = parsed.getSteps().stream().map(ignored -> UUID.randomUUID().toString()).toList();
        ScenarioDocument document = new ScenarioDocument(parsed, stepIds);

        ScenarioDraftEntity entity = new ScenarioDraftEntity();
        entity.setProjectId(projectId);
        entity.setScenarioId(scenario.getId());
        entity.setSourceRevision(revision(scenario.getRevision()));
        entity.setBaseContent(scenario.getContent());
        entity.setContent(scenario.getContent());
        entity.setBaseStepIdsJson(jsonCodec.write(stepIds));
        entity.setStepIdsJson(jsonCodec.write(stepIds));
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(actorId);
        entity.setAgentTaskId(agentTaskId);
        entity = draftRepository.saveAndFlush(entity);
        return toRecord(entity, document);
    }

    @Transactional(readOnly = true)
    public List<ScenarioDraftRecord> list(String projectId) {
        requireProject(projectId);
        return draftRepository.findByProjectIdAndStatusOrderByUpdatedAtDesc(projectId, "ACTIVE").stream()
                .map(entity -> toRecord(entity, currentDocument(entity)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ScenarioDraftRecord> list(String projectId, String actorId) {
        requireProject(projectId);
        return draftRepository.findByProjectIdAndCreatedByAndStatusOrderByUpdatedAtDesc(
                        projectId, actorId, "ACTIVE").stream()
                .map(entity -> toRecord(entity, currentDocument(entity)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScenarioDraftRecord get(String draftId) {
        ScenarioDraftEntity entity = requireDraft(draftId);
        return toRecord(entity, currentDocument(entity));
    }

    @Transactional(readOnly = true)
    public ScenarioDraftRecord get(String draftId, String actorId) {
        ScenarioDraftEntity entity = requireOwnedDraft(draftId, actorId);
        return toRecord(entity, currentDocument(entity));
    }

    @Transactional
    public CommandExecutionResult applyCommands(String draftId, Map<String, Object> input) {
        ScenarioCommandPlan plan = commandDecoder.decode(input);
        if (!draftId.equals(plan.targetId())) {
            throw new ScenarioCommandException(
                    "INVALID_COMMAND_TARGET",
                    "Command Plan targetId does not match the requested Draft",
                    "plan.targetId");
        }

        ScenarioDraftEntity entity = requireActiveDraft(draftId);
        String commandFingerprint = commandFingerprint(plan.targetId(), input.get("commands"));
        if (commandFingerprint.equals(entity.getLastCommandFingerprint())) {
            ScenarioDraftRecord current = toRecord(entity, currentDocument(entity));
            List<String> warnings = new ArrayList<>(plan.warnings());
            warnings.add("The same Command Plan was already applied; no duplicate changes were made");
            return new CommandExecutionResult(
                    current, 0, plan.assumptions(), List.copyOf(warnings), plan.citations());
        }
        long actualRevision = revision(entity.getRevision());
        if (plan.baseRevision() != actualRevision) {
            throw new ScenarioDraftConflictException(
                    "DRAFT_REVISION_CHANGED",
                    "Draft changed after the Command Plan was created",
                    plan.baseRevision(),
                    actualRevision);
        }
        ensureSourceUnchanged(entity);

        ScenarioDocument base = baseDocument(entity);
        ScenarioDocument current = currentDocument(entity);
        String projectId = entity.getProjectId();
        ScenarioDocument updated = commandEngine.apply(
                current,
                plan.commands(),
                operationId -> resolveOperation(projectId, operationId));
        ScenarioValidationResult validation = validator.validate(projectId, updated);
        ScenarioDraftDiff diff = diffService.diff(base, updated);

        entity.setContent(codec.write(updated.scenario()));
        entity.setStepIdsJson(jsonCodec.write(updated.stepIds()));
        entity.setLastCommandFingerprint(commandFingerprint);
        try {
            entity = draftRepository.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ScenarioDraftConflictException(
                    "DRAFT_REVISION_CHANGED",
                    "Draft was updated concurrently",
                    plan.baseRevision(),
                    -1);
        }
        return new CommandExecutionResult(
                toRecord(entity, updated, validation, diff),
                plan.commands().size(),
                plan.assumptions(),
                plan.warnings(),
                plan.citations());
    }

    @Transactional
    public CommandExecutionResult applyCommands(String draftId, String actorId, Map<String, Object> input) {
        requireOwnedDraft(draftId, actorId);
        return applyCommands(draftId, input);
    }

    @Transactional(readOnly = true)
    public ScenarioValidationResult validate(String draftId) {
        ScenarioDraftEntity entity = requireDraft(draftId);
        return validator.validate(entity.getProjectId(), currentDocument(entity));
    }

    @Transactional(readOnly = true)
    public ScenarioValidationResult validate(String draftId, String actorId) {
        ScenarioDraftEntity entity = requireOwnedDraft(draftId, actorId);
        return validator.validate(entity.getProjectId(), currentDocument(entity));
    }

    @Transactional(readOnly = true)
    public ScenarioDraftDiff diff(String draftId) {
        ScenarioDraftEntity entity = requireDraft(draftId);
        return diffService.diff(baseDocument(entity), currentDocument(entity));
    }

    @Transactional(readOnly = true)
    public ScenarioDraftDiff diff(String draftId, String actorId) {
        ScenarioDraftEntity entity = requireOwnedDraft(draftId, actorId);
        return diffService.diff(baseDocument(entity), currentDocument(entity));
    }

    @Transactional
    public void discard(String draftId) {
        ScenarioDraftEntity entity = requireActiveDraft(draftId);
        entity.setStatus("DISCARDED");
        draftRepository.save(entity);
    }

    @Transactional
    public void discard(String draftId, String actorId) {
        ScenarioDraftEntity entity = requireOwnedDraft(draftId, actorId);
        if (!"ACTIVE".equals(entity.getStatus())) {
            throw new IllegalArgumentException("Scenario Draft is not active: " + draftId);
        }
        entity.setStatus("DISCARDED");
        draftRepository.save(entity);
    }

    private ScenarioDraftRecord toRecord(ScenarioDraftEntity entity, ScenarioDocument current) {
        ScenarioValidationResult validation = validator.validate(entity.getProjectId(), current);
        ScenarioDraftDiff diff = diffService.diff(baseDocument(entity), current);
        return toRecord(entity, current, validation, diff);
    }

    private ScenarioDraftRecord toRecord(
            ScenarioDraftEntity entity,
            ScenarioDocument current,
            ScenarioValidationResult validation,
            ScenarioDraftDiff diff) {
        List<ScenarioStepReference> steps = new ArrayList<>();
        for (int index = 0; index < current.stepIds().size(); index++) {
            ApiStep step = current.scenario().getSteps().get(index);
            steps.add(new ScenarioStepReference(current.stepIds().get(index), index, step.getName()));
        }
        return new ScenarioDraftRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getScenarioId(),
                entity.getSourceRevision(),
                revision(entity.getRevision()),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getAgentTaskId(),
                steps,
                validation,
                diff,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ScenarioDocument baseDocument(ScenarioDraftEntity entity) {
        return new ScenarioDocument(
                codec.parse(entity.getBaseContent()),
                jsonCodec.readStringList(entity.getBaseStepIdsJson()));
    }

    private ScenarioDocument currentDocument(ScenarioDraftEntity entity) {
        return new ScenarioDocument(
                codec.parse(entity.getContent()),
                jsonCodec.readStringList(entity.getStepIdsJson()));
    }

    private OperationSpec resolveOperation(String projectId, String operationId) {
        ApiOperationEntity entity = operationRepository.findById(operationId)
                .orElseThrow(() -> new ScenarioCommandException(
                        "COMMAND_TARGET_NOT_FOUND",
                        "Operation does not exist: " + operationId,
                        "operationId"));
        if (!projectId.equals(entity.getProjectId())) {
            throw new ScenarioCommandException(
                    "OPERATION_OUTSIDE_PROJECT",
                    "Operation belongs to another Project",
                    "operationId");
        }
        OperationSpec operation = new OperationSpec();
        operation.setId(entity.getId());
        operation.setApiSpecId(entity.getApiSpecId());
        operation.setOperationId(entity.getOperationId());
        operation.setSummary(entity.getSummary());
        operation.setMethod(entity.getMethod());
        operation.setPath(entity.getPath());
        return operation;
    }

    private void ensureSourceUnchanged(ScenarioDraftEntity draft) {
        CaseEntity scenario = requireScenario(draft.getScenarioId());
        long currentRevision = revision(scenario.getRevision());
        if (currentRevision != draft.getSourceRevision()) {
            throw new ScenarioDraftConflictException(
                    "SOURCE_SCENARIO_CHANGED",
                    "Source Scenario changed after this Draft was created",
                    draft.getSourceRevision(),
                    currentRevision);
        }
    }

    private void requireProject(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new NoSuchElementException("Project not found: " + projectId);
        }
    }

    private CaseEntity requireScenario(String scenarioId) {
        return caseRepository.findById(scenarioId)
                .orElseThrow(() -> new NoSuchElementException("Scenario not found: " + scenarioId));
    }

    private void requireScenarioProject(CaseEntity scenario, String projectId) {
        if (!projectId.equals(scenario.getProjectId())) {
            throw new IllegalArgumentException("Scenario does not belong to the selected Project");
        }
    }

    private ScenarioDraftEntity requireDraft(String draftId) {
        return draftRepository.findById(draftId)
                .orElseThrow(() -> new NoSuchElementException("Scenario Draft not found: " + draftId));
    }

    private ScenarioDraftEntity requireActiveDraft(String draftId) {
        ScenarioDraftEntity draft = requireDraft(draftId);
        if (!"ACTIVE".equals(draft.getStatus())) {
            throw new IllegalArgumentException("Scenario Draft is not active: " + draftId);
        }
        return draft;
    }

    private ScenarioDraftEntity requireOwnedDraft(String draftId, String actorId) {
        ScenarioDraftEntity draft = requireDraft(draftId);
        String owner = draft.getCreatedBy() == null || draft.getCreatedBy().isBlank()
                ? AgentRequestIdentity.LOCAL_USER
                : draft.getCreatedBy();
        if (!owner.equals(actorId)) throw new SecurityException("Scenario Draft belongs to another user");
        return draft;
    }

    private long revision(Long value) {
        return value == null ? 0 : value;
    }

    private String commandFingerprint(String targetId, Object commands) {
        String canonical = jsonCodec.write(Map.of("targetId", targetId, "commands", commands));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record CreateScenarioDraftRequest(@NotBlank String scenarioId) {}

    public record ScenarioStepReference(String id, int index, String name) {}

    public record ScenarioDraftRecord(
            String id,
            String projectId,
            String scenarioId,
            long sourceRevision,
            long revision,
            String content,
            String status,
            String createdBy,
            String agentTaskId,
            List<ScenarioStepReference> steps,
            ScenarioValidationResult validation,
            ScenarioDraftDiff diff,
            Instant createdAt,
            Instant updatedAt) {}

    public record CommandExecutionResult(
            ScenarioDraftRecord draft,
            int appliedCommandCount,
            List<String> assumptions,
            List<String> warnings,
            List<ScenarioCommandPlan.Citation> citations) {}
}
