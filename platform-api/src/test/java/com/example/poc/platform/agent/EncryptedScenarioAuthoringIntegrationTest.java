package com.example.poc.platform.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.poc.engine.ApiStep;
import com.example.poc.engine.HttpStatusSpec;
import com.example.poc.engine.ValidationSpec;
import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.AgentTaskService.CreateAgentTaskRequest;
import com.example.poc.platform.agent.capability.AgentToolRegistry;
import com.example.poc.platform.agent.scenario.ScenarioDocument;
import com.example.poc.platform.agent.scenario.ScenarioDraftService;
import com.example.poc.platform.agent.scenario.ScenarioDraftValidator;
import com.example.poc.platform.agent.scenario.ScenarioDslCodec;
import com.example.poc.platform.agent.scenario.ScenarioValidationResult;
import com.example.poc.platform.api.ApiSpecService;
import com.example.poc.platform.openapi.OperationProcessingService;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.ProjectEntity;
import com.example.poc.platform.persistence.entity.SuiteEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.SuiteRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:encrypted-authoring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "agent.deepseek.api-key="
})
class EncryptedScenarioAuthoringIntegrationTest {
    @Autowired ApiSpecService specs;
    @Autowired ApiOperationRepository operations;
    @Autowired ProjectRepository projects;
    @Autowired SuiteRepository suites;
    @Autowired AgentTaskService tasks;
    @Autowired AgentToolRegistry tools;
    @Autowired ScenarioDraftValidator validator;
    @Autowired ScenarioDraftService drafts;
    @Autowired ScenarioDslCodec codec;
    @Autowired JsonCodec json;
    @Autowired OperationProcessingService processing;
    String projectId;
    AgentTaskRecord task;
    Map<String, ApiOperationEntity> selected;

    @BeforeEach void importActualContract() throws Exception {
        ProjectEntity project = new ProjectEntity();
        project.setCode("encrypted-authoring-" + UUID.randomUUID()); project.setName("Encrypted authoring"); project.setStatus("ACTIVE");
        projectId = projects.saveAndFlush(project).getId();
        String yaml = Files.readString(Path.of("../mock-service/src/main/resources/openapi/mock-service-openapi.yaml"));
        var spec = specs.importSpec(projectId, new MockMultipartFile("file", "mock-service-openapi.yaml", "application/yaml", yaml.getBytes(StandardCharsets.UTF_8)));
        selected = new LinkedHashMap<>();
        operations.findByApiSpecIdOrderByMethodAscPathAsc(spec.id()).forEach(operation -> selected.put(operation.getOperationId(), operation));
        SuiteEntity suite = new SuiteEntity(); suite.setProjectId(projectId); suite.setName("Orders"); suite.setStatus("ACTIVE");
        suite = suites.saveAndFlush(suite);
        task = tasks.create(projectId, "tester", new CreateAgentTaskRequest("Create/query/cancel", "SUITE", suite.getId(), "FAKE", null, "tester", 16_384L, null));
    }

    @Test void givesTheAgentExpandedBusinessFieldsNestedExamplesAndTheCorrectResponseSource() {
        Map<String, Object> operation = read("createFulfillmentOrder");
        Map<String, Object> authoring = map(operation.get("authoring"));
        Map<String, Object> request = map(authoring.get("request"));
        Map<String, Object> schema = map(request.get("schema"));
        assertThat(schema.get("required")).isEqualTo(List.of("customerName", "items"));
        Map<String, Object> item = map(map(map(schema.get("properties")).get("items")).get("items"));
        assertThat(map(item.get("properties"))).containsKeys("productId", "quantity");
        assertThat(json.write(request.get("example"))).contains("customerName", "productId", "quantity").doesNotContain("sku", "currency");
        Map<String, Object> response = map(map(authoring.get("responses")).get("200"));
        assertThat(response.get("source")).isEqualTo("business.response.body");
        assertThat(map(map(response.get("schema")).get("properties"))).containsKeys("id", "status").doesNotContainKey("orderId");
        assertThat(json.write(response.get("schema"))).doesNotContain("\"$ref\"");
    }

    @Test void invalidGeneratedFieldsAndRawResponsePathsCannotPassDraftValidationOrPublish() {
        Map<String, Object> input = new LinkedHashMap<>(validInput());
        List<Map<String, Object>> steps = mutableSteps(input);
        steps.get(0).put("request", Map.of("body", Map.of("customerId", "CUST-1001", "currency", "USD",
                "lines", List.of(Map.of("sku", "SKU-10001", "quantity", 2)))));
        steps.get(0).put("response", Map.of("status", status(200), "assertions", List.of(
                assertion("response.body", "$.status", "CREATED")),
                "extract", Map.of("orderId", Map.of("source", "response.body", "path", "$.orderId"))));
        input.put("steps", steps);
        var draft = map(tool("createNewScenarioDraft", input).get("draft"));
        String id = (String) draft.get("id");
        ScenarioValidationResult result = drafts.validate(id);
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ScenarioValidationResult.ValidationIssue::code)
                .contains("REQUEST_BODY_REQUIRED", "REQUEST_BODY_UNKNOWN_FIELD", "BUSINESS_RESPONSE_SOURCE_REQUIRED", "RESPONSE_PATH_NOT_DECLARED");
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.path()).endsWith("/request/body/customerName");
            assertThat(issue.message()).contains("customerName");
        });
        assertThatThrownBy(() -> drafts.publishNew(id, "tester", new ScenarioDraftService.PublishScenarioDraftRequest(false, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pass validation");
    }

    @Test void acceptsCreateQueryCancelWithIdMappedToTheOrderIdVariable() {
        var draft = map(tool("createNewScenarioDraft", validInput()).get("draft"));
        var result = drafts.get((String) draft.get("id"));
        assertThat(result.validation().valid()).as("%s", result.validation().issues()).isTrue();
        var scenario = codec.parse(result.content());
        assertThat(scenario.getSteps()).hasSize(3);
        assertThat(scenario.getSteps().get(0).getResponse().getExtract().get("orderId").toString())
                .contains("$.id", "business.response.body");
        assertThat(scenario.getSteps().get(1).getRequest().getBody()).isEqualTo(Map.of("orderId", "${orderId}"));
        assertThat(scenario.getSteps().get(2).getResponse().getAssertions().get(0).getExpected()).isEqualTo("CANCELLED");
        Map<String, Object> sources = tool("getStepDataSources", Map.of("draftId", result.id(), "stepId", result.steps().get(0).id(), "usage", "ASSERTION"));
        Map<String, Object> business = ((List<?>) sources.get("sources")).stream().map(this::map)
                .filter(source -> "business.response.body".equals(source.get("name"))).findFirst().orElseThrow();
        assertThat(business.get("description").toString()).contains("automatic API response decoding").doesNotContain("produced by an enabled afterReceive");
    }

    @Test void correctedUserYamlAlsoMatchesTheActualImportedContract() throws Exception {
        var scenario = codec.parse(Files.readString(Path.of("../deliverables/encrypted-order-create-query-cancel.fixed.yaml")));
        for (var step : scenario.getSteps()) {
            var operation = selected.get(step.getOperation().getOperationId());
            step.getOperation().setId(operation.getId());
            step.getOperation().setApiSpecId(operation.getApiSpecId());
        }
        var result = validator.validate(projectId, new ScenarioDocument(scenario, List.of("create", "query", "cancel")));
        assertThat(result.valid()).as("%s", result.issues()).isTrue();
    }

    @Test void validatesNestedItemsButAllowsRuntimeVariableValuesAndIntentionalNegativeRequests() {
        ScenarioDocument document = document();
        ApiStep step = document.scenario().getSteps().get(0);
        step.getRequest().setBody(Map.of("customerName", "Test", "items", List.of(Map.of("sku", "wrong", "quantity", 0))));
        var invalid = validator.validate(projectId, document);
        assertThat(invalid.issues()).extracting(ScenarioValidationResult.ValidationIssue::code)
                .contains("REQUEST_BODY_REQUIRED", "REQUEST_BODY_UNKNOWN_FIELD", "REQUEST_BODY_MINIMUM");
        step.getRequest().setBody(Map.of("customerName", "${customer}", "items", List.of(Map.of("productId", "${productId}", "quantity", "${quantity}"))));
        assertThat(validator.validate(projectId, document).valid()).isTrue();
        document.scenario().setSteps(List.of(step));
        step.getRequest().setBody(Map.of("intentionallyInvalid", true));
        HttpStatusSpec status = new HttpStatusSpec(); status.setOperator("equals"); status.setExpected(400);
        step.getResponse().setStatus(status); step.getResponse().setAssertions(List.of()); step.getResponse().setExtract(Map.of());
        var negative = validator.validate(projectId, new ScenarioDocument(document.scenario(), List.of("negative")));
        assertThat(negative.valid()).as("%s", negative.issues()).isTrue();
        assertThat(negative.issues()).isNotEmpty().allSatisfy(issue -> assertThat(issue.severity()).isEqualTo(ScenarioValidationResult.Severity.WARNING));
    }

    @Test void allowsWireMetadataAndUsesCurrentPlatformSchemaOverrides() {
        var document = document();
        ValidationSpec metadata = new ValidationSpec(); metadata.setSource("wire.response.body"); metadata.setPath("$.code"); metadata.setExpected("SUCCESS");
        document.scenario().getSteps().get(0).getResponse().getAssertions().add(metadata);
        assertThat(validator.validate(projectId, document).valid()).isTrue();
        ApiOperationEntity operation = selected.get("createFulfillmentOrder");
        var settings = processing.get(projectId, operation.getId());
        var configuration = settings.configuration();
        configuration.getRequest().setBusinessSchema(Map.of("type", "object", "required", List.of("tenant"),
                "properties", Map.of("tenant", Map.of("type", "string"))));
        processing.save(projectId, operation.getId(), new OperationProcessingService.SaveRequest(settings.revision(), configuration));
        assertThat(json.write(map(read("createFulfillmentOrder").get("authoring")).get("request"))).contains("tenant");
        assertThat(validator.validate(projectId, document).issues()).anySatisfy(issue -> assertThat(issue.path()).endsWith("/request/body/tenant"));
    }

    private ScenarioDocument document() {
        Map<String, Object> input = new LinkedHashMap<>(validInput());
        input.put("dslVersion", "v1");
        return new ScenarioDocument(codec.parse(json.write(input)), List.of("create", "query", "cancel"));
    }

    private Map<String, Object> validInput() {
        return Map.of("name", "Encrypted create/query/cancel", "steps", List.of(
                step("Create", "createFulfillmentOrder", Map.of("customerName", "Automation Partner", "items", List.of(Map.of("productId", "P100", "quantity", 1))), "CREATED", true),
                step("Query", "queryFulfillmentOrder", Map.of("orderId", "${orderId}"), "CREATED", false),
                step("Cancel", "cancelFulfillmentOrder", Map.of("orderId", "${orderId}"), "CANCELLED", false)));
    }

    private Map<String, Object> step(String name, String operation, Object body, String expected, boolean extract) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status(200)); response.put("assertions", List.of(assertion("business.response.body", "$.status", expected)));
        if (extract) response.put("extract", Map.of("orderId", Map.of("source", "business.response.body", "path", "$.id")));
        return Map.of("name", name, "type", "http", "operation", read(operation).get("dslOperation"),
                "request", Map.of("body", body), "response", response);
    }

    private Map<String, Object> assertion(String source, String path, String expected) { return Map.of("source", source, "path", path, "operator", "equals", "expected", expected); }
    private Map<String, Object> status(int value) { return Map.of("operator", "equals", "expected", value); }
    private Map<String, Object> read(String operation) { return map(tool("getApiOperationSchema", Map.of("operationRefId", selected.get(operation).getId())).get("operation")); }
    private Map<String, Object> tool(String name, Map<String, Object> input) {
        return tools.toolsFor(task).stream().filter(tool -> name.equals(tool.definition().name())).findFirst().orElseThrow().execute(input).output();
    }
    private List<Map<String, Object>> mutableSteps(Map<String, Object> input) {
        List<Map<String, Object>> result = new ArrayList<>();
        ((List<?>) input.get("steps")).forEach(step -> result.add(new LinkedHashMap<>(map(step)))); return result;
    }
    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
}
