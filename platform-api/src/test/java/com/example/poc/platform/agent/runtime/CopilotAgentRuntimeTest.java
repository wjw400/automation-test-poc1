package com.example.poc.platform.agent.runtime;

import static com.example.poc.platform.agent.runtime.CopilotResponsesClient.object;
import static com.example.poc.platform.agent.runtime.CopilotResponsesClient.objects;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.agent.AgentRuntimeResources;
import com.example.poc.platform.agent.capability.AgentTool;
import com.example.poc.platform.agent.capability.AgentToolDefinition;
import com.example.poc.platform.agent.capability.AgentToolResult;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.persistence.JsonCodec;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CopilotAgentRuntimeTest {
    private final AgentProperties properties = new AgentProperties();
    private final JsonCodec json = new JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService httpExecutor = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<Responder> replies = new LinkedBlockingQueue<>();
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();
    private final List<AgentRuntimeEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;
    private CopilotAgentRuntime runtime;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(httpExecutor);
        server.createContext("/responses", exchange -> {
            requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version"),
                    exchange.getRequestHeaders().getFirst("Copilot-Integration-Id"),
                    exchange.getRequestHeaders().getFirst("X-Custom-Header"),
                    json.readObjectMap(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))));
            try {
                Responder reply = replies.poll(3, TimeUnit.SECONDS);
                if (reply == null) write(exchange, 500, "text/plain", "No scripted response");
                else reply.send(exchange);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // Expected when the runtime cancels a stream or stops at response.completed.
            } finally {
                exchange.close();
            }
        });
        server.start();
        var config = properties.getCopilot();
        config.setEnabled(true);
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setApiToken("rest-test-secret");
        config.setModel("test-responses");
        properties.setModelRequestTimeout(Duration.ofSeconds(5));
        runtime = new CopilotAgentRuntime(properties, new AgentRuntimeResources(executor, scheduler),
                json, new AgentContextRedactor());
    }

    @AfterEach
    void tearDown() throws Exception {
        runtime.shutdown();
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
        scheduler.shutdownNow();
        httpExecutor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(httpExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void requiresEnabledRestCredentialsAndAnExplicitModel() {
        assertThat(runtime.capabilities().available()).isTrue();
        properties.getCopilot().setEnabled(false);
        assertNotConfigured();
        properties.getCopilot().setEnabled(true);
        properties.getCopilot().setApiToken("");
        assertNotConfigured();
        properties.getCopilot().setApiToken("rest-test-secret");
        properties.getCopilot().setModel("auto");
        assertNotConfigured();
        assertThat(requests).isEmpty();
    }

    @Test
    void sendsResponsesHistoryContextAndHeadersAndStreamsTextWithoutDuplication() throws Exception {
        var config = properties.getCopilot();
        config.setBaseUrl(config.getBaseUrl() + "/");
        config.setApiVersion("test-api-version");
        config.setIntegrationId("test-integration");
        config.setHeaders(Map.of("X-Custom-Header", "custom", "Authorization", "must-not-override"));
        config.setModels(List.of("second-model"));
        String stream = ": keepalive\r\n\r\n"
                + event("response.output_text.delta", Map.of("delta", "你", "output_index", 0, "content_index", 0))
                + event("response.output_text.delta", Map.of("delta", "好", "output_index", 0, "content_index", 0))
                + completed(List.of(message("你好")), 10, 2);
        sse(stream);
        AgentTurnResult result = run(request());
        assertThat(result.content()).isEqualTo("你好");
        assertThat(result.promptTokens()).isEqualTo(10);
        assertThat(result.completionTokens()).isEqualTo(2);
        assertThat(events).extracting(event -> event.payload().get("delta")).containsExactly("你", "好");
        CapturedRequest sent = requests.get(0);
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/responses");
        assertThat(sent.authorization()).isEqualTo("Bearer rest-test-secret");
        assertThat(sent.apiVersion()).isEqualTo("test-api-version");
        assertThat(sent.integrationId()).isEqualTo("test-integration");
        assertThat(sent.customHeader()).isEqualTo("custom");
        assertThat(sent.body()).containsEntry("model", "test-responses").containsEntry("stream", true)
                .containsEntry("store", false).containsEntry("max_output_tokens", 100);
        List<Map<String, Object>> input = objects(sent.body().get("input"));
        assertThat(input).extracting(item -> item.get("role")).containsExactly("system", "user", "assistant", "user");
        assertThat(input.get(0).get("content").toString()).contains("trusted platform context", "Draft");
        assertThat(input.get(3).get("content")).isEqualTo("latest request");
        assertThat(json.write(sent.body())).doesNotContain("rest-test-secret");
        assertThat(runtime.capabilities().models()).containsExactly("test-responses", "second-model");
    }

    @Test
    void replaysReasoningAndMultipleToolResultsAcrossRoundsWithCumulativeUsage() throws Exception {
        Map<String, Object> reasoning = Map.of("type", "reasoning", "id", "reason-1", "summary", List.of(),
                "encrypted_content", "opaque-reasoning");
        sse(completed(List.of(reasoning, call("c1", "getScenario", "{\"name\":\"Order\"}"),
                call("c2", "getScenario", "{\"name\":\"Lookup\"}")), 10, 7));
        sse(completed(List.of(message("Draft inspected")), 20, 4));
        AgentTurnResult result = run(withTool());
        assertThat(result.content()).isEqualTo("Draft inspected");
        assertThat(result.promptTokens()).isEqualTo(30);
        assertThat(result.completionTokens()).isEqualTo(11);
        assertThat(toolCalls).hasValue(2);
        assertThat(events).extracting(AgentRuntimeEvent::type).containsSubsequence(
                "tool.started", "artifact.output", "tool.completed", "tool.started", "artifact.output", "tool.completed", "message.delta");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).body()).containsEntry("max_output_tokens", 93);
        List<Map<String, Object>> history = objects(requests.get(1).body().get("input"));
        assertThat(history.get(4)).isEqualTo(reasoning);
        assertThat(history.get(7)).containsEntry("type", "function_call_output").containsEntry("call_id", "c1");
        assertThat(history.get(7).get("output").toString()).contains("Order", "\"ok\":true");
        assertThat(history.get(8)).containsEntry("call_id", "c2");
        Map<String, Object> tool = objects(requests.get(0).body().get("tools")).get(0);
        assertThat(tool).containsEntry("type", "function").containsEntry("name", "getScenario").containsEntry("strict", false);
        assertThat(tool).doesNotContainKey("function");
        assertThat(object(tool.get("parameters"))).containsEntry("type", "object");
    }

    @Test
    void returnsMalformedToolArgumentsToTheModelForRepair() throws Exception {
        sse(completed(List.of(call("bad", "getScenario", "{\"name\":")), 1, 1));
        sse(completed(List.of(call("fixed", "getScenario", "{\"name\":\"Order\"}")), 1, 1));
        sse(completed(List.of(message("Fixed")), 1, 1));
        assertThat(run(withTool()).content()).isEqualTo("Fixed");
        assertThat(toolCalls).hasValue(1);
        assertThat(json.write(requests.get(1).body().get("input"))).contains("TOOL_INPUT_INVALID");
        assertThat(events).extracting(AgentRuntimeEvent::type).contains("tool.failed", "tool.completed");
    }

    @Test
    void rejectsAnUnregisteredToolBeforeExecutingAnyCallInTheBatch() {
        sse(completed(List.of(call("allowed", "getScenario", "{}"), call("denied", "shell", "{}")), 1, 1));
        assertFailure(withTool(), "TOOL_PERMISSION_DENIED");
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void rejectsDuplicateToolCallIdentifiersBeforeExecutingTools() {
        sse(completed(List.of(call("same", "getScenario", "{}"), call("same", "getScenario", "{}")), 1, 1));
        assertFailure(withTool(), "MODEL_PROVIDER_ERROR");
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void stopsAtThePlatformToolBudget() {
        properties.setMaxToolCalls(1);
        sse(completed(List.of(call("first", "getScenario", "{}")), 1, 1));
        sse(completed(List.of(call("second", "getScenario", "{\"name\":\"new\"}")), 1, 1));
        assertFailure(withTool(), "TOOL_CALL_BUDGET_EXCEEDED");
        assertThat(toolCalls).hasValue(1);
        assertThat(requests).hasSize(2);
    }

    @Test
    void stopsRepeatedToolCallsWithoutFurtherRequests() {
        properties.setMaxRepeatedToolCalls(1);
        sse(completed(List.of(call("first", "getScenario", "{}")), 1, 1));
        sse(completed(List.of(call("second", "getScenario", "{}")), 1, 1));
        assertFailure(withTool(), "REPEATED_TOOL_CALL");
        assertThat(toolCalls).hasValue(1);
    }

    @Test
    void enforcesTokenBudgetBeforeExecutingTools() {
        sse(completed(List.of(call("c1", "getScenario", "{}")), 1, 101));
        assertFailure(withTool(), "TOKEN_BUDGET_EXCEEDED");
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void capsEachRequestAndHandlesNonStreamingJsonResponses() throws Exception {
        properties.getCopilot().setMaxOutputTokens(32);
        properties.getCopilot().setBaseUrl(properties.getCopilot().getBaseUrl() + "/responses");
        reply(200, "application/json", json.write(response(List.of(message("JSON fallback")), 3, 2)));
        assertThat(run(request()).content()).isEqualTo("JSON fallback");
        assertThat(requests.get(0).body()).containsEntry("max_output_tokens", 32);
    }

    @ParameterizedTest
    @CsvSource({"401,MODEL_AUTHENTICATION_FAILED,false", "402,MODEL_BALANCE_EXHAUSTED,false",
            "403,MODEL_ACCESS_DENIED,false", "429,MODEL_RATE_LIMITED,true", "500,MODEL_UNAVAILABLE,true",
            "503,MODEL_UNAVAILABLE,true", "504,MODEL_TIMEOUT,true", "400,MODEL_PROVIDER_ERROR,false"})
    void classifiesHttpErrorsWithoutExposingProviderBodies(int status, String code, boolean retryable) throws Exception {
        reply(status, "application/json", "{\"message\":\"Authorization: Bearer rest-test-secret\"}");
        AgentRuntimeException error = failure(request());
        assertThat(error.getCode()).isEqualTo(code);
        assertThat(error.isRetryable()).isEqualTo(retryable);
        assertThat(error.getMessage()).doesNotContain("rest-test-secret");
        assertThat(error.getCause()).isNull();
        assertThat(events).isEmpty();
        assertThat(requests).hasSize(1);
    }

    @Test
    void classifiesStreamErrorsWithoutExposingMessages() {
        sse(event("response.failed", Map.of("response", Map.of("error", Map.of(
                "code", "rate_limit_exceeded", "message", "rest-test-secret")))));
        assertFailure(request(), "MODEL_RATE_LIMITED");
    }

    @Test
    void rejectsTruncatedStreamsWithoutExecutingPartialTools() {
        sse(event("response.output_item.done", Map.of("item", call("partial", "getScenario", "{}")))
                + "data: [DONE]\n\n");
        assertFailure(withTool(), "MODEL_RESPONSE_INCOMPLETE");
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void rejectsExplicitIncompleteResponses() {
        sse(event("response.incomplete", Map.of("response", Map.of("status", "incomplete"))));
        assertFailure(request(), "MODEL_RESPONSE_INCOMPLETE");
    }

    @Test
    void reportsTokenAllowanceReachedWhenProviderStopsAtTheOutputLimit() {
        sse(event("response.incomplete", Map.of("response", Map.of("status", "incomplete",
                "output", List.of(call("partial", "getScenario", "{}")),
                "usage", Map.of("input_tokens", 1, "output_tokens", 100),
                "incomplete_details", Map.of("reason", "max_output_tokens")))));
        assertFailure(withTool(), "TOKEN_BUDGET_EXCEEDED");
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void rejectsIncompleteJsonResponsesWithoutExecutingTools() {
        reply(200, "application/json", json.write(Map.of("status", "incomplete",
                "output", List.of(call("partial", "getScenario", "{}")),
                "usage", Map.of("input_tokens", 1, "output_tokens", 10))));
        assertFailure(withTool(), "MODEL_RESPONSE_INCOMPLETE");
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void cancelsAnActiveHttpStreamAndRejectsDuplicateTurns() throws Exception {
        CountDownLatch connected = holdStream();
        CompletableFuture<AgentTurnResult> result = runtime.startTurn(request(), events::add);
        assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();
        assertFailure(request(), "TASK_ALREADY_RUNNING");
        assertThat(runtime.cancel("task-1")).isTrue();
        assertThat(result.isCancelled()).isTrue();
        assertThat(runtime.cancel("task-1")).isFalse();
        // Cancellation must free the worker even while the remote server holds the connection.
        sse(completed(List.of(message("Next turn")), 1, 1));
        assertThat(run(request()).content()).isEqualTo("Next turn");
    }

    @Test
    void timesOutAnOpenStream() throws Exception {
        properties.setModelRequestTimeout(Duration.ofMillis(150));
        holdStream();
        assertFailure(request(), "MODEL_TIMEOUT");
    }

    @Test
    void finishesAtTheCompletionEventWithoutWaitingForConnectionClosure() throws Exception {
        replies.add(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(completed(List.of(message("Done")), 1, 1).getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            release.await(5, TimeUnit.SECONDS);
        });
        assertThat(run(request()).content()).isEqualTo("Done");
        assertThat(release.getCount()).isEqualTo(1);
    }

    @Test
    void shutdownCancelsActiveRequests() throws Exception {
        CountDownLatch connected = holdStream();
        var result = runtime.startTurn(request(), events::add);
        assertThat(connected.await(5, TimeUnit.SECONDS)).isTrue();
        runtime.shutdown();
        assertThat(result.isCancelled()).isTrue();
    }

    private void assertNotConfigured() {
        assertThat(runtime.capabilities().available()).isFalse();
        assertFailure(request(), "COPILOT_NOT_CONFIGURED");
    }

    private AgentTurnResult run(AgentTurnRequest request) throws Exception {
        return runtime.startTurn(request, events::add).get(5, TimeUnit.SECONDS);
    }

    private AgentRuntimeException failure(AgentTurnRequest request) throws Exception {
        try {
            run(request);
            throw new AssertionError("Expected a failed turn");
        } catch (ExecutionException error) {
            assertThat(error.getCause()).isInstanceOf(AgentRuntimeException.class);
            return (AgentRuntimeException) error.getCause();
        }
    }

    private void assertFailure(AgentTurnRequest request, String code) {
        assertThatThrownBy(() -> run(request)).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOfSatisfying(AgentRuntimeException.class,
                        error -> assertThat(error.getCode()).isEqualTo(code));
    }

    private CountDownLatch holdStream() {
        CountDownLatch connected = new CountDownLatch(1);
        replies.add(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            connected.countDown();
            release.await(5, TimeUnit.SECONDS);
        });
        return connected;
    }

    private AgentTurnRequest request() {
        return new AgentTurnRequest("task-1", "turn-1", null, 100,
                List.of(new AgentConversationMessage("USER", "previous request"),
                        new AgentConversationMessage("ASSISTANT", "previous reply"),
                        new AgentConversationMessage("USER", "latest request")),
                "trusted platform context", List.of(), List.of());
    }

    private AgentTurnRequest withTool() {
        AgentTool tool = new AgentTool() {
            @Override public AgentToolDefinition definition() {
                return new AgentToolDefinition("getScenario", "Inspect a scenario",
                        "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}",
                        "{}", "READ", "NONE", 1_000, 10_000);
            }
            @Override public AgentToolResult execute(Map<String, Object> input) {
                toolCalls.incrementAndGet();
                return new AgentToolResult(Map.of("name", input.getOrDefault("name", "Order")), List.of(new AgentArtifactOutput(
                        "RUNTIME_TRACE", null, null, null, "READY", Map.of("runtime", "COPILOT"))));
            }
        };
        return request().withCapabilities("trusted platform context", List.of("scenario-authoring"), List.of(tool));
    }

    private Map<String, Object> message(String text) {
        return Map.of("id", "message-1", "type", "message", "role", "assistant", "status", "completed",
                "content", List.of(Map.of("type", "output_text", "text", text, "annotations", List.of())));
    }

    private Map<String, Object> call(String id, String name, String arguments) {
        return Map.of("type", "function_call", "id", "item-" + id, "call_id", id, "name", name, "arguments", arguments);
    }

    private Map<String, Object> response(List<Map<String, Object>> output, long inputTokens, long outputTokens) {
        return Map.of("id", "resp-1", "status", "completed", "output", output,
                "usage", Map.of("input_tokens", inputTokens, "output_tokens", outputTokens));
    }

    private String completed(List<Map<String, Object>> output, long inputTokens, long outputTokens) {
        return event("response.completed", Map.of("response", response(output, inputTokens, outputTokens)));
    }

    private String event(String type, Map<String, Object> body) {
        // Exercise SSE event-name fallback as well as normal JSON event types.
        return "event: " + type + "\ndata: " + json.write(body) + "\n\n";
    }

    private void sse(String body) { reply(200, "text/event-stream", body); }

    private void reply(int status, String contentType, String body) {
        replies.add(exchange -> write(exchange, status, contentType, body));
    }

    private void write(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface Responder { void send(HttpExchange exchange) throws IOException, InterruptedException; }

    private record CapturedRequest(String method, String path, String authorization, String apiVersion,
            String integrationId, String customHeader, Map<String, Object> body) {}
}
