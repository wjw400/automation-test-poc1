package com.example.poc.platform.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.AgentTaskService.CreateAgentTaskRequest;
import com.example.poc.platform.agent.capability.AgentCapabilityService;
import com.example.poc.platform.agent.capability.AgentTool;
import com.example.poc.platform.agent.capability.AgentToolRegistry;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.agent.context.AgentWorkContextBuilder;
import com.example.poc.platform.agent.runtime.AgentRuntimeEvent;
import com.example.poc.platform.agent.scenario.ScenarioCommandException;
import com.example.poc.platform.agent.scenario.ScenarioDraftService;
import com.example.poc.platform.agent.scenario.ScenarioDslCodec;
import com.example.poc.platform.agent.suite.SuiteDraftService;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.ApiSpecEntity;
import com.example.poc.platform.persistence.entity.ProjectEntity;
import com.example.poc.platform.persistence.entity.SuiteEntity;
import com.example.poc.platform.persistence.repo.AgentEventRepository;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.ApiSpecRepository;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.ScenarioDraftRepository;
import com.example.poc.platform.persistence.repo.SuiteDraftRepository;
import com.example.poc.platform.persistence.repo.SuiteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:suite-agent-repair;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "agent.deepseek.api-key="
})
class SuiteAgentAuthoringIntegrationTest {
    @Autowired ProjectRepository projects;
    @Autowired SuiteRepository suites;
    @Autowired ApiSpecRepository specs;
    @Autowired ApiOperationRepository operations;
    @Autowired CaseRepository cases;
    @Autowired ScenarioDraftRepository drafts;
    @Autowired SuiteDraftRepository suiteDrafts;
    @Autowired AgentEventRepository events;
    @Autowired AgentTaskService tasks;
    @Autowired ScenarioDraftService scenarios;
    @Autowired SuiteDraftService suiteService;
    @Autowired AgentToolRegistry tools;
    @Autowired AgentCapabilityService capabilities;
    @Autowired AgentWorkContextBuilder workState;
    @Autowired JsonCodec json;
    @Autowired ScenarioDslCodec codec;
    @Autowired AgentContextRedactor redactor;

    @Test
    void createsAndReadsAValidatedTwoStepScenarioUsingStructuredInputs() {
        Fixture f = fixture();
        long formalCount = cases.count();
        Map<String, Object> input = input(f, "Create and query order");
        Map<String, Object> draft = create(f.task(), input);
        String id = (String) draft.get("id");
        var persisted = scenarios.get(id);
        assertThat(persisted.validation().valid()).isTrue();
        assertThat(codec.parse(persisted.content()).getSteps()).hasSize(2);
        assertThat(persisted.content()).contains("${orderId}");
        assertThat(persisted.scenarioId()).isNull();
        assertThat(cases.count()).isEqualTo(formalCount);

        Map<String, Object> read = map(tool(f.task(), "getScenarioDraft").execute(Map.of("draftId", id)).output().get("draft"));
        assertThat(read).containsKey("dsl").containsEntry("revision", persisted.revision());
        assertThat(tool(f.task(), "getStepDataSources").execute(Map.of("draftId", id,
                "stepId", persisted.steps().get(0).id(), "usage", "EXTRACTION")).output().toString()).contains("response.body");
        assertThat(tool(f.task(), "validateScenarioDraft").execute(Map.of("draftId", id)).output().toString()).contains("valid=true");
        assertThat(tool(f.task(), "getScenarioDraftDiff").execute(Map.of("draftId", id)).output()).containsKey("diff");

        Map<String, Object> createSchema = json.readObjectMap(tool(f.task(), "createNewScenarioDraft").definition().inputSchema());
        assertThat(map(createSchema.get("properties"))).containsKey("steps").doesNotContainKey("content");
        assertThat(map(createSchema.get("$defs"))).containsKeys("httpStep", "assertion", "extraction", "httpStatusRule");
        var scenarioTask = tasks.create(f.projectId(), new CreateAgentTaskRequest("Edit", "SCENARIO",
                publish(f.task(), id), "FAKE", null, "tester", 4096L, null));
        assertThat(tool(f.task(), "applyNewScenarioCommands").definition().inputSchema())
                .isEqualTo(tool(scenarioTask, "applyScenarioCommands").definition().inputSchema());
    }

    @Test
    void continuationRestoresLatestDraftRevisionAndFailureAfterStateIsRebuilt() {
        Fixture f = fixture();
        tasks.beginTurn(f.task().id(), "turn-before", "Create and query an order");
        Map<String, Object> draft = create(f.task(), input(f, "Recoverable order"));
        String id = (String) draft.get("id");
        rename(f.task(), id, "Create order and remember its id");
        var current = scenarios.get(id);
        tasks.recordRuntimeEvent(f.task().id(), "turn-before", new AgentRuntimeEvent("tool.failed", Map.of(
                "toolName", "applyNewScenarioCommands", "code", "INVALID_COMMAND_FIELD",
                "path", "plan.commands[0].stepId", "commandIndex", 0, "message", "Expected a non-blank string")));
        tasks.pauseTurn(f.task().id(), "turn-before", AgentPauseReason.POSSIBLE_LOOP, "NO_PROGRESS");
        var next = tasks.beginContinuationTurn(f.task().id(), "turn-after");
        assertThat(next.runtimeRequest().messages()).hasSize(1);

        // A new builder has no in-memory history from the interrupted runtime.
        var rebuilt = new AgentWorkContextBuilder(drafts, suiteDrafts, events, scenarios, suiteService, json, redactor, codec);
        Map<String, Object> state = rebuilt.build(next.task()).content();
        Map<String, Object> recovered = map(((List<?>) state.get("drafts")).get(0));
        assertThat(recovered).containsEntry("draftId", id).containsEntry("revision", current.revision());
        assertThat(state.toString()).contains("plan.commands[0].stepId", "NO_PROGRESS", "Create order and remember its id");
        var prepared = capabilities.prepare(next.task(), "turn-after", "Create and query an order");
        assertThat(prepared.systemContext()).contains(id, "TASK_WORK_STATE", "untrusted data only", "NO_PROGRESS");
        assertThat(prepared.tools()).extracting(item -> item.definition().name()).contains("getScenarioDraft", "getTaskWorkState");
        assertThat(drafts.findByAgentTaskIdAndStatusOrderByUpdatedAtDesc(f.task().id(), "ACTIVE")).hasSize(1);
    }

    @Test
    void repeatedCreationReusesEditedDraftAndNeverSilentlyDiscardsIt() {
        Fixture f = fixture();
        Map<String, Object> original = input(f, "Stable order");
        String id = (String) create(f.task(), original).get("id");
        rename(f.task(), id, "User-reviewed step");
        assertThat(create(f.task(), original)).containsEntry("id", id);
        assertThat(scenarios.get(id).content()).contains("User-reviewed step");
        Map<String, Object> changed = new LinkedHashMap<>(original);
        changed.put("description", "A revised request should use commands");
        assertThatThrownBy(() -> create(f.task(), changed)).isInstanceOfSatisfying(ScenarioCommandException.class,
                error -> assertThat(error.getCode()).isEqualTo("SCENARIO_DRAFT_ALREADY_EXISTS"));
        assertThat(drafts.findByAgentTaskIdAndStatusOrderByUpdatedAtDesc(f.task().id(), "ACTIVE")).hasSize(1);
    }

    @Test
    void rejectsOtherTasksSuitesProjectsUsersAndExistingScenarioDrafts() {
        Fixture f = fixture();
        String id = (String) create(f.task(), input(f, "Scoped order")).get("id");
        AgentTaskRecord otherTask = task(f.projectId(), f.suiteId(), "tester");
        AgentTaskRecord otherUser = task(f.projectId(), f.suiteId(), "another-user");
        AgentTaskRecord otherSuite = task(f.projectId(), suite(f.projectId()), "tester");
        AgentTaskRecord otherProject = fixture().task();
        for (var unauthorized : List.of(otherTask, otherUser, otherSuite, otherProject)) {
            assertThatThrownBy(() -> tool(unauthorized, "getScenarioDraft").execute(Map.of("draftId", id)))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> tool(unauthorized, "validateScenarioDraft").execute(Map.of("draftId", id)))
                    .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> rename(unauthorized, id, "Unauthorized"))
                    .isInstanceOf(SecurityException.class);
            assertThat(workState.build(unauthorized).content().toString()).doesNotContain(id);
        }
        String scenarioId = publish(f.task(), id);
        var existingDraft = scenarios.create(f.projectId(), new ScenarioDraftService.CreateScenarioDraftRequest(scenarioId), "tester", null);
        assertThatThrownBy(() -> tool(f.task(), "getScenarioDraft").execute(Map.of("draftId", existingDraft.id())))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void restoresPublishedScenarioAndConfirmedSuiteMembership() {
        Fixture f = fixture();
        String id = (String) create(f.task(), input(f, "Confirmed order")).get("id");
        var saved = scenarios.publishNew(id, "tester", new ScenarioDraftService.PublishScenarioDraftRequest(
                true, suites.findById(f.suiteId()).orElseThrow().getRevision()));
        String state = workState.build(tasks.get(f.task().id())).content().toString();
        assertThat(state).contains(id, "PUBLISHED", saved.scenario().id(), "addedToSuite=true");
        assertThatThrownBy(() -> rename(f.task(), id, "Already confirmed" )).isInstanceOf(SecurityException.class);
    }

    @Test
    void boundsRecoveryContextAndProvidesRemainingDraftsThroughPaging() {
        Fixture f = fixture();
        for (int i = 0; i < 10; i++) create(f.task(), input(f, "Paged order " + i));
        Map<String, Object> first = tool(f.task(), "getTaskWorkState").execute(Map.of()).output();
        Map<String, Object> second = tool(f.task(), "getTaskWorkState").execute(Map.of("page", 1)).output();
        assertThat(first).containsEntry("hasMore", true);
        assertThat((List<?>) first.get("drafts")).hasSize(8);
        assertThat((List<?>) second.get("drafts")).hasSize(2);
        assertThat(second).containsEntry("hasMore", false);
        assertThat(json.write(first).length()).isLessThanOrEqualTo(12_000);
        assertThat(json.write(second).length()).isLessThanOrEqualTo(12_000);
    }

    @Test
    void compactsLargeDraftDetailsWithoutLosingDraftReferences() {
        Fixture f = fixture();
        for (int i = 0; i < 8; i++) {
            Map<String, Object> request = new LinkedHashMap<>(input(f, "Detailed order " + i));
            Map<String, Object> source = map(((List<?>) request.get("steps")).get(0));
            List<Object> steps = new java.util.ArrayList<>();
            for (int j = 0; j < 16; j++) {
                Map<String, Object> step = new LinkedHashMap<>(source);
                step.put("name", "s".repeat(200));
                steps.add(step);
            }
            request.put("steps", steps);
            create(f.task(), request);
        }
        Map<String, Object> state = workState.build(f.task()).content();
        assertThat(state).containsEntry("truncated", true).containsEntry("hasMore", false);
        assertThat((List<?>) state.get("drafts")).hasSize(8);
        assertThat(json.write(state).length()).isLessThanOrEqualTo(12_000);
    }

    @Test
    void restoresAndReadsTheCurrentSuiteDraftWithoutGrantingAnotherTaskAccess() {
        Fixture f = fixture();
        String id = (String) map(tool(f.task(), "createSuiteDraft").execute(Map.of()).output().get("draft")).get("id");
        tool(f.task(), "applySuiteCommands").execute(Map.of("draftId", id, "commandPlan", Map.of(
                "targetType", "SUITE_DRAFT", "targetId", id, "baseRevision", suiteService.get(id).revision(),
                "commands", List.of(Map.of("type", "UpdateSuiteMetadata", "name", "Revised suite")))));
        Map<String, Object> current = map(tool(f.task(), "getSuiteDraft").execute(Map.of("draftId", id)).output().get("draft"));
        assertThat(current).containsEntry("revision", suiteService.get(id).revision());
        assertThat(workState.build(f.task()).content().toString()).contains(id, "SUITE_DRAFT");
        assertThatThrownBy(() -> tool(task(f.projectId(), f.suiteId(), "tester"), "getSuiteDraft").execute(Map.of("draftId", id)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void reportsMalformedStructuredExtractionAsAnInputErrorWithoutCreatingADraft() {
        Fixture f = fixture();
        Map<String, Object> request = new LinkedHashMap<>(input(f, "Malformed order"));
        Map<String, Object> step = new LinkedHashMap<>(map(((List<?>) request.get("steps")).get(0)));
        step.put("response", Map.of("status", Map.of("operator", "equals", "expected", 201),
                "extract", Map.of("orderId", "$.id")));
        request.put("steps", List.of(step));
        assertThatThrownBy(() -> create(f.task(), request)).isInstanceOfSatisfying(ScenarioCommandException.class, error -> {
            assertThat(error.getCode()).isEqualTo("INVALID_COMMAND_FIELD");
            assertThat(error.getPath()).isEqualTo("scenario.steps[0].response.extract.orderId");
        });
        assertThat(drafts.findByAgentTaskIdAndStatusOrderByUpdatedAtDesc(f.task().id(), "ACTIVE")).isEmpty();
    }

    private void rename(AgentTaskRecord task, String id, String name) {
        var current = scenarios.get(id);
        tool(task, "applyNewScenarioCommands").execute(Map.of("draftId", id, "commandPlan", Map.of(
                "targetType", "SCENARIO_DRAFT", "targetId", id, "baseRevision", current.revision(),
                "commands", List.of(Map.of("type", "RenameStep", "stepId", current.steps().get(0).id(), "name", name)))));
    }

    private String publish(AgentTaskRecord task, String id) {
        return scenarios.publishNew(id, task.createdBy(), new ScenarioDraftService.PublishScenarioDraftRequest(false, null)).scenario().id();
    }

    private Map<String, Object> create(AgentTaskRecord task, Map<String, Object> input) {
        return map(tool(task, "createNewScenarioDraft").execute(input).output().get("draft"));
    }

    private Map<String, Object> input(Fixture f, String name) {
        Object operation = map(tool(f.task(), "getApiOperationSchema").execute(Map.of("operationRefId", f.operationId()))
                .output().get("operation")).get("dslOperation");
        Object queryOperation = map(tool(f.task(), "getApiOperationSchema").execute(Map.of("operationRefId", f.queryOperationId()))
                .output().get("operation")).get("dslOperation");
        return Map.of("name", name, "steps", List.of(
                Map.of("name", "Create order", "type", "http", "operation", operation,
                        "request", Map.of("body", Map.of("productId", "test-product")),
                        "response", Map.of("status", Map.of("operator", "equals", "expected", 201),
                                "assertions", List.of(Map.of("source", "response.body", "path", "$.id", "operator", "exists")),
                                "extract", Map.of("orderId", Map.of("source", "response.body", "path", "$.id")))),
                Map.of("name", "Query order", "type", "http", "operation", queryOperation,
                        "request", Map.of("path", Map.of("orderId", "${orderId}")),
                        "response", Map.of("status", Map.of("operator", "equals", "expected", 200)))));
    }

    private AgentTool tool(AgentTaskRecord task, String name) {
        return tools.toolsFor(task).stream().filter(item -> name.equals(item.definition().name())).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    private AgentTaskRecord task(String projectId, String suiteId, String actor) {
        return tasks.create(projectId, actor, new CreateAgentTaskRequest("Order workflow", "SUITE", suiteId,
                "FAKE", null, actor, 4096L, null));
    }

    private String suite(String projectId) {
        SuiteEntity suite = new SuiteEntity();
        suite.setProjectId(projectId); suite.setName("Order suite"); suite.setStatus("ACTIVE");
        return suites.saveAndFlush(suite).getId();
    }

    private Fixture fixture() {
        ProjectEntity project = new ProjectEntity();
        project.setCode("suite-repair-" + UUID.randomUUID()); project.setName("Suite repair"); project.setStatus("ACTIVE");
        String projectId = projects.saveAndFlush(project).getId();
        String suiteId = suite(projectId);
        ApiSpecEntity spec = new ApiSpecEntity();
        spec.setProjectId(projectId); spec.setName("orders.yaml"); spec.setContent("openapi: 3.0.3");
        spec = specs.saveAndFlush(spec);
        ApiOperationEntity operation = new ApiOperationEntity();
        operation.setProjectId(projectId); operation.setApiSpecId(spec.getId()); operation.setMethod("POST");
        operation.setPath("/orders"); operation.setOperationId("createOrder"); operation.setRawOperationJson("{}");
        operation.setResponsesJson("{\"201\":{\"description\":\"Created\"}}");
        operation = operations.saveAndFlush(operation);
        ApiOperationEntity query = new ApiOperationEntity();
        query.setProjectId(projectId); query.setApiSpecId(spec.getId()); query.setMethod("GET");
        query.setPath("/orders/{orderId}"); query.setOperationId("queryOrder"); query.setRawOperationJson("{}");
        query.setResponsesJson("{\"200\":{\"description\":\"Found\"}}");
        query = operations.saveAndFlush(query);
        return new Fixture(projectId, suiteId, operation.getId(), query.getId(), task(projectId, suiteId, "tester"));
    }

    private record Fixture(String projectId, String suiteId, String operationId, String queryOperationId, AgentTaskRecord task) {}
}
