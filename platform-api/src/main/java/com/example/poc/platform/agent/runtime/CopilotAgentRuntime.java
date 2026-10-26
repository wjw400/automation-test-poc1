package com.example.poc.platform.agent.runtime;

import static com.example.poc.platform.agent.runtime.CopilotResponsesClient.text;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.agent.AgentRuntimeResources;
import com.example.poc.platform.agent.capability.AgentTool;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.persistence.JsonCodec;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Direct Responses adapter. The platform owns history, tool execution, permissions and budgets. */
@Component
public class CopilotAgentRuntime implements AgentRuntime {
    private static final Logger log = LoggerFactory.getLogger(CopilotAgentRuntime.class);
    private final AgentProperties properties;
    private final AgentRuntimeResources resources;
    private final JsonCodec json;
    private final AgentToolExecution toolExecution;
    private final CopilotResponsesClient client;
    private final Map<String, Turn> active = new ConcurrentHashMap<>();

    public CopilotAgentRuntime(AgentProperties properties, AgentRuntimeResources resources,
            JsonCodec json, AgentContextRedactor redactor) {
        this.properties = properties;
        this.resources = resources;
        this.json = json;
        this.toolExecution = new AgentToolExecution(json, redactor);
        this.client = new CopilotResponsesClient(properties, json);
    }

    @Override
    public String type() { return "COPILOT"; }

    @Override
    public AgentRuntimeCapabilities capabilities() {
        var config = properties.getCopilot();
        Set<String> models = new LinkedHashSet<>();
        if (config.getModel() != null && !config.getModel().isBlank()) models.add(config.getModel());
        config.getModels().stream().filter(model -> !model.isBlank()).forEach(models::add);
        return new AgentRuntimeCapabilities(config.isConfigured(), true, false, true,
                config.getModel(), List.copyOf(models));
    }

    @Override
    public CompletableFuture<AgentTurnResult> startTurn(AgentTurnRequest request,
            Consumer<AgentRuntimeEvent> eventSink) {
        if (!properties.getCopilot().isConfigured()) {
            return CompletableFuture.failedFuture(new AgentRuntimeException("COPILOT_NOT_CONFIGURED",
                    "Configure COPILOT_ENABLED, COPILOT_API_TOKEN and an explicit COPILOT_MODEL supporting /responses", false));
        }
        Turn turn = new Turn();
        turn.worker = new FutureTask<>(() -> { execute(request, eventSink, turn); return null; });
        if (active.putIfAbsent(request.taskId(), turn) != null) {
            return CompletableFuture.failedFuture(new AgentRuntimeException(
                    "TASK_ALREADY_RUNNING", "A Copilot turn is already running for this task", false));
        }
        turn.result.whenComplete((ignored, error) -> {
            if (turn.result.isCancelled()) turn.worker.cancel(true);
            active.remove(request.taskId(), turn);
        });
        try {
            resources.executor().execute(turn.worker);
        } catch (RuntimeException error) {
            turn.result.completeExceptionally(classify(error));
        }
        return turn.result;
    }

    private void execute(AgentTurnRequest request, Consumer<AgentRuntimeEvent> sink, Turn turn) {
        long started = System.nanoTime();
        log.info("Agent model turn started taskId={} turnId={} runtime={} model={}",
                request.taskId(), request.turnId(), type(), request.modelId());
        try {
            List<Map<String, Object>> input = messages(request);
            AgentToolCallBudget budget = new AgentToolCallBudget(properties, json);
            while (true) {
                turn.checkRunning();
                long remaining = request.tokenBudget() - turn.outputTokens;
                if (remaining <= 0) throw tokenBudgetExceeded();
                var response = client.request(requestBody(request, input, remaining), delta -> {
                    turn.checkRunning();
                    turn.content.append(delta);
                    sink.accept(new AgentRuntimeEvent("message.delta", Map.of("delta", delta)));
                }, turn::checkRunning);
                turn.inputTokens += response.inputTokens();
                turn.outputTokens += response.outputTokens();
                if (turn.outputTokens > request.tokenBudget()) throw tokenBudgetExceeded();
                if (response.incomplete()) {
                    if (turn.outputTokens >= request.tokenBudget()) throw tokenBudgetExceeded();
                    throw new AgentRuntimeException("MODEL_RESPONSE_INCOMPLETE",
                            "Copilot stopped before completing its response; check the per-request output limit", false);
                }
                List<Map<String, Object>> calls = response.output().stream()
                        .filter(item -> "function_call".equals(text(item, "type"))).toList();
                if (calls.isEmpty()) {
                    turn.checkRunning();
                    turn.result.complete(new AgentTurnResult(turn.content.toString(),
                            turn.inputTokens, turn.outputTokens, List.of()));
                    return;
                }
                if (turn.outputTokens >= request.tokenBudget()) throw tokenBudgetExceeded();
                validateCalls(calls, request);
                // Stateless continuation replays reasoning and function calls as well as messages.
                input.addAll(response.output());
                for (Map<String, Object> call : calls) {
                    turn.checkRunning();
                    AgentTool tool = request.tools().stream()
                            .filter(candidate -> candidate.definition().name().equals(text(call, "name")))
                            .findFirst().orElseThrow();
                    String output = toolExecution.execute(tool, request, sink, budget, text(call, "arguments"));
                    turn.checkRunning();
                    input.add(Map.of("type", "function_call_output", "call_id", text(call, "call_id"), "output", output));
                }
            }
        } catch (Throwable error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            AgentRuntimeException classified = classify(error);
            log.warn("Agent model turn failed taskId={} turnId={} runtime={} code={}",
                    request.taskId(), request.turnId(), type(), classified.getCode());
            turn.result.completeExceptionally(classified);
        } finally {
            log.info("Agent model turn stopped taskId={} turnId={} runtime={} elapsedMs={}",
                    request.taskId(), request.turnId(), type(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        }
    }

    private List<Map<String, Object>> messages(AgentTurnRequest request) {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(Map.of("role", "system", "content", AgentRuntimePrompt.SYSTEM_PROMPT
                + (request.systemContext().isBlank() ? "" : "\n\n" + request.systemContext())));
        for (AgentConversationMessage message : request.messages()) {
            String role = message.role().toLowerCase(Locale.ROOT);
            if (Set.of("user", "assistant", "system").contains(role)) {
                input.add(Map.of("role", role, "content", message.text()));
            }
        }
        return input;
    }

    private Map<String, Object> requestBody(AgentTurnRequest request, List<Map<String, Object>> input, long remaining) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.modelId() == null || request.modelId().isBlank()
                ? properties.getCopilot().getModel() : request.modelId());
        body.put("input", input);
        body.put("stream", true);
        body.put("store", false);
        body.put("max_output_tokens", Math.min(properties.getCopilot().getMaxOutputTokens(), remaining));
        body.put("include", List.of("reasoning.encrypted_content"));
        if (!request.tools().isEmpty()) {
            body.put("tools", request.tools().stream().map(tool -> Map.of(
                    "type", "function", "name", tool.definition().name(),
                    "description", tool.definition().description(),
                    "parameters", json.readObjectMap(tool.definition().inputSchema()), "strict", false)).toList());
        }
        return body;
    }

    private void validateCalls(List<Map<String, Object>> calls, AgentTurnRequest request) {
        Set<String> ids = new LinkedHashSet<>();
        for (Map<String, Object> call : calls) {
            if (text(call, "call_id").isBlank() || !ids.add(text(call, "call_id"))) {
                throw new AgentRuntimeException("MODEL_PROVIDER_ERROR", "Copilot returned invalid tool call identifiers", false);
            }
            if (request.tools().stream().noneMatch(tool -> tool.definition().name().equals(text(call, "name")))) {
                throw new AgentRuntimeException("TOOL_PERMISSION_DENIED", "Copilot requested a tool outside the platform allowlist", false);
            }
        }
    }

    private AgentRuntimeException tokenBudgetExceeded() {
        return new AgentRuntimeException("TOKEN_BUDGET_EXCEEDED", "Agent reached its output token allowance", false);
    }

    AgentRuntimeException classify(Throwable error) {
        boolean networkError = false;
        int depth = 0;
        for (Throwable cause = error; cause != null && depth++ < 12; cause = cause.getCause()) {
            if (cause instanceof AgentRuntimeException runtimeError) return runtimeError;
            if (cause instanceof CancellationException || cause instanceof InterruptedException) {
                return new AgentRuntimeException("TURN_CANCELLED", "Copilot turn was cancelled", false);
            }
            if (cause instanceof TimeoutException || cause.getMessage() != null
                    && cause.getMessage().contains("Timeout on blocking read")) {
                return new AgentRuntimeException("MODEL_TIMEOUT", "Copilot Responses request timed out", true);
            }
            if (cause instanceof IOException) networkError = true;
        }
        return new AgentRuntimeException(networkError ? "MODEL_UNAVAILABLE" : "MODEL_PROVIDER_ERROR",
                "Copilot Responses request failed", networkError);
    }

    @Override
    public boolean cancel(String taskId) {
        Turn turn = active.get(taskId);
        return turn != null && turn.result.cancel(true);
    }

    @PreDestroy
    void shutdown() { active.keySet().forEach(this::cancel); }

    private static final class Turn {
        final CompletableFuture<AgentTurnResult> result = new CompletableFuture<>();
        final StringBuilder content = new StringBuilder();
        volatile FutureTask<Void> worker;
        long inputTokens;
        long outputTokens;

        void checkRunning() {
            if (result.isDone() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Copilot turn stopped");
            }
        }
    }
}
