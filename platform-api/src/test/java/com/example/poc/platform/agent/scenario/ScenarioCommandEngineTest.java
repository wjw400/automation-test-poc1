package com.example.poc.platform.agent.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.poc.engine.ActionInputSpec;
import com.example.poc.engine.ActionOutputSpec;
import com.example.poc.engine.ApiStep;
import com.example.poc.engine.CaseActionSpec;
import com.example.poc.engine.ExtractValueSpec;
import com.example.poc.engine.OperationSpec;
import com.example.poc.engine.ValidationSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioCommandEngineTest {
    private final ScenarioDslCodec codec = new ScenarioDslCodec();
    private final ScenarioCommandDecoder decoder = new ScenarioCommandDecoder(codec);
    private final ScenarioCommandEngine engine = new ScenarioCommandEngine(codec);
    private final ScenarioDiffService diffService = new ScenarioDiffService(codec);

    @Test
    void appliesAcceptanceCommandPlanWithoutChangingSourceScenario() {
        ScenarioDocument source = sourceDocument();
        ScenarioCommandPlan plan = decoder.decode(Map.of(
                "targetType", "SCENARIO_DRAFT",
                "targetId", "draft-1",
                "baseRevision", 0,
                "commands", List.of(
                        Map.of(
                                "type", "SetRequestHeader",
                                "stepId", "create-order",
                                "name", "X-Correlation-Id",
                                "value", "${requestId}"),
                        Map.of(
                                "type", "AddExtraction",
                                "stepId", "create-order",
                                "name", "orderId",
                                "extraction", Map.of(
                                        "source", "response.body",
                                        "path", "$.data.id")))));

        ScenarioDocument updated = engine.apply(source, plan.commands(), ignored -> operation("op-create", "POST", "/orders"));
        ApiStep updatedStep = updated.scenario().getSteps().get(0);

        assertThat(updatedStep.getRequest().getHeaders())
                .containsEntry("X-Correlation-Id", "${requestId}");
        assertThat(updatedStep.getResponse().getExtract()).containsKey("orderId");
        assertThat(source.scenario().getSteps().get(0).getRequest().getHeaders())
                .doesNotContainKey("X-Correlation-Id");
        assertThat(source.scenario().getSteps().get(0).getResponse().getExtract())
                .doesNotContainKey("orderId");

        ScenarioDraftDiff diff = diffService.diff(source, updated);
        assertThat(diff.items()).extracting(ScenarioDraftDiff.DiffItem::path)
                .contains(
                        "/steps/create-order/request/headers/X-Correlation-Id",
                        "/steps/create-order/response/extract/orderId");
    }

    @Test
    void supportsTheCompleteInitialCommandVocabulary() {
        ScenarioDocument source = sourceDocument();
        CaseActionSpec before = action("data.encodeBase64", "request.body", "artifacts.encoded");
        CaseActionSpec after = action("crypto.pgp.decryptAndVerify", "response.body", "decrypted.payload");
        ValidationSpec assertion = assertion("response.body", "$.code", "equals", "SUCCESS");
        ExtractValueSpec extraction = extraction("response.body", "$.data.id");
        ApiStep temporaryStep = codec.convert(Map.of(
                "name", "Temporary",
                "type", "http",
                "operation", Map.of(
                        "id", "op-temp",
                        "method", "GET",
                        "path", "/temporary"),
                "request", Map.of(),
                "response", Map.of("status", 200)), ApiStep.class, "step");

        List<ScenarioCommand> commands = List.of(
                new ScenarioCommand.SetRequestHeader("create-order", "X-Test", "true"),
                new ScenarioCommand.RemoveRequestHeader("create-order", "Content-Type"),
                new ScenarioCommand.SetQueryParameter("create-order", "expand", "lines"),
                new ScenarioCommand.SetPathParameter("create-order", "tenantId", "cn"),
                new ScenarioCommand.SetRequestBody("create-order", Map.of("order", Map.of("customer", Map.of()))),
                new ScenarioCommand.SetRequestBodyValue("create-order", "/order/customer/name", "Blaine"),
                new ScenarioCommand.AddBeforeAction("create-order", before),
                new ScenarioCommand.AddAfterAction("create-order", after),
                new ScenarioCommand.AddAssertion("create-order", assertion),
                new ScenarioCommand.RemoveAssertion("create-order", 0),
                new ScenarioCommand.AddExtraction("create-order", "temporary", extraction),
                new ScenarioCommand.RemoveExtraction("create-order", "temporary"),
                new ScenarioCommand.AddExtraction("create-order", "orderId", extraction),
                new ScenarioCommand.RenameStep("create-order", "Create order for tenant"),
                new ScenarioCommand.SelectOperation("create-order", "op-create-v2"),
                new ScenarioCommand.AddStep("temporary-step", "create-order", null, temporaryStep),
                new ScenarioCommand.MoveStep("temporary-step", 0),
                new ScenarioCommand.RemoveStep("temporary-step"));

        ScenarioDocument updated = engine.apply(
                source,
                commands,
                operationId -> operation(operationId, "POST", "/tenants/{tenantId}/orders"));
        ApiStep step = updated.scenario().getSteps().get(0);

        assertThat(updated.stepIds()).containsExactly("create-order", "get-order");
        assertThat(step.getName()).isEqualTo("Create order for tenant");
        assertThat(step.getOperation().getId()).isEqualTo("op-create-v2");
        assertThat(step.getRequest().getHeaders()).containsEntry("X-Test", "true").doesNotContainKey("Content-Type");
        assertThat(step.getRequest().getQuery()).containsEntry("expand", "lines");
        assertThat(step.getRequest().getPath()).containsEntry("tenantId", "cn");
        assertThat(step.getRequest().getBody()).isEqualTo(Map.of(
                "order", Map.of("customer", Map.of("name", "Blaine"))));
        assertThat(step.getActions().getBeforeSend()).hasSize(1);
        assertThat(step.getActions().getAfterReceive()).hasSize(1);
        assertThat(step.getResponse().getAssertions()).singleElement().satisfies(value ->
                assertThat(value.getExpected()).isEqualTo("SUCCESS"));
        assertThat(step.getResponse().getExtract()).containsOnlyKeys("orderId");
    }

    @Test
    void commandPlanIsAtomicWhenALaterCommandFails() {
        ScenarioDocument source = sourceDocument();

        assertThatThrownBy(() -> engine.apply(
                source,
                List.of(
                        new ScenarioCommand.SetRequestHeader("create-order", "X-Test", "true"),
                        new ScenarioCommand.RemoveAssertion("create-order", 99)),
                ignored -> null))
                .isInstanceOf(ScenarioCommandException.class)
                .satisfies(error -> assertThat(((ScenarioCommandException) error).getCommandIndex()).isEqualTo(1));

        assertThat(source.scenario().getSteps().get(0).getRequest().getHeaders())
                .doesNotContainKey("X-Test");
    }

    @Test
    void rejectsUnknownCommandFields() {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("type", "SetRequestHeader");
        command.put("stepId", "create-order");
        command.put("name", "X-Test");
        command.put("value", "true");
        command.put("profile", "must-not-be-accepted");

        assertThatThrownBy(() -> decoder.decode(Map.of(
                "targetType", "SCENARIO_DRAFT",
                "targetId", "draft-1",
                "baseRevision", 0,
                "commands", List.of(command))))
                .isInstanceOf(ScenarioCommandException.class)
                .satisfies(error -> {
                    ScenarioCommandException commandError = (ScenarioCommandException) error;
                    assertThat(commandError.getCode()).isEqualTo("UNKNOWN_COMMAND_FIELD");
                    assertThat(commandError.getCommandIndex()).isZero();
                });
    }

    @Test
    void rejectsMissingTargetsForEveryTargetedCommand() {
        CaseActionSpec action = action("data.encodeBase64", "request.body", "artifacts.encoded");
        ValidationSpec assertion = assertion("response.body", "$.status", "equals", "OK");
        ExtractValueSpec extraction = extraction("response.body", "$.id");
        ApiStep step = sourceDocument().scenario().getSteps().get(0);
        List<ScenarioCommand> commands = List.of(
                new ScenarioCommand.SetRequestHeader("missing", "X-Test", "true"),
                new ScenarioCommand.RemoveRequestHeader("missing", "X-Test"),
                new ScenarioCommand.SetQueryParameter("missing", "q", "value"),
                new ScenarioCommand.SetPathParameter("missing", "id", "value"),
                new ScenarioCommand.SetRequestBody("missing", Map.of()),
                new ScenarioCommand.SetRequestBodyValue("missing", "/id", "value"),
                new ScenarioCommand.AddBeforeAction("missing", action),
                new ScenarioCommand.AddAfterAction("missing", action),
                new ScenarioCommand.AddAssertion("missing", assertion),
                new ScenarioCommand.RemoveAssertion("missing", 0),
                new ScenarioCommand.AddExtraction("missing", "id", extraction),
                new ScenarioCommand.RemoveExtraction("missing", "id"),
                new ScenarioCommand.AddStep("new-step", "missing", null, step),
                new ScenarioCommand.RemoveStep("missing"),
                new ScenarioCommand.MoveStep("missing", 0),
                new ScenarioCommand.RenameStep("missing", "Renamed"),
                new ScenarioCommand.SelectOperation("missing", "op-create"));

        for (ScenarioCommand command : commands) {
            assertThatThrownBy(() -> engine.apply(
                    sourceDocument(), List.of(command), ignored -> operation("op-create", "POST", "/orders")))
                    .as(command.getClass().getSimpleName())
                    .isInstanceOf(ScenarioCommandException.class)
                    .satisfies(error -> assertThat(((ScenarioCommandException) error).getCode())
                            .isEqualTo("COMMAND_TARGET_NOT_FOUND"));
        }
    }

    @Test
    void decoderRejectsInvalidInputForEveryCommandType() {
        List<String> commandTypes = List.of(
                "SetRequestHeader",
                "RemoveRequestHeader",
                "SetQueryParameter",
                "SetPathParameter",
                "SetRequestBody",
                "SetRequestBodyValue",
                "AddBeforeAction",
                "AddAfterAction",
                "AddAssertion",
                "RemoveAssertion",
                "AddExtraction",
                "RemoveExtraction",
                "AddStep",
                "RemoveStep",
                "MoveStep",
                "RenameStep",
                "SelectOperation");

        for (String commandType : commandTypes) {
            assertThatThrownBy(() -> decoder.decode(Map.of(
                    "targetType", "SCENARIO_DRAFT",
                    "targetId", "draft-1",
                    "baseRevision", 0,
                    "commands", List.of(Map.of("type", commandType)))))
                    .as(commandType)
                    .isInstanceOf(ScenarioCommandException.class);
        }
    }

    @Test
    void addsAStepDirectlyAtTheRequestedIndex() {
        ScenarioDocument source = sourceDocument();
        ApiStep firstStep = codec.convert(Map.of(
                "name", "Authenticate",
                "type", "http",
                "operation", Map.of(
                        "id", "op-auth",
                        "method", "POST",
                        "path", "/sessions"),
                "request", Map.of(),
                "response", Map.of("status", 200)), ApiStep.class, "step");
        ScenarioCommandPlan plan = decoder.decode(Map.of(
                "targetType", "SCENARIO_DRAFT",
                "targetId", "draft-1",
                "baseRevision", 0,
                "commands", List.of(Map.of(
                        "type", "AddStep",
                        "stepId", "authenticate",
                        "targetIndex", 0,
                        "step", codec.toGeneric(firstStep)))));

        ScenarioDocument updated = engine.apply(source, plan.commands(), ignored -> null);

        assertThat(updated.stepIds()).containsExactly("authenticate", "create-order", "get-order");
        assertThat(updated.scenario().getSteps().get(0).getName()).isEqualTo("Authenticate");
    }

    @Test
    void yamlRoundTripPreservesScenarioSemantics() {
        ScenarioDocument source = sourceDocument();

        Object before = codec.toGeneric(source.scenario());
        Object after = codec.toGeneric(codec.parse(codec.write(source.scenario())));

        assertThat(after).isEqualTo(before);
    }

    @Test
    void diffUsesStableStepOrderAndFieldLocations() {
        ScenarioDocument source = sourceDocument();
        ScenarioDocument updated = engine.apply(
                source,
                List.of(
                        new ScenarioCommand.MoveStep("get-order", 0),
                        new ScenarioCommand.SetRequestHeader("create-order", "X-Test", "true")),
                ignored -> null);

        ScenarioDraftDiff diff = diffService.diff(source, updated);

        assertThat(diff.items()).extracting(ScenarioDraftDiff.DiffItem::type)
                .containsExactly(
                        ScenarioDraftDiff.ChangeType.MOVE,
                        ScenarioDraftDiff.ChangeType.ADD,
                        ScenarioDraftDiff.ChangeType.MOVE);
        assertThat(diff.items()).extracting(ScenarioDraftDiff.DiffItem::path)
                .containsExactly(
                        "/steps/create-order",
                        "/steps/create-order/request/headers/X-Test",
                        "/steps/get-order");
    }

    private ScenarioDocument sourceDocument() {
        return new ScenarioDocument(codec.parse("""
                dslVersion: v1
                name: Order lifecycle
                description: Create and read an order.
                tags: [agent]
                runtime: {}
                variables:
                  requestId: req-1001
                steps:
                  - name: Create order
                    type: http
                    operation:
                      id: op-create
                      apiSpecId: spec-1
                      operationId: createOrder
                      method: POST
                      path: /orders
                    request:
                      headers:
                        Content-Type: application/json
                      query: {}
                      body:
                        customer: original
                    actions:
                      beforeSend: []
                      afterReceive: []
                    response:
                      status: 201
                      assertions:
                        - source: response.body
                          path: $.status
                          operator: equals
                          expected: CREATED
                      extract: {}
                  - name: Get order
                    type: http
                    operation:
                      id: op-get
                      apiSpecId: spec-1
                      operationId: getOrder
                      method: GET
                      path: /orders/${orderId}
                    request:
                      headers: {}
                      query: {}
                    actions:
                      beforeSend: []
                      afterReceive: []
                    response:
                      status: 200
                      assertions: []
                      extract: {}
                """), List.of("create-order", "get-order"));
    }

    private CaseActionSpec action(String type, String source, String target) {
        CaseActionSpec action = new CaseActionSpec();
        action.setType(type);
        ActionInputSpec input = new ActionInputSpec();
        input.setSource(source);
        action.setInput(input);
        ActionOutputSpec output = new ActionOutputSpec();
        output.setTarget(target);
        action.setOutput(output);
        return action;
    }

    private ValidationSpec assertion(String source, String path, String operator, Object expected) {
        ValidationSpec assertion = new ValidationSpec();
        assertion.setSource(source);
        assertion.setPath(path);
        assertion.setOperator(operator);
        assertion.setExpected(expected);
        return assertion;
    }

    private ExtractValueSpec extraction(String source, String path) {
        ExtractValueSpec extraction = new ExtractValueSpec();
        extraction.setSource(source);
        extraction.setPath(path);
        return extraction;
    }

    private OperationSpec operation(String id, String method, String path) {
        OperationSpec operation = new OperationSpec();
        operation.setId(id);
        operation.setApiSpecId("spec-1");
        operation.setOperationId(id);
        operation.setMethod(method);
        operation.setPath(path);
        return operation;
    }
}
