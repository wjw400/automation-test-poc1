package com.example.poc.platform.agent.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScenarioDraftValidatorTest {
    private final ScenarioDslCodec codec = new ScenarioDslCodec();
    private final ApiOperationRepository operationRepository = mock(ApiOperationRepository.class);
    private final ScenarioDraftValidator validator = new ScenarioDraftValidator(operationRepository, codec, mock(ScenarioContractValidator.class));

    @Test
    void acceptsValidDraftAndDetectsVariableUsedBeforeExtraction() {
        ApiOperationEntity create = operation("op-create", "project-1", "POST", "/orders");
        ApiOperationEntity get = operation("op-get", "project-1", "GET", "/orders/{orderId}");
        when(operationRepository.findById("op-create")).thenReturn(Optional.of(create));
        when(operationRepository.findById("op-get")).thenReturn(Optional.of(get));

        ScenarioDocument valid = new ScenarioDocument(codec.parse("""
                dslVersion: v1
                name: Order lifecycle
                runtime: {}
                variables: {}
                steps:
                  - name: Create order
                    type: http
                    operation:
                      id: op-create
                      method: POST
                      path: /orders
                    request:
                      headers: {}
                      query: {}
                    response:
                      status:
                        operator: oneOf
                        expected: [201]
                      assertions: []
                      extract:
                        orderId:
                          source: response.body
                          path: $.id
                  - name: Read order
                    type: http
                    operation:
                      id: op-get
                      method: GET
                      path: /orders/{orderId}
                    request:
                      path:
                        orderId: ${orderId}
                      headers: {}
                      query: {}
                    response:
                      status:
                        operator: oneOf
                        expected: [200]
                      assertions: []
                      extract: {}
                """), List.of("create", "read"));

        assertThat(validator.validate("project-1", valid).valid()).isTrue();

        valid.scenario().getSteps().get(0).getRequest().getHeaders().put("X-Order", "${laterOrderId}");
        valid.scenario().getSteps().get(1).getResponse().getExtract().put(
                "laterOrderId", java.util.Map.of("source", "response.body", "path", "$.id"));
        ScenarioValidationResult invalid = validator.validate("project-1", valid);

        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.issues()).extracting(ScenarioValidationResult.ValidationIssue::code)
                .contains("VARIABLE_USED_BEFORE_EXTRACTION");
    }

    @Test
    void rejectsOperationFromAnotherProject() {
        when(operationRepository.findById("foreign-op"))
                .thenReturn(Optional.of(operation("foreign-op", "project-2", "GET", "/foreign")));
        ScenarioDocument document = singleStepDocument("""
                operation:
                  id: foreign-op
                  method: GET
                  path: /foreign
                request: {}
                actions:
                  beforeSend: []
                  afterReceive: []
                """);

        ScenarioValidationResult result = validator.validate("project-1", document);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ScenarioValidationResult.ValidationIssue::code)
                .contains("OPERATION_OUTSIDE_PROJECT");
    }

    @Test
    void acceptsManagedProfileReferencesAndValidPgpActions() {
        when(operationRepository.findById("secure-op"))
                .thenReturn(Optional.of(operation("secure-op", "project-1", "POST", "/secure")));
        ScenarioDocument document = singleStepDocument("""
                runtime:
                  profile: managed-secure-profile
                operation:
                  id: secure-op
                  method: POST
                  path: /secure
                request:
                  body:
                    orderId: O-1001
                actions:
                  beforeSend:
                    - type: crypto.pgp.signAndEncrypt
                      input:
                        source: request.body
                        format: json
                      output:
                        target: artifacts.encrypted
                        format: text
                  afterReceive:
                    - type: crypto.pgp.decryptAndVerify
                      input:
                        source: response.body
                        path: $.payload
                      output:
                        target: decrypted.payload
                        format: json
                """);

        ScenarioValidationResult result = validator.validate("project-1", document);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsRawAndUnavailableDecryptedExtractionSources() {
        when(operationRepository.findById("extract-op"))
                .thenReturn(Optional.of(operation("extract-op", "project-1", "GET", "/orders")));
        ScenarioDocument document = singleStepDocument("""
                operation:
                  id: extract-op
                  method: GET
                  path: /orders
                request: {}
                actions:
                  beforeSend: []
                  afterReceive: []
                """);

        document.scenario().getSteps().get(0).getResponse().getExtract().put(
                "rawValue", java.util.Map.of("source", "raw", "path", "$.id"));
        document.scenario().getSteps().get(0).getResponse().getExtract().put(
                "missingPayload", java.util.Map.of("source", "decrypted.missing", "path", "$.id"));

        ScenarioValidationResult result = validator.validate("project-1", document);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ScenarioValidationResult.ValidationIssue::code)
                .containsOnly("EXTRACTION_SOURCE_INVALID")
                .hasSize(2);
        assertThat(result.issues()).extracting(ScenarioValidationResult.ValidationIssue::message)
                .anyMatch(message -> message.contains("raw") && message.contains("not supported"))
                .anyMatch(message -> message.contains("decrypted.missing") && message.contains("not produced"));
    }

    @Test
    void acceptsExtractionFromAnEnabledAfterReceiveOutput() {
        when(operationRepository.findById("decrypt-op"))
                .thenReturn(Optional.of(operation("decrypt-op", "project-1", "POST", "/secure")));
        ScenarioDocument document = singleStepDocument("""
                runtime:
                  profile: secure-profile
                operation:
                  id: decrypt-op
                  method: POST
                  path: /secure
                request: {}
                actions:
                  beforeSend: []
                  afterReceive:
                    - type: crypto.pgp.decryptAndVerify
                      input:
                        source: response.body
                        path: $.payload
                      output:
                        target: decrypted.securePayload
                        format: json
                """);
        document.scenario().getSteps().get(0).getResponse().getExtract().put(
                "orderId", java.util.Map.of("source", "decrypted.securePayload", "path", "$.orderId"));

        assertThat(validator.validate("project-1", document).valid()).isTrue();
    }

    @Test
    void rejectsPostmanStyleVariablesAtTheRequestField() {
        when(operationRepository.findById("create-op"))
                .thenReturn(Optional.of(operation("create-op", "project-1", "POST", "/orders")));
        ScenarioDocument document = singleStepDocument("""
                operation:
                  id: create-op
                  method: POST
                  path: /orders
                request:
                  body:
                    items:
                      - productId: "{{list-products.productId}}"
                        quantity: 2
                actions:
                  beforeSend: []
                  afterReceive: []
                """);

        ScenarioValidationResult result = validator.validate("project-1", document);

        assertThat(result.valid()).isFalse();
        assertThat(result.issues())
                .filteredOn(issue -> "UNSUPPORTED_VARIABLE_SYNTAX".equals(issue.code()))
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.path()).isEqualTo("/steps/step-1/request/body/items/0/productId");
                    assertThat(issue.message()).contains("${variableName}");
                });
    }

    private ScenarioDocument singleStepDocument(String stepFields) {
        String yaml = """
                dslVersion: v1
                name: Validator test
                runtime: {}
                variables: {}
                steps:
                  - name: Test step
                    type: http
                %s
                    response:
                      status:
                        operator: oneOf
                        expected: [200]
                      assertions: []
                      extract: {}
                """.formatted(indent(stepFields, 4));
        return new ScenarioDocument(codec.parse(yaml), List.of("step-1"));
    }

    private String indent(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        return value.lines().map(line -> prefix + line).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private ApiOperationEntity operation(String id, String projectId, String method, String path) {
        ApiOperationEntity entity = new ApiOperationEntity();
        entity.setId(id);
        entity.setProjectId(projectId);
        entity.setApiSpecId("spec-1");
        entity.setMethod(method);
        entity.setPath(path);
        return entity;
    }
}
