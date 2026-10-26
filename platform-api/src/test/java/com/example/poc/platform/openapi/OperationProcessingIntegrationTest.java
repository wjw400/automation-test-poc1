package com.example.poc.platform.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.poc.engine.OperationProcessingSpec;
import com.example.poc.platform.api.ApiSpecService;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.*;
import com.example.poc.platform.persistence.repo.*;
import com.example.poc.platform.run.RunExecutionService;
import com.sun.net.httpserver.HttpServer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:operation-processing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "agent.deepseek.api-key="
})
@AutoConfigureMockMvc
class OperationProcessingIntegrationTest {
    @Autowired ApiSpecService specs;
    @Autowired OperationProcessingService processing;
    @Autowired OpenApiContractService contracts;
    @Autowired RunExecutionService runs;
    @Autowired ProjectRepository projects;
    @Autowired ApiOperationRepository operations;
    @Autowired EnvironmentRepository environments;
    @Autowired ProfileRepository profiles;
    @Autowired CaseRepository cases;
    @Autowired JsonCodec json;
    @Autowired MockMvc mvc;
    String projectId;

    @BeforeEach void project() {
        ProjectEntity project = new ProjectEntity(); project.setCode("pgp-" + UUID.randomUUID());
        project.setName("PGP configuration"); project.setStatus("ACTIVE"); projectId = projects.saveAndFlush(project).getId();
    }

    @Test void importsExtensionsKeepsManualOverridesAndPreservesOperationReferences() {
        var spec = importSpec(extension("payload"));
        var operation = specs.listOperationsBySpec(projectId, spec.id()).get(0);
        var initial = processing.get(projectId, operation.id());
        assertThat(initial.configuration().getRequest().getCiphertext().getPointer()).isEqualTo("/payload");
        var manual = initial.configuration(); manual.getRequest().getCiphertext().setPointer("/data/encryptdata");
        processing.save(projectId, operation.id(), new OperationProcessingService.SaveRequest(initial.revision(), manual));
        importSpec(extension("newField"));

        var current = processing.get(projectId, operation.id());
        assertThat(specs.listOperationsBySpec(projectId, spec.id()).get(0).id()).isEqualTo(operation.id());
        assertThat(current.configuration().getRequest().getCiphertext().getPointer()).isEqualTo("/data/encryptdata");
        assertThat(current.importedConfiguration().getRequest().getCiphertext().getPointer()).isEqualTo("/newField");
        assertThat(current.needsReview()).isTrue();
        assertThat(contracts.getOperationContract(projectId, operation.id()).processingSettings().configuration().getRequest().getCiphertext().getPointer()).isEqualTo("/data/encryptdata");
        var confirmed = processing.save(projectId, operation.id(), new OperationProcessingService.SaveRequest(current.revision(), current.configuration()));
        assertThat(confirmed.needsReview()).isFalse();
        assertThatThrownBy(() -> processing.save(projectId, operation.id(), new OperationProcessingService.SaveRequest(current.revision(), current.configuration())))
                .isInstanceOf(OperationProcessingService.ConfigurationConflictException.class);
        var reset = processing.useImported(projectId, operation.id(), confirmed.revision());
        assertThat(reset.source()).isEqualTo("IMPORTED");
        assertThat(reset.configuration().getRequest().getCiphertext().getPointer()).isEqualTo("/newField");
    }

    @Test void savesAndUpdatesProfileKeysByPurposeWithoutSwappingOurAndTheirMaterial() throws Exception {
        EnvironmentEntity environment = new EnvironmentEntity();
        environment.setProjectId(projectId); environment.setName("Test");
        environment.setBaseUrl("http://localhost"); environment.setStatus("ACTIVE");
        environment.setVariablesJson("{}"); environment.setEndpointsJson("{}");
        environments.saveAndFlush(environment);
        Map<String, Object> body = new java.util.LinkedHashMap<>(Map.of(
                "name", "Independent key roles", "type", "PGP", "enabled", true,
                "environmentIds", List.of(environment.getId()),
                "signingPrivateKey", "our-signing-key", "signingPrivateKeyPassphrase", "signing-passphrase",
                "decryptionPrivateKey", "our-decryption-key", "decryptionPrivateKeyPassphrase", "decryption-passphrase",
                "encryptionPublicKey", "their-encryption-key", "verificationPublicKey", "their-verification-key"));
        String response = mvc.perform(post("/api/projects/{projectId}/profiles", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(json.write(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<String, Object> created = json.readObjectMap(response);
        assertThat(created).containsAllEntriesOf(body);
        assertThat(created.keySet()).noneMatch(key -> key.startsWith("sender") || key.startsWith("recipient"));

        String id = (String) created.get("id");
        body.put("signingPrivateKey", "rotated-signing-key");
        mvc.perform(put("/api/projects/{projectId}/profiles/{profileId}", projectId, id)
                        .contentType(MediaType.APPLICATION_JSON).content(json.write(body)))
                .andExpect(status().isOk());
        ProfileEntity saved = profiles.findById(id).orElseThrow();
        assertThat(saved.getSigningPrivateKey()).isEqualTo("rotated-signing-key");
        assertThat(saved.getSigningPrivateKeyPassphrase()).isEqualTo("signing-passphrase");
        assertThat(saved.getDecryptionPrivateKey()).isEqualTo("our-decryption-key");
        assertThat(saved.getDecryptionPrivateKeyPassphrase()).isEqualTo("decryption-passphrase");
        assertThat(saved.getEncryptionPublicKey()).isEqualTo("their-encryption-key");
        assertThat(saved.getVerificationPublicKey()).isEqualTo("their-verification-key");
    }

    @Test void configuresOrdinaryOpenApiAndReturnsConflictForStaleWrites() throws Exception {
        var spec = importSpec("");
        var operation = specs.listOperationsBySpec(projectId, spec.id()).get(0);
        var settings = processing.get(projectId, operation.id());
        assertThat(settings.configuration()).isNull();
        var configuration = configuration("/data/encryptdata");
        String body = json.write(new OperationProcessingService.SaveRequest(settings.revision(), configuration));
        mvc.perform(put("/api/projects/{projectId}/operations/{operationId}/processing", projectId, operation.id()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(put("/api/projects/{projectId}/operations/{operationId}/processing", projectId, operation.id()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertThat(specs.getSpecContent(projectId, spec.id())).doesNotContain("x-platform-processing");
        assertThat(contracts.getOperationContract(projectId, operation.id()).capabilities().encrypted()).isTrue();
    }

    @Test void normalizesLegacyPipelineAndDiagnosesUnsupportedExtensions() {
        var normalized = processing.normalize(Map.of("x-platform-processing", Map.of(
                "request", Map.of("pipeline", "system/pgp-json-envelope-request@1", "options", Map.of("parameters", Map.of("envelopePointer", "/data/encryptdata"))),
                "response", Map.of("200", Map.of("pipeline", "system/pgp-json-envelope-response@1")))));
        assertThat(normalized.error()).isNull();
        assertThat(normalized.configuration().getRequest().getCiphertext().getPointer()).isEqualTo("/data/encryptdata");
        assertThat(processing.normalize(Map.of("x-platform-processing", Map.of("request", Map.of("pipeline", "unknown@1")))).error()).contains("Unsupported processing pipeline");
        assertThat(processing.normalize(Map.of("x-platform-processing", Map.of("version", 2))).error()).contains("version");
    }

    @Test void ignoresRemovedExtensionsWithoutSchemaFallbackOrSyntheticProcessing() {
        var spec = importSpec("""
                      x-platform-payload:
                        request:
                          business:
                            schema: {type: object, properties: {oldRequest: {type: string}}}
                          transform:
                            steps:
                              - {type: pgp-sign-encrypt}
                        responses:
                          '200':
                            business:
                              schema: {type: object, properties: {oldResponse: {type: string}}}
                            transform:
                              steps:
                                - {type: pgp-decrypt-verify}
                      x-plaintext-request-schema: {type: object}
                      x-plaintext-response-schema: {type: object}
                """);
        var operation = specs.listOperationsBySpec(projectId, spec.id()).get(0);
        var contract = contracts.getOperationContract(projectId, operation.id());
        assertThat(contract.request().business()).isNull();
        assertThat(contract.request().processing()).isNull();
        assertThat(contract.responses()).allSatisfy(response -> {
            assertThat(response.business()).isNull();
            assertThat(response.processing()).isNull();
        });
        assertThat(contract.capabilities().hasBusinessRequest()).isFalse();
        assertThat(contract.capabilities().hasBusinessResponse()).isFalse();
        assertThat(contract.capabilities().hasRequestProcessing()).isFalse();
        assertThat(contract.capabilities().hasResponseProcessing()).isFalse();
        assertThat(contract.capabilities().encrypted()).isFalse();
        assertThat(contract.diagnostics()).isEmpty();
        assertThat(processing.get(projectId, operation.id()).configuration()).isNull();

        var current = processing.get(projectId, operation.id());
        var manual = configuration("/data/encryptdata");
        manual.getRequest().setBusinessSchema(Map.of("type", "object", "properties", Map.of("currentRequest", Map.of("type", "string"))));
        processing.save(projectId, operation.id(), new OperationProcessingService.SaveRequest(current.revision(), manual));
        var configured = contracts.getOperationContract(projectId, operation.id());
        assertThat(json.write(configured.request().business().schema())).contains("currentRequest").doesNotContain("oldRequest");
        assertThat(configured.responses()).allSatisfy(response -> assertThat(response.business()).isNull());
        assertThat(configured.capabilities().encrypted()).isTrue();
    }

    @Test void importsCurrentMockServiceDocumentWithBusinessSchemasAndPlainErrorRules() throws Exception {
        String content = Files.readString(Path.of("../mock-service/src/main/resources/openapi/mock-service-openapi.yaml"));
        var spec = specs.importSpec(projectId, new MockMultipartFile("file", "mock.yaml", "application/yaml", content.getBytes(StandardCharsets.UTF_8)));
        var contract = contracts.getSpecContract(projectId, spec.id());
        assertThat(contract.operations()).hasSize(24);
        var secure = contract.operations().stream().filter(operation -> operation.path().equals("/secure/fulfillment/orders/create")).findFirst().orElseThrow();
        assertThat(secure.processingSettings().importError()).isNull();
        assertThat(secure.processingSettings().configuration().getRequest().getCiphertext().getPointer()).isEqualTo("/payload");
        assertThat(secure.processingSettings().configuration().getResponses().get("400").getProtection()).isEqualTo("none");
        assertThat(secure.request().business().schemaRef()).isEqualTo("#/components/schemas/CreateOrderPayload");
    }

    @Test void rejectsMisspelledProtectionInsteadOfSilentlyDisablingEncryption() throws Exception {
        var spec = importSpec("");
        var operation = specs.listOperationsBySpec(projectId, spec.id()).get(0);
        mvc.perform(put("/api/projects/{projectId}/operations/{operationId}/processing", projectId, operation.id())
                .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":1,\"configuration\":{\"request\":{\"protecion\":\"pgp-encrypt\"}}}"))
                .andExpect(status().isBadRequest());
        assertThat(processing.get(projectId, operation.id()).configuration()).isNull();
    }

    @Test void persistsActualProcessingAndFreezesHistoryAcrossConfigurationChanges() throws Exception {
        var spec = importSpec("");
        var operation = specs.listOperationsBySpec(projectId, spec.id()).get(0);
        var settings = processing.get(projectId, operation.id());
        settings = processing.save(projectId, operation.id(), new OperationProcessingService.SaveRequest(settings.revision(), configuration("/data/encryptdata")));
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/orders", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"orderId\":\"O-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            EnvironmentEntity environment = new EnvironmentEntity(); environment.setProjectId(projectId); environment.setName("Local");
            environment.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort()); environment.setEndpointsJson("{}"); environment.setVariablesJson("{}"); environment.setStatus("ACTIVE");
            environment = environments.saveAndFlush(environment);
            ProfileEntity profile = new ProfileEntity(); profile.setProjectId(projectId); profile.setName("Partner"); profile.setType("PGP"); profile.setEnabled(true); profile.setEnvironmentIds(Set.of(environment.getId()));
            Path key = Path.of("../test-framework/src/test/resources/keys/server-public.asc");
            profile.setEncryptionPublicKey(Files.readString(key)); profile = profiles.saveAndFlush(profile);
            CaseEntity testCase = new CaseEntity(); testCase.setProjectId(projectId); testCase.setName("Create order"); testCase.setCode("pgp-history"); testCase.setDslVersion("v1"); testCase.setStatus("ACTIVE"); testCase.setTagsJson("[]");
            testCase.setContent("""
                    dslVersion: v1
                    name: Create order
                    steps:
                      - name: Create
                        operation:
                          id: %s
                          apiSpecId: %s
                          method: POST
                          path: /orders
                        request:
                          body: {orderId: O-1}
                        actions: {beforeSend: [], afterReceive: []}
                        response:
                          status: {operator: equals, expected: 200}
                          extract:
                            orderId: {source: business.response.body, path: $.orderId}
                    """.formatted(operation.id(), spec.id()));
            testCase = cases.saveAndFlush(testCase);
            var run = runs.startRun(new RunExecutionService.StartRunRequest(testCase.getId(), environment.getId(), profile.getId(), "test"));
            assertThat(run.status()).as("Run error: %s", run.errorMessage()).isEqualTo("PASSED");
            assertThat(request.get()).contains("encryptdata", "BEGIN PGP MESSAGE");
            assertThat(run.steps().get(0).actions()).hasSize(2).allMatch(action -> "API".equals(action.getOrigin()));
            assertThat(run.steps().get(0).extractedValues()).containsEntry("orderId", "O-1");
            String frozen = json.write(run.steps().get(0).processingSnapshot());
            assertThat(frozen).contains("/data/encryptdata").doesNotContain("PRIVATE KEY", "Passphrase");
            processing.save(projectId, operation.id(), new OperationProcessingService.SaveRequest(settings.revision(), configuration("/changed")));
            assertThat(json.write(runs.getRun(run.id()).steps().get(0).processingSnapshot())).isEqualTo(frozen);
            assertThat(json.write(runs.getRun(run.id()).steps().get(0).request().getBody())).contains("encryptdata").doesNotContain("changed");
        } finally { server.stop(0); }
    }

    private OperationProcessingSpec configuration(String pointer) {
        OperationProcessingSpec configuration = new OperationProcessingSpec();
        configuration.getRequest().setProtection("pgp-encrypt"); configuration.getRequest().getCiphertext().setPointer(pointer);
        configuration.setResponses(Map.of("200", new OperationProcessingSpec.MessageRule())); return configuration;
    }

    private ApiSpecService.ApiSpecRecord importSpec(String extension) {
        String content = """
                openapi: 3.0.3
                info: {title: Orders, version: '1'}
                paths:
                  /orders:
                    post:
                      operationId: createOrder
                      responses:
                        '200': {description: Success}
                """ + extension;
        return specs.importSpec(projectId, new MockMultipartFile("file", "orders.yaml", "application/yaml", content.getBytes(StandardCharsets.UTF_8)));
    }

    private String extension(String field) {
        return """
                      x-platform-processing:
                        version: 1
                        request:
                          protection: pgp-sign-encrypt
                          ciphertext: {location: json-field, field: %s}
                        responses:
                          '200':
                            protection: pgp-decrypt-verify
                            ciphertext: {location: json-pointer, pointer: /response/resultData}
                """.formatted(field);
    }
}
