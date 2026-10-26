package com.example.poc.platform.agent.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.poc.platform.catalog.PlatformCatalogService;
import com.example.poc.platform.catalog.PlatformCatalogService.CaseRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.SuiteRecord;
import com.example.poc.platform.agent.AgentTaskService;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.ScenarioDraftEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.ScenarioDraftRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ScenarioDraftServiceTest {
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final CaseRepository caseRepository = mock(CaseRepository.class);
    private final ScenarioDraftRepository draftRepository = mock(ScenarioDraftRepository.class);
    private final ApiOperationRepository operationRepository = mock(ApiOperationRepository.class);
    private final PlatformCatalogService catalogService = mock(PlatformCatalogService.class);
    private final AgentTaskService agentTaskService = mock(AgentTaskService.class);
    private final ScenarioDslCodec codec = new ScenarioDslCodec();
    private final JsonCodec jsonCodec = new JsonCodec(JsonMapper.builder().build());
    private ScenarioDraftService service;

    @BeforeEach
    void setUp() {
        ScenarioCommandDecoder decoder = new ScenarioCommandDecoder(codec);
        ScenarioCommandEngine engine = new ScenarioCommandEngine(codec);
        ScenarioDraftValidator validator = new ScenarioDraftValidator(operationRepository, codec, mock(ScenarioContractValidator.class));
        ScenarioDiffService diffService = new ScenarioDiffService(codec);
        service = new ScenarioDraftService(
                projectRepository,
                caseRepository,
                draftRepository,
                operationRepository,
                jsonCodec,
                codec,
                decoder,
                engine,
                validator,
                diffService,
                catalogService,
                agentTaskService);
    }

    @Test
    void rejectsStaleDraftRevisionBeforeApplyingCommands() {
        ScenarioDraftEntity draft = draft(1, 0);
        when(draftRepository.findById("draft-1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.applyCommands("draft-1", plan(0)))
                .isInstanceOf(ScenarioDraftConflictException.class)
                .satisfies(error -> {
                    ScenarioDraftConflictException conflict = (ScenarioDraftConflictException) error;
                    assertThat(conflict.getReason()).isEqualTo("DRAFT_REVISION_CHANGED");
                    assertThat(conflict.getExpectedRevision()).isZero();
                    assertThat(conflict.getActualRevision()).isEqualTo(1);
                });
    }

    @Test
    void rejectsDraftWhenSourceScenarioRevisionChanged() {
        ScenarioDraftEntity draft = draft(0, 2);
        CaseEntity source = new CaseEntity();
        source.setId("scenario-1");
        source.setProjectId("project-1");
        source.setRevision(3L);
        when(draftRepository.findById("draft-1")).thenReturn(Optional.of(draft));
        when(caseRepository.findById("scenario-1")).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.applyCommands("draft-1", plan(0)))
                .isInstanceOf(ScenarioDraftConflictException.class)
                .satisfies(error -> {
                    ScenarioDraftConflictException conflict = (ScenarioDraftConflictException) error;
                    assertThat(conflict.getReason()).isEqualTo("SOURCE_SCENARIO_CHANGED");
                    assertThat(conflict.getExpectedRevision()).isEqualTo(2);
                    assertThat(conflict.getActualRevision()).isEqualTo(3);
                });
    }

    @Test
    void replacesAnActiveAgentDraftWhenItsSourceRevisionIsStale() {
        ScenarioDraftEntity stale = draft(1, 1);
        stale.setAgentTaskId("task-1");
        CaseEntity source = new CaseEntity();
        source.setId("scenario-1");
        source.setProjectId("project-1");
        source.setStatus("ACTIVE");
        source.setRevision(2L);
        source.setContent("""
                dslVersion: v1
                name: Current scenario
                description: ''
                tags: []
                variables: {}
                steps: []
                """);
        when(projectRepository.existsById("project-1")).thenReturn(true);
        when(caseRepository.findById("scenario-1")).thenReturn(Optional.of(source));
        when(draftRepository.findFirstByAgentTaskIdAndStatusOrderByUpdatedAtDesc("task-1", "ACTIVE"))
                .thenReturn(Optional.of(stale));
        AtomicInteger saves = new AtomicInteger();
        when(draftRepository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            ScenarioDraftEntity saved = invocation.getArgument(0);
            if (saves.incrementAndGet() == 2) {
                saved.setId("draft-2");
                saved.setRevision(0L);
            }
            return saved;
        });

        ScenarioDraftService.ScenarioDraftRecord created = service.create(
                "project-1",
                new ScenarioDraftService.CreateScenarioDraftRequest("scenario-1"),
                "tester",
                "task-1");

        assertThat(stale.getStatus()).isEqualTo("DISCARDED");
        assertThat(created.id()).isEqualTo("draft-2");
        assertThat(created.sourceRevision()).isEqualTo(2);
        assertThat(created.agentTaskId()).isEqualTo("task-1");
        verify(draftRepository, org.mockito.Mockito.times(2))
                .saveAndFlush(org.mockito.ArgumentMatchers.any(ScenarioDraftEntity.class));
    }

    @Test
    void publishesANewSuiteScenarioDraftAfterUserConfirmation() {
        ScenarioDraftEntity draft = newScenarioDraft("ACTIVE", null);
        when(draftRepository.findById("draft-new")).thenReturn(Optional.of(draft));
        when(catalogService.saveCase(
                org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.any(PlatformCatalogService.UpsertCaseRequest.class)))
                .thenReturn(caseRecord("scenario-new"));
        when(draftRepository.saveAndFlush(draft)).thenReturn(draft);

        var result = service.publishNew(
                "draft-new",
                "tester",
                new ScenarioDraftService.PublishScenarioDraftRequest(false, null));

        assertThat(result.created()).isTrue();
        assertThat(result.scenario().id()).isEqualTo("scenario-new");
        assertThat(result.suite()).isNull();
        assertThat(draft.getScenarioId()).isEqualTo("scenario-new");
        assertThat(draft.getStatus()).isEqualTo("PUBLISHED");
        verify(draftRepository).saveAndFlush(draft);
        verify(agentTaskService).recordDecision(
                org.mockito.ArgumentMatchers.eq("task-1"),
                org.mockito.ArgumentMatchers.eq("tester"),
                org.mockito.ArgumentMatchers.eq("SCENARIO_SAVED"),
                org.mockito.ArgumentMatchers.argThat(details -> "scenario-new".equals(details.get("scenarioId"))));
    }

    @Test
    void publishingTheSameDraftAgainReturnsTheExistingScenario() {
        ScenarioDraftEntity draft = newScenarioDraft("PUBLISHED", "scenario-new");
        when(draftRepository.findById("draft-new")).thenReturn(Optional.of(draft));
        when(catalogService.getCase("scenario-new")).thenReturn(caseRecord("scenario-new"));

        var result = service.publishNew(
                "draft-new",
                "tester",
                new ScenarioDraftService.PublishScenarioDraftRequest(false, null));

        assertThat(result.created()).isFalse();
        assertThat(result.scenario().id()).isEqualTo("scenario-new");
        verify(catalogService, never()).saveCase(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(PlatformCatalogService.UpsertCaseRequest.class));
    }

    @Test
    void publishesAndAddsTheScenarioToTheSourceSuiteInOneConfirmation() {
        ScenarioDraftEntity draft = newScenarioDraft("ACTIVE", null);
        CaseRecord scenario = caseRecord("scenario-new");
        SuiteRecord suite = suiteRecord(3, List.of());
        SuiteRecord updatedSuite = suiteRecord(4, List.of());
        when(draftRepository.findById("draft-new")).thenReturn(Optional.of(draft));
        when(catalogService.saveCase(
                org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.any(PlatformCatalogService.UpsertCaseRequest.class)))
                .thenReturn(scenario);
        when(catalogService.getSuite("suite-1")).thenReturn(suite);
        when(catalogService.saveSuite(
                org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.any(PlatformCatalogService.UpsertSuiteRequest.class)))
                .thenReturn(updatedSuite);
        when(draftRepository.saveAndFlush(draft)).thenReturn(draft);

        var result = service.publishNew(
                "draft-new",
                "tester",
                new ScenarioDraftService.PublishScenarioDraftRequest(true, 3L));

        ArgumentCaptor<PlatformCatalogService.UpsertSuiteRequest> request = ArgumentCaptor.forClass(
                PlatformCatalogService.UpsertSuiteRequest.class);
        verify(catalogService).saveSuite(org.mockito.ArgumentMatchers.eq("project-1"), request.capture());
        assertThat(request.getValue().cases()).singleElement().satisfies(entry -> {
            assertThat(entry.caseId()).isEqualTo("scenario-new");
            assertThat(entry.enabled()).isTrue();
        });
        assertThat(result.suite()).isSameAs(updatedSuite);
    }

    private ScenarioDraftEntity draft(long revision, long sourceRevision) {
        ScenarioDraftEntity draft = new ScenarioDraftEntity();
        draft.setId("draft-1");
        draft.setProjectId("project-1");
        draft.setScenarioId("scenario-1");
        draft.setRevision(revision);
        draft.setSourceRevision(sourceRevision);
        draft.setStatus("ACTIVE");
        return draft;
    }

    private ScenarioDraftEntity newScenarioDraft(String status, String scenarioId) {
        String content = """
                dslVersion: v1
                name: Agent-created scenario
                description: Created from a Suite conversation
                tags: [agent]
                runtime: {}
                variables: {}
                steps: []
                """;
        ScenarioDraftEntity draft = new ScenarioDraftEntity();
        draft.setId("draft-new");
        draft.setProjectId("project-1");
        draft.setScenarioId(scenarioId);
        draft.setSourceSuiteId("suite-1");
        draft.setSourceRevision(0);
        draft.setBaseContent(content);
        draft.setContent(content);
        draft.setBaseStepIdsJson("[]");
        draft.setStepIdsJson("[]");
        draft.setCreatedBy("tester");
        draft.setAgentTaskId("task-1");
        draft.setStatus(status);
        draft.setRevision(0L);
        return draft;
    }

    private CaseRecord caseRecord(String id) {
        return new CaseRecord(
                id, "project-1", "Agent-created scenario", "agent-created-scenario",
                "Created from a Suite conversation", null, null, null, List.of("agent"),
                "v1", "dslVersion: v1\nname: Agent-created scenario\nsteps: []\n", "ACTIVE", 0,
                Instant.parse("2026-09-16T00:00:00Z"), Instant.parse("2026-09-16T00:00:00Z"));
    }

    private SuiteRecord suiteRecord(long revision, List<PlatformCatalogService.SuiteCaseRecord> cases) {
        return new SuiteRecord(
                "suite-1", "project-1", "Order regression", "", "ACTIVE", revision, cases,
                Instant.parse("2026-09-16T00:00:00Z"), Instant.parse("2026-09-16T00:00:00Z"));
    }

    private Map<String, Object> plan(long revision) {
        return Map.of(
                "targetType", "SCENARIO_DRAFT",
                "targetId", "draft-1",
                "baseRevision", revision,
                "commands", List.of(Map.of(
                        "type", "SetRequestHeader",
                        "stepId", "step-1",
                        "name", "X-Test",
                        "value", "true")));
    }
}
