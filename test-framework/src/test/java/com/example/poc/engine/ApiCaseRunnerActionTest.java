package com.example.poc.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.poc.engine.report.ApiCaseExecutionReport;
import com.example.poc.pgp.PgpCryptoService;
import com.example.poc.pgp.PgpProfileMaterial;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ApiCaseRunnerActionTest {
    private final ObjectMapper jsonMapper = JsonMapper.builder().build();
    private final PgpCryptoService pgpCryptoService = new PgpCryptoService();

    @Test
    void executesXmlEncryptionBase64JsonBuildAndResponseDecryptionActions() throws Exception {
        AtomicReference<Map<String, Object>> wireRequest = new AtomicReference<>();
        AtomicReference<String> decryptedRequest = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/secure/xml-envelope", exchange ->
                handleSecureExchange(exchange, wireRequest, decryptedRequest));
        server.start();

        try {
            String caseYaml = actionCaseYaml(server.getAddress().getPort());
            ApiCaseExecutionReport report = new ApiCaseRunner().executeInline("action-pipeline.yaml", caseYaml);

            assertThat(report.getStatus()).isEqualTo("PASSED");
            assertThat(report.getSteps()).hasSize(1);
            assertThat(report.getSteps().get(0).getActions())
                    .extracting(action -> action.getType())
                    .containsExactly(
                            CaseActionExecutor.SIGN_AND_ENCRYPT,
                            CaseActionExecutor.ENCODE_BASE64,
                            CaseActionExecutor.BUILD_JSON,
                            CaseActionExecutor.DECRYPT_AND_VERIFY);
            assertThat(report.getSteps().get(0).getActions())
                    .allSatisfy(action -> assertThat(action.getStatus()).isEqualTo("PASSED"));
            assertThat(wireRequest.get()).containsEntry("meta", Map.of("a", "xxx"));
            assertThat(wireRequest.get().get("encryptBase64")).isInstanceOf(String.class);
            assertThat(decryptedRequest.get()).isEqualTo("<Payment><Amount>100</Amount></Payment>");
            assertThat(report.getSteps().get(0).getDecryptedPayloads().get("securePayload"))
                    .isEqualTo(Map.of("result", "ACCEPTED", "amount", 100));
            assertThat(report.getSteps().get(0).getValidations())
                    .allSatisfy(validation -> assertThat(validation.getStatus()).isEqualTo("PASSED"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolvesBaseUrlFromApiSpecBindingBeforeDefaultBaseUrl() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            byte[] responseBytes = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();

        try {
            String caseYaml = """
                    dslVersion: v1
                    name: API Spec endpoint binding
                    variables:
                      baseUrl: http://127.0.0.1:1
                      customer-api.baseUrl: http://127.0.0.1:%d
                    steps:
                      - name: Check service health
                        type: http
                        operation:
                          apiSpecId: customer-api
                          operationId: getHealth
                          method: GET
                          path: /health
                        request: {}
                        response:
                          status: 200
                          assertions:
                            - source: response.body
                              path: $.status
                              operator: equals
                              expected: UP
                    """.formatted(server.getAddress().getPort());

            ApiCaseExecutionReport report = new ApiCaseRunner().executeInline("endpoint-binding.yaml", caseYaml);

            assertThat(report.getStatus()).isEqualTo("PASSED");
            assertThat(report.getSteps().get(0).getRequest().getUrl())
                    .isEqualTo("http://127.0.0.1:" + server.getAddress().getPort() + "/health");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolvesAndEncodesOperationPathParameters() throws Exception {
        AtomicReference<String> rawPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            rawPath.set(exchange.getRequestURI().getRawPath());
            byte[] responseBytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();

        try {
            String caseYaml = """
                    dslVersion: v1
                    name: Path parameter binding
                    variables:
                      baseUrl: http://127.0.0.1:%d
                      orderId: 42
                    steps:
                      - name: Read customer order
                        type: http
                        operation:
                          operationId: getCustomerOrder
                          method: GET
                          path: /customers/{customer}/orders/{orderId}
                        request:
                          path:
                            customer: A B
                            orderId: ${orderId}
                        response:
                          status: 200
                          assertions: []
                    """.formatted(server.getAddress().getPort());

            ApiCaseExecutionReport report = new ApiCaseRunner().executeInline("path-parameters.yaml", caseYaml);

            assertThat(report.getStatus()).isEqualTo("PASSED");
            assertThat(rawPath.get()).isEqualTo("/customers/A%20B/orders/42");
        } finally {
            server.stop(0);
        }
    }

    private void handleSecureExchange(
            HttpExchange exchange,
            AtomicReference<Map<String, Object>> wireRequest,
            AtomicReference<String> decryptedRequest) throws IOException {
        try {
            Map<String, Object> request = jsonMapper.readValue(
                    exchange.getRequestBody(),
                    new TypeReference<>() {});
            wireRequest.set(request);
            String base64Payload = String.valueOf(request.get("encryptBase64"));
            String armoredPayload = new String(
                    Base64.getDecoder().decode(base64Payload),
                    StandardCharsets.UTF_8);
            decryptedRequest.set(pgpCryptoService.decryptAndVerify(armoredPayload, serverProfile()));

            String responsePayload = pgpCryptoService.signAndEncrypt(
                    jsonMapper.writeValueAsString(Map.of("result", "ACCEPTED", "amount", 100)),
                    serverProfile());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("code", "SUCCESS");
            response.put("payload", responsePayload);
            byte[] responseBytes = jsonMapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        } catch (RuntimeException error) {
            byte[] responseBytes = error.getMessage().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        } finally {
            exchange.close();
        }
    }

    private PgpProfileMaterial serverProfile() {
        PgpProfileMaterial profile = new PgpProfileMaterial();
        profile.setSenderPublicKey(keyMaterial("keys/client-public.asc"));
        profile.setSenderPrivateKey(keyMaterial("keys/server-private.asc"));
        profile.setSenderPrivateKeyPassphrase("demo-server-passphrase");
        profile.setRecipientPublicKey(keyMaterial("keys/client-public.asc"));
        profile.setRecipientPrivateKey(keyMaterial("keys/server-private.asc"));
        profile.setRecipientPrivateKeyPassphrase("demo-server-passphrase");
        return profile;
    }

    private String keyMaterial(String resourcePath) {
        try (var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Test key not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test key: " + resourcePath, e);
        }
    }

    private String actionCaseYaml(int port) {
        return """
                dslVersion: v1
                name: Secure XML envelope action pipeline
                runtime:
                  profile: secureEcho
                variables:
                  baseUrl: http://127.0.0.1:%d
                profiles:
                  secureEcho:
                    senderPublicKey: classpath:keys/server-public.asc
                    senderPrivateKey: classpath:keys/client-private.asc
                    senderPrivateKeyPassphrase: demo-client-passphrase
                    recipientPublicKey: classpath:keys/server-public.asc
                    recipientPrivateKey: classpath:keys/client-private.asc
                    recipientPrivateKeyPassphrase: demo-client-passphrase
                steps:
                  - name: Submit encrypted XML envelope
                    type: http
                    operation:
                      operationId: submitEncryptedXml
                      method: POST
                      path: /secure/xml-envelope
                    request:
                      headers:
                        Content-Type: application/json
                      body: <Payment><Amount>100</Amount></Payment>
                    actions:
                      beforeSend:
                        - type: crypto.pgp.signAndEncrypt
                          name: Sign and encrypt XML
                          input:
                            source: request.body
                            format: xml
                          output:
                            target: artifacts.encryptedPayload
                            format: text
                        - type: data.encodeBase64
                          name: Encode encrypted payload
                          input:
                            source: artifacts.encryptedPayload
                          output:
                            target: artifacts.encryptedPayloadBase64
                        - type: body.buildJson
                          name: Build wire request body
                          template:
                            meta:
                              a: xxx
                            encryptBase64: ${artifacts.encryptedPayloadBase64}
                          output:
                            target: request.body
                      afterReceive:
                        - type: crypto.pgp.decryptAndVerify
                          name: Decrypt secure response payload
                          input:
                            source: response.body
                            path: $.payload
                          output:
                            target: decrypted.securePayload
                            format: json
                    response:
                      status: 200
                      assertions:
                        - source: response.body
                          path: $.code
                          operator: equals
                          expected: SUCCESS
                        - source: decrypted.securePayload
                          path: $.result
                          operator: equals
                          expected: ACCEPTED
                        - source: decrypted.securePayload
                          path: $.amount
                          operator: equals
                          expected: 100
                      extract: {}
                """.formatted(port);
    }
}
