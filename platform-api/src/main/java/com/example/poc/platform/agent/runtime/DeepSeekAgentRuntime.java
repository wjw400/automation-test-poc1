package com.example.poc.platform.agent.runtime;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.agent.capability.AgentTool;
import com.example.poc.platform.persistence.JsonCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

@Component
public class DeepSeekAgentRuntime implements AgentRuntime {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekAgentRuntime.class);
    private final AgentProperties properties;
    private final JsonCodec jsonCodec;
    private final AgentContextRedactor redactor;
    private final Map<String, Disposable> active = new ConcurrentHashMap<>();

    public DeepSeekAgentRuntime(
            AgentProperties properties,
            JsonCodec jsonCodec,
            AgentContextRedactor redactor) {
        this.properties = properties;
        this.jsonCodec = jsonCodec;
        this.redactor = redactor;
    }

    @Override
    public String type() { return "DEEPSEEK"; }

    @Override
    public AgentRuntimeCapabilities capabilities() {
        List<String> models = properties.getDeepseek().getModels().isEmpty()
                ? List.of(properties.getDeepseek().getModel())
                : properties.getDeepseek().getModels();
        return new AgentRuntimeCapabilities(
                properties.getDeepseek().isConfigured(),
                true,
                true,
                true,
                properties.getDeepseek().getModel(),
                models);
    }

    @Override
    public CompletableFuture<AgentTurnResult> startTurn(
            AgentTurnRequest request,
            Consumer<AgentRuntimeEvent> eventSink) {
        if (!properties.getDeepseek().isConfigured()) {
            return CompletableFuture.failedFuture(new AgentRuntimeException(
                    "DEEPSEEK_NOT_CONFIGURED",
                    "DeepSeek API key is not configured",
                    false));
        }

        CompletableFuture<AgentTurnResult> result = new CompletableFuture<>();
        StringBuilder content = new StringBuilder();
        long[] usage = new long[2];
        long startedNanos = System.nanoTime();
        String modelId = modelId(request);
        AgentToolCallBudget toolBudget = new AgentToolCallBudget(properties, jsonCodec);
        log.info(
                "Agent model turn started taskId={} turnId={} runtime={} model={} messages={} tools={} tokenBudget={} timeoutMs={}",
                request.taskId(),
                request.turnId(),
                type(),
                modelId,
                request.messages().size(),
                request.tools().size(),
                request.tokenBudget(),
                properties.getModelRequestTimeout().toMillis());
        ChatClient chatClient = ChatClient.builder(createModel(request)).build();
        var prompt = chatClient.prompt(new Prompt(toMessages(request)));
        if (!request.tools().isEmpty()) {
            prompt.tools((Object[]) request.tools().stream()
                    .map(tool -> callback(tool, request, eventSink, toolBudget))
                    .toArray(ToolCallback[]::new));
        }
        Disposable subscription = prompt
                .stream()
                .chatResponse()
                .subscribe(
                        response -> consume(response, content, usage, eventSink),
                        error -> {
                            AgentRuntimeException classified = classify(error);
                            log.error(
                                    "Agent model turn failed taskId={} turnId={} runtime={} model={} code={} retryable={} elapsedMs={} diagnostic={}",
                                    request.taskId(),
                                    request.turnId(),
                                    type(),
                                    modelId,
                                    classified.getCode(),
                                    classified.isRetryable(),
                                    elapsedMillis(startedNanos),
                                    safeDiagnostic(error));
                            result.completeExceptionally(classified);
                        },
                        () -> {
                            log.info(
                                    "Agent model turn completed taskId={} turnId={} runtime={} model={} elapsedMs={} promptTokens={} completionTokens={} responseCharacters={}",
                                    request.taskId(),
                                    request.turnId(),
                                    type(),
                                    modelId,
                                    elapsedMillis(startedNanos),
                                    usage[0],
                                    usage[1],
                                    content.length());
                            result.complete(new AgentTurnResult(
                                    content.toString(), usage[0], usage[1], List.of()));
                        });
        active.put(request.taskId(), subscription);
        result.whenComplete((ignored, error) -> active.remove(request.taskId(), subscription));
        return result;
    }

    private OpenAiChatModel createModel(AgentTurnRequest request) {
        int tokenBudget = (int) Math.min(Integer.MAX_VALUE, Math.max(1, request.tokenBudget()));
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(properties.getDeepseek().getBaseUrl())
                .apiKey(properties.getDeepseek().getApiKey())
                .model(modelId(request))
                .temperature(properties.getDeepseek().getTemperature())
                .maxTokens(tokenBudget)
                .maxRetries(0)
                .timeout(properties.getModelRequestTimeout())
                .build();
        return OpenAiChatModel.builder().options(options).build();
    }

    private List<Message> toMessages(AgentTurnRequest request) {
        List<Message> messages = new ArrayList<>();
        String system = request.systemContext().isBlank()
                ? AgentRuntimePrompt.SYSTEM_PROMPT
                : AgentRuntimePrompt.SYSTEM_PROMPT + "\n\n" + request.systemContext();
        messages.add(new SystemMessage(system));
        for (AgentConversationMessage item : request.messages()) {
            switch (item.role()) {
                case "USER" -> messages.add(new UserMessage(item.text()));
                case "ASSISTANT" -> messages.add(new AssistantMessage(item.text()));
                case "SYSTEM" -> messages.add(new SystemMessage(item.text()));
                default -> { }
            }
        }
        return messages;
    }

    ToolCallback callback(
            AgentTool tool,
            AgentTurnRequest request,
            Consumer<AgentRuntimeEvent> eventSink,
            AgentToolCallBudget budget) {
        ToolDefinition definition = DefaultToolDefinition.builder()
                .name(tool.definition().name())
                .description(tool.definition().description())
                .inputSchema(tool.definition().inputSchema())
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() { return definition; }

            @Override
            public String call(String arguments) {
                return new AgentToolExecution(jsonCodec, redactor).execute(tool, request, eventSink, budget, arguments);
            }
        };
    }

    private void consume(
            ChatResponse response,
            StringBuilder content,
            long[] tokenUsage,
            Consumer<AgentRuntimeEvent> eventSink) {
        if (response.getResult() != null && response.getResult().getOutput() != null) {
            String delta = response.getResult().getOutput().getText();
            if (delta != null && !delta.isEmpty()) {
                content.append(delta);
                eventSink.accept(new AgentRuntimeEvent("message.delta", Map.of("delta", delta)));
            }
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null) {
            tokenUsage[0] = value(usage.getPromptTokens());
            tokenUsage[1] = value(usage.getCompletionTokens());
        }
    }

    private long value(Integer value) { return value == null ? 0 : value.longValue(); }

    AgentRuntimeException classify(Throwable error) {
        CancellationException cancellation = findCause(error, CancellationException.class);
        if (cancellation != null) {
            return new AgentRuntimeException("TURN_CANCELLED", "Agent turn was cancelled", false, error);
        }
        AgentRuntimeException runtimeException = findCause(error, AgentRuntimeException.class);
        if (runtimeException != null) return runtimeException;
        String detail = searchableDetail(error);
        if (detail.contains("429")) return providerError("MODEL_RATE_LIMITED", true, error);
        if (detail.contains("503") || detail.contains("500")) return providerError("MODEL_UNAVAILABLE", true, error);
        if (detail.contains("401")) return providerError("MODEL_AUTHENTICATION_FAILED", false, error);
        if (detail.contains("403")) return providerError("MODEL_ACCESS_DENIED", false, error);
        if (detail.contains("402")) return providerError("MODEL_BALANCE_EXHAUSTED", false, error);
        if (detail.contains("timeout") || detail.contains("timed out")) {
            return providerError("MODEL_TIMEOUT", true, error);
        }
        if (detail.contains("connection") || detail.contains("unreachable")) {
            return providerError("MODEL_UNAVAILABLE", true, error);
        }
        return providerError("MODEL_PROVIDER_ERROR", false, error);
    }

    private AgentRuntimeException providerError(String code, boolean retryable, Throwable cause) {
        return new AgentRuntimeException(code, "DeepSeek request failed", retryable, cause);
    }

    private String modelId(AgentTurnRequest request) {
        return request.modelId() == null || request.modelId().isBlank()
                ? properties.getDeepseek().getModel()
                : request.modelId();
    }

    private String searchableDetail(Throwable error) {
        StringBuilder detail = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            detail.append(current.getClass().getName()).append(' ')
                    .append(String.valueOf(current.getMessage())).append(' ');
            current = current.getCause();
        }
        return detail.toString().toLowerCase(Locale.ROOT);
    }

    private String safeDiagnostic(Throwable error) {
        StringBuilder diagnostic = new StringBuilder();
        Throwable current = error;
        int causes = 0;
        int frames = 0;
        while (current != null && causes++ < 6 && frames < 40) {
            if (!diagnostic.isEmpty()) diagnostic.append(" <- ");
            diagnostic.append(current.getClass().getName()).append(": ")
                    .append(safeDiagnosticMessage(current.getMessage()));
            for (StackTraceElement frame : current.getStackTrace()) {
                if (frames++ >= 40) break;
                diagnostic.append("\n  at ").append(frame);
            }
            current = current.getCause();
        }
        return diagnostic.toString();
    }

    private String safeDiagnosticMessage(String message) {
        if (message == null || message.isBlank()) return "<no message>";
        String safe = redactor.redactText(message);
        return safe.length() <= 2000 ? safe : safe.substring(0, 2000);
    }

    private <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 12) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getCause();
        }
        return null;
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    @Override
    public boolean cancel(String taskId) {
        Disposable subscription = active.remove(taskId);
        if (subscription == null) return false;
        subscription.dispose();
        return true;
    }
}
