package com.example.poc.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.poc.engine.report.ApiCaseExecutionReport;
import com.example.poc.pgp.PgpCryptoService;
import com.example.poc.pgp.PgpProfileMaterial;
import com.sun.net.httpserver.HttpServer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

class OperationProcessingTest {
    private final ObjectMapper json = JsonMapper.builder().build();
    private final ObjectMapper yaml = YAMLMapper.builder().build();
    private final PgpCryptoService pgp = new PgpCryptoService();

    @Test void signsEncryptsAndDecodesNestedFieldsInVisibleActionOrder() throws Exception { roundTrip(true, "/data/encryptdata"); }
    @Test void supportsUnsignedEncryptionWithoutRequiringSigningKeys() throws Exception { roundTrip(false, "/payload"); }

    private void roundTrip(boolean signed, String requestPointer) throws Exception {
        AtomicReference<Object> received = new AtomicReference<>();
        AtomicReference<Object> envelope = new AtomicReference<>();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/secure", exchange -> {
            try {
                Object wire = json.readValue(exchange.getRequestBody(), Object.class);
                envelope.set(wire);
                String cipher = (String) JsonBodyPointer.read(wire, requestPointer);
                received.set(json.readValue(signed ? pgp.decryptAndVerify(cipher, serverProfile()) : pgp.decrypt(cipher, serverProfile()), Object.class));
                String text = json.writeValueAsString(Map.of("orderId", "ORDER-7", "status", "CREATED"));
                String encrypted = signed ? pgp.signAndEncrypt(text, serverProfile()) : pgp.encrypt(text, serverProfile());
                byte[] response = json.writeValueAsBytes(Map.of("response", Map.of("resultData", encrypted)));
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally { exchange.close(); }
        });
        server.start();
        try {
            ApiTestCase testCase = testCase(server.getAddress().getPort());
            if (!signed) {
                testCase.getProfiles().get("client").setSigningPrivateKey(null);
                testCase.getProfiles().get("client").setVerificationPublicKey(null);
            }
            ApiStep step = testCase.getSteps().get(0);
            OperationProcessingSpec configuration = configuration(signed, requestPointer);
            configuration.getRequest().setEnvelope(Map.of("channel", "${channel}"));
            step.setProcessing(configuration);
            step.setProcessingSnapshot(Map.of("revision", 3, "configuration", configuration));
            CaseActionSpec before = new CaseActionSpec();
            before.setType(CaseActionExecutor.BUILD_JSON);
            before.setTemplate(Map.of("orderId", "${orderId}"));
            before.getOutput().setTarget("request.body");
            step.getActions().getBeforeSend().add(before);
            CaseActionSpec wireAction = new CaseActionSpec();
            wireAction.setType(CaseActionExecutor.ENCODE_BASE64);
            wireAction.getInput().setSource("request.body");
            wireAction.getOutput().setTarget("artifacts.wireCopy");
            step.getActions().getAfterRequestProcessing().add(wireAction);
            ValidationSpec assertion = new ValidationSpec();
            assertion.setSource("business.response.body"); assertion.setPath("$.status"); assertion.setExpected("CREATED");
            step.getResponse().setAssertions(List.of(assertion));
            step.getResponse().setExtract(Map.of("savedOrder", Map.of("source", "business.response.body", "path", "$.orderId")));

            ApiCaseExecutionReport report = new ApiCaseRunner().executeInline("operation.yaml", yaml.writeValueAsString(testCase));
            assertThat(report.getStatus()).as("Execution error: %s", report.getErrorMessage()).isEqualTo("PASSED");
            assertThat(received.get()).isEqualTo(Map.of("orderId", "ORDER-7"));
            assertThat(((Map<?, ?>) envelope.get()).get("channel")).isEqualTo("test");
            var evidence = report.getSteps().get(0);
            assertThat(evidence.getActions()).extracting(action -> action.getStage()).containsExactly("beforeSend", "requestProcessing", "requestProcessing", "afterRequestProcessing", "responseProcessing");
            assertThat(evidence.getActions().get(1).getOrigin()).isEqualTo("API");
            assertThat(evidence.getBusinessRequest()).isEqualTo(Map.of("orderId", "ORDER-7"));
            assertThat(evidence.getExtractedValues()).containsEntry("savedOrder", "ORDER-7");
            assertThat(evidence.getProcessingSnapshot().get("revision")).isEqualTo(3);
        } finally { server.stop(0); }
    }

    @Test void requiresValidSignatureAndNeverFallsBackToPlaintext() throws Exception {
        runResponseFailure(Map.of("response", Map.of("resultData", pgp.encrypt("{\"status\":\"CREATED\"}", serverProfile()))), "verifiable signature");
        runResponseFailure(Map.of("differentField", "ciphertext"), "pointer does not exist");
    }

    private void runResponseFailure(Object body, String message) throws Exception {
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/secure", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = json.writeValueAsBytes(body);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            ApiTestCase testCase = testCase(server.getAddress().getPort());
            ApiStep step = testCase.getSteps().get(0);
            OperationProcessingSpec spec = configuration(true, "/data/encryptdata");
            spec.getRequest().setProtection("none");
            step.setProcessing(spec);
            var report = new ApiCaseRunner().executeInline("failure.yaml", yaml.writeValueAsString(testCase));
            assertThat(report.getStatus()).isEqualTo("FAILED");
            assertThat(report.getErrorMessage()).contains(message);
            assertThat(report.getSteps().get(0).getResponse()).isNotNull();
            assertThat(report.getSteps().get(0).getDecryptedPayloads()).isEmpty();
            assertThat(report.getSteps().get(0).getActions().get(0).getStatus()).isEqualTo("FAILED");
        } finally { server.stop(0); }
    }

    @Test void wireModeSkipsEncryptionAndHandlesPlaintextErrorResponse() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/secure", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"error\":\"INVALID_CIPHERTEXT\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            ApiTestCase testCase = testCase(server.getAddress().getPort());
            ApiStep step = testCase.getSteps().get(0);
            step.setProcessing(configuration(true, "/payload"));
            step.setProcessingMode("wire");
            step.getRequest().setBody("invalid ciphertext");
            step.getResponse().setStatus(status(400));
            step.getResponse().setExtract(Map.of("error", Map.of("source", "business.response.body", "path", "$.error")));
            var report = new ApiCaseRunner().executeInline("negative.yaml", yaml.writeValueAsString(testCase));
            assertThat(report.getStatus()).as("Execution error: %s", report.getErrorMessage()).isEqualTo("PASSED");
            assertThat(received.get()).isEqualTo("invalid ciphertext");
            assertThat(report.getSteps().get(0).getActions()).isEmpty();
            assertThat(report.getSteps().get(0).getExtractedValues()).containsEntry("error", "INVALID_CIPHERTEXT");
        } finally { server.stop(0); }
    }

    @Test void rejectsDuplicatePgpBeforeSending() {
        ApiTestCase testCase = testCase(1);
        ApiStep step = testCase.getSteps().get(0);
        step.setProcessing(configuration(true, "/payload"));
        CaseActionSpec action = OperationProcessingActions.request(step.getProcessing().getRequest()).get(0);
        step.getActions().getBeforeSend().add(action);
        var report = new ApiCaseRunner().executeInline("duplicate.yaml", yaml.writeValueAsString(testCase));
        assertThat(report.getErrorMessage()).contains("duplicates API PGP");
        assertThat(report.getSteps()).isEmpty();
    }

    @Test void encryptsAndDecryptsWholeHttpBodies() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/secure", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            String plain = pgp.decryptAndVerify(request, serverProfile());
            byte[] response = pgp.signAndEncrypt(plain, serverProfile()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            ApiTestCase testCase = testCase(server.getAddress().getPort());
            OperationProcessingSpec spec = configuration(true, "/unused");
            spec.getRequest().getCiphertext().setLocation("body");
            spec.getResponses().get("200").getCiphertext().setLocation("body");
            testCase.getSteps().get(0).setProcessing(spec);
            var report = new ApiCaseRunner().executeInline("whole-body.yaml", yaml.writeValueAsString(testCase));
            assertThat(report.getStatus()).as("Run error: %s", report.getErrorMessage()).isEqualTo("PASSED");
            assertThat(report.getSteps().get(0).getRequest().getBody()).isInstanceOf(String.class).asString().startsWith("-----BEGIN PGP MESSAGE-----");
            assertThat(report.getSteps().get(0).getDecryptedPayloads()).containsEntry("business", Map.of("orderId", "ORDER-7"));
            assertThat(contentType.get()).startsWith("text/plain");
        } finally { server.stop(0); }
    }

    @Test void rejectsContentTypeConflictAndMatchesResponseRulesByActualStatus() {
        ApiTestCase testCase = testCase(1);
        testCase.getSteps().get(0).setProcessing(configuration(true, "/payload"));
        testCase.getSteps().get(0).getRequest().setHeaders(Map.of("Content-Type", "text/plain"));
        var report = new ApiCaseRunner().executeInline("content-type.yaml", yaml.writeValueAsString(testCase));
        assertThat(report.getErrorMessage()).contains("Content-Type conflicts");
        assertThat(report.getSteps().get(0).getRequest()).isNull();
        OperationProcessingSpec spec = configuration(true, "/payload");
        var range = new OperationProcessingSpec.MessageRule();
        var fallback = new OperationProcessingSpec.MessageRule();
        spec.getResponses().put("2XX", range); spec.getResponses().put("default", fallback);
        assertThat(spec.responseRule(200)).isSameAs(spec.getResponses().get("200"));
        assertThat(spec.responseRule(201)).isSameAs(range);
        assertThat(spec.responseRule(503)).isSameAs(fallback);
    }

    @Test void addressesEscapedNestedFieldsAndRejectsInvalidMappings() {
        Object body = JsonBodyPointer.write(new LinkedHashMap<>(), "/data/a~1b/~0key", "cipher");
        assertThat(JsonBodyPointer.read(body, "/data/a~1b/~0key")).isEqualTo("cipher");
        assertThatThrownBy(() -> JsonBodyPointer.read(body, "/unknown")).hasMessageContaining("does not exist");
        assertThatThrownBy(() -> JsonBodyPointer.tokens("/bad~2escape")).hasMessageContaining("Invalid JSON Pointer");
        assertThatThrownBy(() -> JsonBodyPointer.write(new LinkedHashMap<>(Map.of("data", 4)), "/data/cipher", "x")).hasMessageContaining("non-container");
        OperationProcessingSpec config = configuration(true, "/payload");
        config.setVersion(2);
        assertThatThrownBy(config::validate).hasMessageContaining("version");
    }

    private OperationProcessingSpec configuration(boolean signed, String pointer) {
        OperationProcessingSpec spec = new OperationProcessingSpec();
        spec.getRequest().setProtection(signed ? "pgp-sign-encrypt" : "pgp-encrypt");
        spec.getRequest().getCiphertext().setPointer(pointer);
        var response = new OperationProcessingSpec.MessageRule();
        response.setProtection(signed ? "pgp-decrypt-verify" : "pgp-decrypt");
        response.getCiphertext().setPointer("/response/resultData");
        spec.setResponses(new LinkedHashMap<>(Map.of("200", response, "400", new OperationProcessingSpec.MessageRule())));
        return spec;
    }

    private ApiTestCase testCase(int port) {
        ApiTestCase testCase = new ApiTestCase();
        testCase.setDslVersion("v1"); testCase.setName("Operation processing");
        testCase.setVariables(Map.of("baseUrl", "http://127.0.0.1:" + port, "orderId", "ORDER-7", "channel", "test"));
        CryptoProfile profile = new CryptoProfile();
        profile.setVerificationPublicKey("classpath:keys/server-public.asc");
        profile.setSigningPrivateKey("classpath:keys/client-private.asc"); profile.setSigningPrivateKeyPassphrase("demo-client-passphrase");
        profile.setEncryptionPublicKey("classpath:keys/server-public.asc");
        profile.setDecryptionPrivateKey("classpath:keys/client-private.asc"); profile.setDecryptionPrivateKeyPassphrase("demo-client-passphrase");
        testCase.setProfiles(Map.of("client", profile)); testCase.getRuntime().setProfile("client");
        ApiStep step = new ApiStep(); step.setName("Create order");
        step.getOperation().setMethod("POST"); step.getOperation().setPath("/secure");
        step.setRequest(new RequestSpec()); step.getRequest().setBody(Map.of("orderId", "${orderId}"));
        step.setResponse(new ResponseSpec()); step.getResponse().setStatus(status(200));
        testCase.setSteps(List.of(step)); return testCase;
    }

    private PgpProfileMaterial serverProfile() {
        PgpProfileMaterial profile = new PgpProfileMaterial();
        profile.setVerificationPublicKey("classpath:keys/client-public.asc");
        profile.setSigningPrivateKey("classpath:keys/server-private.asc"); profile.setSigningPrivateKeyPassphrase("demo-server-passphrase");
        profile.setEncryptionPublicKey("classpath:keys/client-public.asc");
        profile.setDecryptionPrivateKey("classpath:keys/server-private.asc"); profile.setDecryptionPrivateKeyPassphrase("demo-server-passphrase");
        return profile;
    }

    private HttpStatusSpec status(int expected) {
        HttpStatusSpec rule = new HttpStatusSpec(); rule.setOperator("equals"); rule.setExpected(expected); return rule;
    }
}
