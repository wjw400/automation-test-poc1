package com.example.poc.platform.agent.runtime;

import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.agent.capability.AgentTool;
import com.example.poc.platform.agent.capability.AgentToolResult;
import com.example.poc.platform.agent.scenario.ScenarioCommandException;
import com.example.poc.platform.agent.scenario.ScenarioDraftConflictException;
import com.example.poc.platform.persistence.JsonCodec;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared platform tool execution: budgets, timeouts, errors, and artifact events. */
final class AgentToolExecution {
    private static final Logger log = LoggerFactory.getLogger(AgentToolExecution.class);
    private final JsonCodec jsonCodec;
    private final AgentContextRedactor redactor;

    AgentToolExecution(JsonCodec jsonCodec, AgentContextRedactor redactor) {
        this.jsonCodec = jsonCodec;
        this.redactor = redactor;
    }

    String execute(AgentTool tool, AgentTurnRequest request, Consumer<AgentRuntimeEvent> eventSink,
            AgentToolCallBudget budget, String arguments) {
        long startedNanos = System.nanoTime();
        Map<String, Object> input;
        try {
            input = jsonCodec.readObjectMap(arguments);
        } catch (IllegalArgumentException exception) {
            return invalidToolInputResult(
                    tool, request, arguments, exception, startedNanos, eventSink, budget);
        }
        AgentToolCallBudget.Invocation permit;
        try {
            permit = budget.begin(tool.definition().name(), input);
        } catch (AgentRuntimeException exception) {
            log.warn(
                    "Agent tool rejected taskId={} turnId={} tool={} code={} elapsedMs={}",
                    request.taskId(),
                    request.turnId(),
                    tool.definition().name(),
                    exception.getCode(),
                    elapsedMillis(startedNanos));
            eventSink.accept(new AgentRuntimeEvent("tool.failed", Map.of(
                    "toolName", tool.definition().name(),
                    "code", exception.getCode(),
                    "message", exception.getMessage())));
            throw exception;
        }
        log.info(
                "Agent tool started taskId={} turnId={} tool={} callSequence={} inputFingerprint={}",
                request.taskId(),
                request.turnId(),
                tool.definition().name(),
                permit.sequence(),
                permit.inputFingerprint());
        eventSink.accept(new AgentRuntimeEvent("tool.started", Map.of(
                "toolName", tool.definition().name(),
                "category", tool.definition().category(),
                "sideEffect", tool.definition().sideEffect(),
                "callSequence", permit.sequence(),
                "inputFingerprint", permit.inputFingerprint())));
        CompletableFuture<AgentToolResult> toolFuture = CompletableFuture.supplyAsync(
                () -> tool.execute(input));
        try {
            AgentToolResult toolResult = toolFuture.get(
                    tool.definition().timeoutMillis(), TimeUnit.MILLISECONDS);
            budget.succeeded(permit, toolResult.output(), toolResult.artifacts().size());
            toolResult.artifacts().forEach(artifact -> eventSink.accept(AgentRuntimeEvent.artifact(artifact)));
            String output = jsonCodec.write(Map.of("ok", true, "result", toolResult.output()));
            if (output.length() > tool.definition().maxOutputCharacters()) {
                log.warn(
                        "Agent tool output rejected taskId={} turnId={} tool={} callSequence={} code={} elapsedMs={} outputCharacters={} limit={}",
                        request.taskId(),
                        request.turnId(),
                        tool.definition().name(),
                        permit.sequence(),
                        "TOOL_OUTPUT_TOO_LARGE",
                        elapsedMillis(startedNanos),
                        output.length(),
                        tool.definition().maxOutputCharacters());
                eventSink.accept(new AgentRuntimeEvent("tool.failed", Map.of(
                        "toolName", tool.definition().name(),
                        "callSequence", permit.sequence(),
                        "code", "TOOL_OUTPUT_TOO_LARGE",
                        "message", "Tool output exceeded its limit")));
                return jsonCodec.write(Map.of(
                        "ok", false,
                        "error", Map.of("code", "TOOL_OUTPUT_TOO_LARGE", "message", "Tool output exceeded its limit")));
            }
            eventSink.accept(new AgentRuntimeEvent("tool.completed", Map.of(
                    "toolName", tool.definition().name(),
                    "callSequence", permit.sequence(),
                    "artifactCount", toolResult.artifacts().size(),
                    "outputCharacters", output.length())));
            log.info(
                    "Agent tool completed taskId={} turnId={} tool={} callSequence={} elapsedMs={} artifactCount={} outputCharacters={}",
                    request.taskId(),
                    request.turnId(),
                    tool.definition().name(),
                    permit.sequence(),
                    elapsedMillis(startedNanos),
                    toolResult.artifacts().size(),
                    output.length());
            return output;
        } catch (TimeoutException exception) {
            toolFuture.cancel(true);
            budget.failed(permit, "TOOL_TIMEOUT");
            return failedToolResult(
                    tool, request, permit, "TOOL_TIMEOUT", "Tool execution timed out", startedNanos, eventSink);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            budget.failed(permit, "TOOL_INTERRUPTED");
            return failedToolResult(
                    tool, request, permit, "TOOL_INTERRUPTED", "Tool execution was interrupted", startedNanos, eventSink);
        } catch (Exception exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof AgentRuntimeException runtimeException) {
                log.warn(
                        "Agent tool stopped turn taskId={} turnId={} tool={} callSequence={} code={} elapsedMs={}",
                        request.taskId(),
                        request.turnId(),
                        tool.definition().name(),
                        permit.sequence(),
                        runtimeException.getCode(),
                        elapsedMillis(startedNanos));
                eventSink.accept(new AgentRuntimeEvent("tool.failed", Map.of(
                        "toolName", tool.definition().name(),
                        "callSequence", permit.sequence(),
                        "code", runtimeException.getCode(),
                        "message", runtimeException.getMessage())));
                throw runtimeException;
            }
            String code = toolErrorCode(cause);
            budget.failed(permit, code);
            return failedToolResult(
                    tool, request, permit, code, safeToolMessage(cause), startedNanos, eventSink,
                    toolErrorDetails(cause));
        }
        }

    private String invalidToolInputResult(
            AgentTool tool,
            AgentTurnRequest request,
            String arguments,
            IllegalArgumentException exception,
            long startedNanos,
            Consumer<AgentRuntimeEvent> eventSink,
            AgentToolCallBudget budget) {
        String toolName = tool.definition().name();
        int argumentCharacters = arguments == null ? 0 : arguments.length();
        AgentToolCallBudget.Invocation permit;
        try {
            permit = budget.begin(toolName, Map.of(
                    "_invalidInput", true,
                    "_argumentCharacters", argumentCharacters));
            budget.failed(permit, "TOOL_INPUT_INVALID");
        } catch (AgentRuntimeException runtimeException) {
            log.warn(
                    "Agent malformed tool input stopped turn taskId={} turnId={} tool={} code={} argumentCharacters={} elapsedMs={}",
                    request.taskId(),
                    request.turnId(),
                    toolName,
                    runtimeException.getCode(),
                    argumentCharacters,
                    elapsedMillis(startedNanos));
            eventSink.accept(new AgentRuntimeEvent("tool.failed", Map.of(
                    "toolName", toolName,
                    "code", runtimeException.getCode(),
                    "message", runtimeException.getMessage())));
            throw runtimeException;
        }

        log.warn(
                "Agent tool input was not valid JSON taskId={} turnId={} tool={} callSequence={} argumentCharacters={} elapsedMs={} cause={}",
                request.taskId(),
                request.turnId(),
                toolName,
                permit.sequence(),
                argumentCharacters,
                elapsedMillis(startedNanos),
                exception.getCause() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getCause().getClass().getSimpleName());
        return failedToolResult(
                tool,
                request,
                permit,
                "TOOL_INPUT_INVALID",
                "Tool input must be a complete JSON object matching the Tool schema. Regenerate the entire Tool call.",
                startedNanos,
                eventSink);
    }

    private String toolErrorCode(Throwable error) {
        if (error instanceof SecurityException) return "TOOL_PERMISSION_DENIED";
        if (error instanceof ScenarioCommandException commandError) return commandError.getCode();
        if (error instanceof ScenarioDraftConflictException conflict) return conflict.getReason();
        if (error instanceof IllegalArgumentException) return "TOOL_INPUT_INVALID";
        return "TOOL_EXECUTION_FAILED";
    }

    private String failedToolResult(
            AgentTool tool,
            AgentTurnRequest request,
            AgentToolCallBudget.Invocation permit,
            String code,
            String message,
            long startedNanos,
            Consumer<AgentRuntimeEvent> eventSink) {
        return failedToolResult(tool, request, permit, code, message, startedNanos, eventSink, Map.of());
    }

    private Map<String, Object> toolErrorDetails(Throwable cause) {
        if (!(cause instanceof ScenarioCommandException error)) return Map.of();
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        if (error.getPath() != null) details.put("path", redactor.redactText(error.getPath()));
        if (error.getCommandIndex() >= 0) details.put("commandIndex", error.getCommandIndex());
        return details;
    }

    private String failedToolResult(
            AgentTool tool,
            AgentTurnRequest request,
            AgentToolCallBudget.Invocation permit,
            String code,
            String message,
            long startedNanos,
            Consumer<AgentRuntimeEvent> eventSink,
            Map<String, Object> details) {
        Map<String, Object> error = new java.util.LinkedHashMap<>(details);
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> payload = new java.util.LinkedHashMap<>(error);
        payload.put("toolName", tool.definition().name());
        payload.put("callSequence", permit.sequence());
        eventSink.accept(new AgentRuntimeEvent("tool.failed", payload));
        log.warn(
                "Agent tool failed taskId={} turnId={} tool={} callSequence={} code={} elapsedMs={} path={} commandIndex={} message={}",
                request.taskId(),
                request.turnId(),
                tool.definition().name(),
                permit.sequence(),
                code,
                elapsedMillis(startedNanos),
                details.getOrDefault("path", ""),
                details.getOrDefault("commandIndex", ""),
                message);
        return jsonCodec.write(Map.of(
                "ok", false,
                "error", error));
    }

    private String safeToolMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "Tool execution failed";
        String safe = redactor.redactText(message);
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    private long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}