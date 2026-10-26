package com.example.poc.platform.agent.capability;

import com.example.poc.engine.ApiStep;
import com.example.poc.engine.ApiTestCase;
import com.example.poc.platform.agent.AgentPermissionMode;
import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.scenario.ScenarioDraftService.ScenarioDraftRecord;
import com.example.poc.platform.agent.scenario.ScenarioDslCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.ApiSpecEntity;
import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.TestKnowledgeEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.ApiSpecRepository;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.RunRepository;
import com.example.poc.platform.persistence.repo.SuiteCaseRepository;
import com.example.poc.platform.persistence.repo.SuiteRepository;
import com.example.poc.platform.persistence.repo.TestKnowledgeRepository;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentToolPolicy {
    private final CaseRepository caseRepository;
    private final ApiOperationRepository operationRepository;
    private final ApiSpecRepository specRepository;
    private final TestKnowledgeRepository knowledgeRepository;
    private final RunRepository runRepository;
    private final SuiteRepository suiteRepository;
    private final SuiteCaseRepository suiteCaseRepository;
    private final ScenarioDslCodec scenarioCodec;

    public AgentToolPolicy(
            CaseRepository caseRepository,
            ApiOperationRepository operationRepository,
            ApiSpecRepository specRepository,
            TestKnowledgeRepository knowledgeRepository,
            RunRepository runRepository,
            SuiteRepository suiteRepository,
            SuiteCaseRepository suiteCaseRepository,
            ScenarioDslCodec scenarioCodec) {
        this.caseRepository = caseRepository;
        this.operationRepository = operationRepository;
        this.specRepository = specRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.runRepository = runRepository;
        this.suiteRepository = suiteRepository;
        this.suiteCaseRepository = suiteCaseRepository;
        this.scenarioCodec = scenarioCodec;
    }

    public void authorize(AgentTaskRecord task, AgentToolDefinition definition) {
        if (!allows(task, definition)) throw denied("Agent Task permission mode does not allow this Tool");
    }

    public boolean allows(AgentTaskRecord task, AgentToolDefinition definition) {
        return switch (task.permissionMode()) {
            case READ_ONLY -> "READ".equals(definition.category());
            case REVIEW_REQUIRED, AUTO_APPLY -> Set.of("READ", "DRAFT_WRITE", "VALIDATE")
                    .contains(definition.category());
        };
    }

    public CaseEntity requireTargetScenario(AgentTaskRecord task, String scenarioId) {
        if (!"SCENARIO".equals(task.targetType()) || !task.targetId().equals(scenarioId)) {
            throw denied("Tool can only access the Agent Task target Scenario");
        }
        CaseEntity scenario = caseRepository.findById(scenarioId)
                .orElseThrow(() -> new NoSuchElementException("Scenario not found: " + scenarioId));
        if (!task.projectId().equals(scenario.getProjectId())) throw denied("Scenario belongs to another Project");
        return scenario;
    }

    public CaseEntity requireReadableScenario(AgentTaskRecord task, String scenarioId) {
        if (!readableScenarioIds(task).contains(scenarioId)) {
            throw denied("Scenario is not part of the Agent Task context");
        }
        CaseEntity scenario = caseRepository.findById(scenarioId)
                .orElseThrow(() -> new NoSuchElementException("Scenario not found: " + scenarioId));
        if (!task.projectId().equals(scenario.getProjectId())) throw denied("Scenario belongs to another Project");
        return scenario;
    }

    /**
     * Suite composition may reuse any active Scenario from the same Project, but this
     * does not grant the Suite Agent Scenario write permission.
     */
    public CaseEntity requireProjectScenario(AgentTaskRecord task, String scenarioId) {
        if (!"SUITE".equals(task.targetType())) {
            return requireReadableScenario(task, scenarioId);
        }
        requireTargetSuite(task, task.targetId());
        CaseEntity scenario = caseRepository.findById(scenarioId)
                .orElseThrow(() -> new NoSuchElementException("Scenario not found: " + scenarioId));
        if (!task.projectId().equals(scenario.getProjectId())) throw denied("Scenario belongs to another Project");
        if ("DELETED".equalsIgnoreCase(scenario.getStatus())) throw denied("Scenario is deleted");
        return scenario;
    }

    public void requireTargetSuite(AgentTaskRecord task, String suiteId) {
        if (!"SUITE".equals(task.targetType()) || !task.targetId().equals(suiteId)) {
            throw denied("Tool can only access the Agent Task target Suite");
        }
        var suite = suiteRepository.findById(suiteId)
                .orElseThrow(() -> new NoSuchElementException("Suite not found: " + suiteId));
        if (!task.projectId().equals(suite.getProjectId())) throw denied("Suite belongs to another Project");
    }

    public ApiOperationEntity requireLinkedOperation(AgentTaskRecord task, String operationRefId) {
        if (!linkedOperationIds(task).contains(operationRefId)) {
            throw denied("Operation is not linked to the Agent Task target Scenario");
        }
        ApiOperationEntity operation = operationRepository.findById(operationRefId)
                .orElseThrow(() -> new NoSuchElementException("Operation not found: " + operationRefId));
        if (!task.projectId().equals(operation.getProjectId())) throw denied("Operation belongs to another Project");
        return operation;
    }

    public ApiSpecEntity requireProjectApiSpec(AgentTaskRecord task, String apiSpecRefId) {
        ApiSpecEntity spec = specRepository.findById(apiSpecRefId)
                .orElseThrow(() -> new NoSuchElementException("API Spec not found: " + apiSpecRefId));
        if (!task.projectId().equals(spec.getProjectId())) throw denied("API Spec belongs to another Project");
        return spec;
    }

    public ApiOperationEntity requireProjectOperation(AgentTaskRecord task, String operationRefId) {
        ApiOperationEntity operation = operationRepository.findById(operationRefId)
                .orElseThrow(() -> new NoSuchElementException("Operation not found: " + operationRefId));
        if (!task.projectId().equals(operation.getProjectId())) throw denied("Operation belongs to another Project");
        return operation;
    }

    public void requireTargetDraft(AgentTaskRecord task, ScenarioDraftRecord draft) {
        if (!task.projectId().equals(draft.projectId())) throw denied("Scenario Draft belongs to another Project");
        requireTargetScenario(task, draft.scenarioId());
        if (!"ACTIVE".equals(draft.status())) throw denied("Scenario Draft is not active");
        if (draft.agentTaskId() != null && !task.id().equals(draft.agentTaskId())) {
            throw denied("Scenario Draft belongs to another Agent Task");
        }
        if (draft.createdBy() != null && !task.createdBy().equals(draft.createdBy())) {
            throw denied("Scenario Draft belongs to another user");
        }
    }

    public void requireSuiteNewScenarioDraft(AgentTaskRecord task, ScenarioDraftRecord draft) {
        requireTargetSuite(task, task.targetId());
        if (!task.projectId().equals(draft.projectId()) || draft.scenarioId() != null) {
            throw denied("Scenario Draft is not a new Draft for this Suite");
        }
        if (!task.targetId().equals(draft.sourceSuiteId())) throw denied("Scenario Draft belongs to another Suite");
        if (!"ACTIVE".equals(draft.status())) throw denied("Scenario Draft is not active");
        if (!task.id().equals(draft.agentTaskId())) throw denied("Scenario Draft belongs to another Agent Task");
        if (!task.createdBy().equals(draft.createdBy())) throw denied("Scenario Draft belongs to another user");
    }

    public TestKnowledgeEntity requireAllowedPublishedKnowledge(AgentTaskRecord task, String knowledgeId) {
        TestKnowledgeEntity knowledge = knowledgeRepository.findById(knowledgeId)
                .orElseThrow(() -> new NoSuchElementException("Test knowledge not found: " + knowledgeId));
        if (!task.projectId().equals(knowledge.getProjectId())) throw denied("Knowledge belongs to another Project");
        if (!"PUBLISHED".equals(knowledge.getStatus())) throw denied("Only published Knowledge is available to Agent");
        if (!isKnowledgeInScope(task, knowledge)) throw denied("Knowledge is outside the Agent Task target scope");
        return knowledge;
    }

    public boolean isKnowledgeInScope(AgentTaskRecord task, TestKnowledgeEntity knowledge) {
        if (task.context().excludes("TEST_KNOWLEDGE", knowledge.getId())) return false;
        if ("PROJECT".equals(knowledge.getScope())) return true;
        if ("SCENARIO".equals(knowledge.getScope())) return readableScenarioIds(task).contains(knowledge.getTargetId());
        if ("SUITE".equals(knowledge.getScope())) {
            return ("SUITE".equals(task.targetType()) && task.targetId().equals(knowledge.getTargetId()))
                    || task.context().includes("SUITE", knowledge.getTargetId());
        }
        Set<String> operationIds = linkedOperationIds(task);
        if ("OPERATION".equals(knowledge.getScope())) return operationIds.contains(knowledge.getTargetId());
        if ("API_SPEC".equals(knowledge.getScope())) {
            return operationIds.stream()
                    .map(operationRepository::findById)
                    .flatMap(java.util.Optional::stream)
                    .anyMatch(operation -> operation.getApiSpecId().equals(knowledge.getTargetId()));
        }
        return false;
    }

    public Set<String> linkedOperationIds(AgentTaskRecord task) {
        Set<String> result = new LinkedHashSet<>();
        for (String scenarioId : readableScenarioIds(task)) {
            CaseEntity scenario = caseRepository.findById(scenarioId).orElse(null);
            if (scenario == null || !task.projectId().equals(scenario.getProjectId())) continue;
            ApiTestCase parsed = scenarioCodec.parse(scenario.getContent());
            for (ApiStep step : parsed.getSteps()) {
                if (step.getOperation() != null && step.getOperation().getId() != null
                        && !task.context().excludes("OPERATION", step.getOperation().getId())) {
                    result.add(step.getOperation().getId());
                }
            }
        }
        task.context().included().stream()
                .filter(reference -> "OPERATION".equals(reference.type()))
                .map(reference -> reference.id())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private Set<String> readableScenarioIds(AgentTaskRecord task) {
        Set<String> result = new LinkedHashSet<>();
        if ("SCENARIO".equals(task.targetType()) && task.targetId() != null) result.add(task.targetId());
        if ("SUITE".equals(task.targetType()) && task.targetId() != null) {
            suiteCaseRepository.findBySuiteIdOrderByCaseOrderAsc(task.targetId()).stream()
                    .map(entry -> entry.getCaseId()).forEach(result::add);
        }
        task.context().included().stream()
                .filter(reference -> "SCENARIO".equals(reference.type()))
                .map(reference -> reference.id())
                .forEach(result::add);
        task.context().included().stream()
                .filter(reference -> "EXECUTION".equals(reference.type()))
                .map(reference -> runRepository.findById(reference.id()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(run -> task.projectId().equals(run.getProjectId()))
                .map(run -> run.getCaseId())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private SecurityException denied(String message) {
        return new SecurityException("Agent Tool denied: " + message);
    }
}
