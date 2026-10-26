package com.example.poc.platform.agent.suite;

import com.example.poc.platform.agent.AgentRequestIdentity;
import com.example.poc.platform.agent.scenario.ScenarioCommandException;
import com.example.poc.platform.agent.scenario.ScenarioDraftConflictException;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.SuiteCaseEntity;
import com.example.poc.platform.persistence.entity.SuiteDraftEntity;
import com.example.poc.platform.persistence.entity.SuiteEntity;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.SuiteCaseRepository;
import com.example.poc.platform.persistence.repo.SuiteDraftRepository;
import com.example.poc.platform.persistence.repo.SuiteRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuiteDraftService {
    private final ProjectRepository projectRepository; private final SuiteRepository suiteRepository; private final SuiteCaseRepository suiteCaseRepository; private final SuiteDraftRepository draftRepository; private final JsonCodec jsonCodec; private final SuiteCommandDecoder decoder; private final SuiteCommandEngine engine; private final SuiteDraftValidator validator; private final SuiteDiffService diffService;
    public SuiteDraftService(ProjectRepository projectRepository, SuiteRepository suiteRepository, SuiteCaseRepository suiteCaseRepository, SuiteDraftRepository draftRepository, JsonCodec jsonCodec, SuiteCommandDecoder decoder, SuiteCommandEngine engine, SuiteDraftValidator validator, SuiteDiffService diffService) { this.projectRepository = projectRepository; this.suiteRepository = suiteRepository; this.suiteCaseRepository = suiteCaseRepository; this.draftRepository = draftRepository; this.jsonCodec = jsonCodec; this.decoder = decoder; this.engine = engine; this.validator = validator; this.diffService = diffService; }

    @Transactional public SuiteDraftRecord create(String projectId, @Valid CreateSuiteDraftRequest request) { return create(projectId, request, AgentRequestIdentity.LOCAL_USER, null); }
    @Transactional public SuiteDraftRecord create(String projectId, @Valid CreateSuiteDraftRequest request, String actorId, String agentTaskId) {
        requireProject(projectId); SuiteEntity suite = suite(request.suiteId()); requireSuiteProject(suite, projectId);
        if (agentTaskId != null) { SuiteDraftEntity previous = draftRepository.findFirstByAgentTaskIdAndStatusOrderByUpdatedAtDesc(agentTaskId, "ACTIVE").orElse(null); if (previous != null) { if (!projectId.equals(previous.getProjectId()) || !suite.getId().equals(previous.getSuiteId())) throw new SecurityException("Agent Task cannot reuse a Draft for another target"); if (previous.getSourceRevision() == revision(suite.getRevision())) return record(previous, current(previous)); previous.setStatus("DISCARDED"); draftRepository.saveAndFlush(previous); } }
        SuiteDraftDocument document = source(suite); SuiteDraftEntity entity = new SuiteDraftEntity(); entity.setProjectId(projectId); entity.setSuiteId(suite.getId()); entity.setSourceRevision(revision(suite.getRevision())); entity.setBaseContent(jsonCodec.write(document)); entity.setContent(jsonCodec.write(document)); entity.setStatus("ACTIVE"); entity.setCreatedBy(actorId); entity.setAgentTaskId(agentTaskId); return record(draftRepository.saveAndFlush(entity), document);
    }
    @Transactional(readOnly = true) public SuiteDraftRecord get(String id) { SuiteDraftEntity entity = draft(id); return record(entity, current(entity)); }
    @Transactional public CommandExecutionResult applyCommands(String draftId, Map<String, Object> input) {
        SuiteCommandPlan plan = decoder.decode(input); if (!draftId.equals(plan.targetId())) throw new ScenarioCommandException("INVALID_COMMAND_TARGET", "Command Plan targetId does not match the requested Draft", "plan.targetId");
        SuiteDraftEntity entity = active(draftId); String fingerprint = fingerprint(plan.targetId(), input.get("commands")); if (fingerprint.equals(entity.getLastCommandFingerprint())) { SuiteDraftRecord current = record(entity, current(entity)); List<String> warnings = new ArrayList<>(plan.warnings()); warnings.add("The same Command Plan was already applied; no duplicate changes were made"); return new CommandExecutionResult(current, 0, plan.assumptions(), warnings, plan.citations()); }
        if (plan.baseRevision() != revision(entity.getRevision())) throw new ScenarioDraftConflictException("DRAFT_REVISION_CHANGED", "Draft changed after the Command Plan was created", plan.baseRevision(), revision(entity.getRevision()));
        ensureSourceUnchanged(entity); SuiteDraftDocument base = base(entity); SuiteDraftDocument updated = engine.apply(current(entity), plan.commands()); SuiteValidationResult validation = validator.validate(entity.getProjectId(), updated); SuiteDraftDiff diff = diffService.diff(base, updated); entity.setContent(jsonCodec.write(updated)); entity.setLastCommandFingerprint(fingerprint);
        try { entity = draftRepository.saveAndFlush(entity); } catch (ObjectOptimisticLockingFailureException exception) { throw new ScenarioDraftConflictException("DRAFT_REVISION_CHANGED", "Draft was updated concurrently", plan.baseRevision(), -1); }
        return new CommandExecutionResult(record(entity, updated, validation, diff), plan.commands().size(), plan.assumptions(), plan.warnings(), plan.citations());
    }
    @Transactional(readOnly = true) public SuiteValidationResult validate(String draftId) { SuiteDraftEntity entity = draft(draftId); return validator.validate(entity.getProjectId(), current(entity)); }
    @Transactional(readOnly = true) public SuiteDraftDiff diff(String draftId) { SuiteDraftEntity entity = draft(draftId); return diffService.diff(base(entity), current(entity)); }

    private SuiteDraftRecord record(SuiteDraftEntity entity, SuiteDraftDocument document) { return record(entity, document, validator.validate(entity.getProjectId(), document), diffService.diff(base(entity), document)); }
    private SuiteDraftRecord record(SuiteDraftEntity entity, SuiteDraftDocument document, SuiteValidationResult validation, SuiteDraftDiff diff) { return new SuiteDraftRecord(entity.getId(), entity.getProjectId(), entity.getSuiteId(), entity.getSourceRevision(), revision(entity.getRevision()), entity.getContent(), entity.getStatus(), entity.getCreatedBy(), entity.getAgentTaskId(), document, validation, diff, entity.getCreatedAt(), entity.getUpdatedAt()); }
    private SuiteDraftDocument source(SuiteEntity suite) { return new SuiteDraftDocument(suite.getName(), suite.getDescription(), suite.getStatus(), suiteCaseRepository.findBySuiteIdOrderByCaseOrderAsc(suite.getId()).stream().map(value -> new SuiteDraftDocument.Entry(value.getId(), value.getCaseId(), value.getDisplayName(), value.isEnabled(), jsonCodec.readObjectMap(value.getVariableOverridesJson()), value.getEnvironmentId(), value.getProfileId())).toList()); }
    private SuiteDraftDocument base(SuiteDraftEntity entity) { return jsonCodec.read(entity.getBaseContent(), new tools.jackson.core.type.TypeReference<>() {}, new SuiteDraftDocument("", "", "ACTIVE", List.of())); }
    private SuiteDraftDocument current(SuiteDraftEntity entity) { return jsonCodec.read(entity.getContent(), new tools.jackson.core.type.TypeReference<>() {}, new SuiteDraftDocument("", "", "ACTIVE", List.of())); }
    private void ensureSourceUnchanged(SuiteDraftEntity draft) { SuiteEntity suite = suite(draft.getSuiteId()); long current = revision(suite.getRevision()); if (current != draft.getSourceRevision()) throw new ScenarioDraftConflictException("SOURCE_SUITE_CHANGED", "Source Suite changed after this Draft was created", draft.getSourceRevision(), current); }
    private void requireProject(String id) { if (!projectRepository.existsById(id)) throw new NoSuchElementException("Project not found: " + id); }
    private SuiteEntity suite(String id) { return suiteRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Suite not found: " + id)); }
    private void requireSuiteProject(SuiteEntity suite, String projectId) { if (!projectId.equals(suite.getProjectId())) throw new IllegalArgumentException("Suite does not belong to the selected Project"); }
    private SuiteDraftEntity draft(String id) { return draftRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Suite Draft not found: " + id)); }
    private SuiteDraftEntity active(String id) { SuiteDraftEntity value = draft(id); if (!"ACTIVE".equals(value.getStatus())) throw new IllegalArgumentException("Suite Draft is not active: " + id); return value; }
    private long revision(Long value) { return value == null ? 0 : value; }
    private String fingerprint(String targetId, Object commands) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jsonCodec.write(Map.of("targetId", targetId, "commands", commands)).getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); } }
    public record CreateSuiteDraftRequest(@NotBlank String suiteId) {}
    public record SuiteDraftRecord(String id, String projectId, String suiteId, long sourceRevision, long revision, String content, String status, String createdBy, String agentTaskId, SuiteDraftDocument document, SuiteValidationResult validation, SuiteDraftDiff diff, Instant createdAt, Instant updatedAt) {}
    public record CommandExecutionResult(SuiteDraftRecord draft, int appliedCommandCount, List<String> assumptions, List<String> warnings, List<SuiteCommandPlan.Citation> citations) {}
}
