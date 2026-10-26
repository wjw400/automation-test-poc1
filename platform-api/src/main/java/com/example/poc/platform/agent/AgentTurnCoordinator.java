package com.example.poc.platform.agent;

import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.AgentTaskService.StartedTurn;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.agent.runtime.AgentRuntime;
import com.example.poc.platform.agent.runtime.AgentRuntimeException;
import com.example.poc.platform.agent.runtime.AgentRuntimeRegistry;
import com.example.poc.platform.agent.runtime.AgentTurnResult;
import com.example.poc.platform.agent.capability.AgentCapabilityService;
import com.example.poc.platform.agent.capability.AgentCapabilityService.AgentTurnPreparation;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AgentTurnCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AgentTurnCoordinator.class);
    private final AgentTaskService taskService;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final AgentProperties properties;
    private final AgentRuntimeResources resources;
    private final AgentCapabilityService capabilityService;
    private final AgentContextRedactor redactor;
    private final Map<String, ActiveTurn> activeTurns = new ConcurrentHashMap<>();

    public AgentTurnCoordinator(
            AgentTaskService taskService,
            AgentRuntimeRegistry runtimeRegistry,
            AgentProperties properties,
            AgentRuntimeResources resources,
            AgentCapabilityService capabilityService,
            AgentContextRedactor redactor) {
        this.taskService = taskService;
        this.runtimeRegistry = runtimeRegistry;
        this.properties = properties;
        this.resources = resources;
        this.capabilityService = capabilityService;
        this.redactor = redactor;
    }

    public AgentTaskRecord submit(String taskId, String content) {
        return submit(taskId, taskService.get(taskId).createdBy(), content);
    }

    public AgentTaskRecord submit(String taskId, String actorId, String content) {
        AgentTaskRecord existing = taskService.get(taskId, actorId);
        if (existing.iterationCount() >= properties.getMaxTurns()) {
            return taskService.pauseForTurnBudget(taskId, content);
        }
        if ("WAITING_FOR_USER".equals(existing.status())) {
            throw new AgentTaskConflictException("TASK_WAITING_FOR_USER", "Agent Task is waiting for a continuation decision");
        }
        String turnId = UUID.randomUUID().toString();
        StartedTurn started = taskService.beginTurn(taskId, turnId, content);
        return startPreparedTurn(existing, started, content, false);
    }

    public AgentTaskRecord continueTask(String taskId, String actorId) {
        AgentTaskRecord existing = taskService.get(taskId, actorId);
        String turnId = UUID.randomUUID().toString();
        StartedTurn started = taskService.beginContinuationTurn(taskId, turnId);
        return startPreparedTurn(existing, started, started.runtimeRequest().latestUserText(), true);
    }

    private AgentTaskRecord startPreparedTurn(
            AgentTaskRecord existing,
            StartedTurn started,
            String content,
            boolean continuation) {
        AgentRuntime runtime = runtimeRegistry.require(existing.runtimeType());
        String turnId = started.runtimeRequest().turnId();
        AgentTurnPreparation preparation = capabilityService.prepare(existing, turnId, content);
        taskService.recordTurnPreparation(
                started.runtimeRequest().taskId(), turnId, preparation.snapshot(), preparation.skillVersions());
        log.info(
                "Agent turn prepared taskId={} turnId={} projectId={} runtime={} model={} iteration={} tokenBudget={} continuation={} skills={} tools={}",
                started.runtimeRequest().taskId(),
                turnId,
                existing.projectId(),
                existing.runtimeType(),
                existing.modelId(),
                started.task().iterationCount(),
                started.task().tokenBudget(),
                continuation,
                preparation.skillNames(),
                preparation.tools().stream().map(tool -> tool.definition().name()).toList());

        CompletableFuture<AgentTurnResult> future;
        AtomicReference<ActiveTurn> activeReference = new AtomicReference<>();
        try {
            future = runtime.startTurn(
                    started.runtimeRequest().withCapabilities(
                            withContinuationContext(preparation.systemContext(), continuation),
                            preparation.skillNames(), preparation.tools()),
                    event -> {
                        ActiveTurn current = activeReference.get();
                        if (current != null) current.touch();
                        taskService.recordRuntimeEvent(started.runtimeRequest().taskId(), turnId, event);
                    });
        } catch (Throwable error) {
            log.error(
                    "Agent runtime failed to start taskId={} turnId={} runtime={} diagnostic={}",
                    started.runtimeRequest().taskId(),
                    turnId,
                    existing.runtimeType(),
                    safeDiagnostic(error));
            taskService.failTurn(started.runtimeRequest().taskId(), turnId, "RUNTIME_START_FAILED", "Agent runtime could not start", false);
            throw error;
        }

        ActiveTurn active = new ActiveTurn(turnId, runtime, future, started.task().tokenBudget(), System.nanoTime());
        activeReference.set(active);
        String taskId = started.runtimeRequest().taskId();
        ActiveTurn previous = activeTurns.putIfAbsent(taskId, active);
        if (previous != null) {
            log.warn("Agent turn scheduling conflict taskId={} turnId={}", taskId, turnId);
            runtime.cancel(taskId);
            future.cancel(true);
            taskService.failTurn(taskId, turnId, "COORDINATOR_CONFLICT", "Agent turn could not be scheduled", true);
            throw new AgentTaskConflictException("TASK_ALREADY_RUNNING", "Agent Task already has a running turn");
        }
        active.runTimeout = resources.scheduler().schedule(
                () -> timeout(taskId, active, StopReason.RUN_TIMEOUT),
                properties.getRunTimeout().toMillis(),
                TimeUnit.MILLISECONDS);
        active.inactivityMonitor = resources.scheduler().scheduleWithFixedDelay(
                () -> checkInactivity(taskId, active),
                inactivityCheckIntervalMillis(),
                inactivityCheckIntervalMillis(),
                TimeUnit.MILLISECONDS);
        future.whenComplete((result, error) -> finish(taskId, active, result, error));
        return started.task();
    }

    public AgentTaskRecord cancel(String taskId) {
        return cancel(taskId, taskService.get(taskId).createdBy());
    }

    public AgentTaskRecord cancel(String taskId, String actorId) {
        taskService.get(taskId, actorId);
        ActiveTurn active = activeTurns.get(taskId);
        if (active == null) {
            AgentTaskRecord task = taskService.get(taskId, actorId);
            if (!"RUNNING".equals(task.status())) return task;
            return taskService.cancelTurn(taskId, "unknown");
        }
        active.stopReason.compareAndSet(null, StopReason.USER_CANCELLED);
        active.runtime.cancel(taskId);
        active.future.cancel(true);
        finish(taskId, active, null, new CancellationException("Agent turn cancelled"));
        return taskService.get(taskId, actorId);
    }

    private void checkInactivity(String taskId, ActiveTurn active) {
        if (active.inactiveMillis() < properties.getInactivityTimeout().toMillis()) return;
        timeout(taskId, active, StopReason.INACTIVITY_TIMEOUT);
    }

    private long inactivityCheckIntervalMillis() {
        long timeout = properties.getInactivityTimeout().toMillis();
        return Math.max(1, Math.min(30_000, timeout / 4));
    }

    private void timeout(String taskId, ActiveTurn active, StopReason reason) {
        if (!active.stopReason.compareAndSet(null, reason)) return;
        log.warn(
                "Agent turn timed out taskId={} turnId={} reason={} elapsedMs={} inactiveMs={} runTimeoutMs={} inactivityTimeoutMs={}",
                taskId,
                active.turnId,
                reason,
                active.elapsedMillis(),
                active.inactiveMillis(),
                properties.getRunTimeout().toMillis(),
                properties.getInactivityTimeout().toMillis());
        active.runtime.cancel(taskId);
        active.future.cancel(true);
        finish(taskId, active, null, new CancellationException("Agent turn timed out"));
    }

    private void finish(String taskId, ActiveTurn active, AgentTurnResult result, Throwable error) {
        if (!active.finished.compareAndSet(false, true)) return;
        if (active.runTimeout != null) active.runTimeout.cancel(false);
        if (active.inactivityMonitor != null) active.inactivityMonitor.cancel(false);
        try {
            StopReason stopReason = active.stopReason.get();
            if (stopReason == StopReason.USER_CANCELLED) {
                log.info(
                        "Agent turn cancelled taskId={} turnId={} elapsedMs={}",
                        taskId,
                        active.turnId,
                        active.elapsedMillis());
                taskService.cancelTurn(taskId, active.turnId);
            } else if (stopReason == StopReason.RUN_TIMEOUT) {
                taskService.pauseTurn(taskId, active.turnId, AgentPauseReason.RUN_WINDOW_REACHED, "RUN_TIMEOUT");
            } else if (stopReason == StopReason.INACTIVITY_TIMEOUT) {
                taskService.pauseTurn(taskId, active.turnId, AgentPauseReason.INACTIVITY_DETECTED, "INACTIVITY_TIMEOUT");
            } else if (stopReason == StopReason.SERVICE_SHUTDOWN) {
                log.warn(
                        "Agent turn stopped during shutdown taskId={} turnId={} elapsedMs={}",
                        taskId,
                        active.turnId,
                        active.elapsedMillis());
                taskService.pauseTurn(taskId, active.turnId, AgentPauseReason.SERVICE_INTERRUPTED, "SERVICE_SHUTDOWN");
            } else if (error != null) {
                Throwable cause = unwrap(error);
                if (cause instanceof AgentRuntimeException runtimeError) {
                    log.error(
                            "Agent turn failed taskId={} turnId={} code={} retryable={} elapsedMs={} causeType={} message={}",
                            taskId,
                            active.turnId,
                            runtimeError.getCode(),
                            runtimeError.isRetryable(),
                            active.elapsedMillis(),
                            cause.getCause() == null ? cause.getClass().getName() : cause.getCause().getClass().getName(),
                            safeMessage(runtimeError));
                    AgentPauseReason.fromRuntimeCode(runtimeError.getCode())
                            .ifPresentOrElse(
                                    reason -> taskService.pauseTurn(taskId, active.turnId, reason, runtimeError.getCode()),
                                    () -> taskService.failTurn(
                                            taskId, active.turnId, runtimeError.getCode(), runtimeError.getMessage(), runtimeError.isRetryable()));
                } else {
                    log.error(
                            "Agent turn failed taskId={} turnId={} code={} retryable={} elapsedMs={} causeType={} message={}",
                            taskId,
                            active.turnId,
                            "RUNTIME_FAILURE",
                            false,
                            active.elapsedMillis(),
                            cause.getClass().getName(),
                            safeMessage(cause));
                    taskService.failTurn(
                            taskId, active.turnId, "RUNTIME_FAILURE", "Agent runtime failed", false);
                }
            } else if (result.completionTokens() > active.tokenBudget) {
                log.warn(
                        "Agent turn exceeded token budget taskId={} turnId={} elapsedMs={} completionTokens={} tokenBudget={}",
                        taskId,
                        active.turnId,
                        active.elapsedMillis(),
                        result.completionTokens(),
                        active.tokenBudget);
                taskService.pauseTurn(
                        taskId,
                        active.turnId,
                        AgentPauseReason.OUTPUT_ALLOWANCE_REACHED,
                        "TOKEN_BUDGET_EXCEEDED");
            } else {
                log.info(
                        "Agent turn completed taskId={} turnId={} elapsedMs={} promptTokens={} completionTokens={} artifacts={}",
                        taskId,
                        active.turnId,
                        active.elapsedMillis(),
                        result.promptTokens(),
                        result.completionTokens(),
                        result.artifacts().size());
                taskService.completeTurn(taskId, active.turnId, result);
            }
        } finally {
            activeTurns.remove(taskId, active);
        }
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException) && current.getCause() != null) current = current.getCause();
        return current;
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "<no message>";
        String flattened = redactor.redactText(message).replace('\r', ' ').replace('\n', ' ');
        return flattened.length() <= 1000 ? flattened : flattened.substring(0, 1000);
    }

    private String withContinuationContext(String context, boolean continuation) {
        if (!continuation) return context;
        return context + "\n\nCONTINUATION CONTEXT (trusted platform instruction):\n"
                + "Continue the user's interrupted request. Do not repeat completed side effects. "
                + "Inspect and reuse existing Drafts and validate mutable resources before applying new commands. "
                + "If the goal is already satisfied, summarize the result and stop.";
    }

    private String safeDiagnostic(Throwable error) {
        StringBuilder diagnostic = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (!diagnostic.isEmpty()) diagnostic.append(" <- ");
            diagnostic.append(current.getClass().getName()).append(": ").append(safeMessage(current));
            current = current.getCause();
        }
        return diagnostic.toString();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() { taskService.recoverInterruptedTasks(); }

    @PreDestroy
    public void stopActiveTurns() {
        activeTurns.forEach((taskId, active) -> {
            if (!active.stopReason.compareAndSet(null, StopReason.SERVICE_SHUTDOWN)) return;
            active.runtime.cancel(taskId);
            active.future.cancel(true);
            finish(taskId, active, null, new CancellationException("Agent service is shutting down"));
        });
    }

    private enum StopReason { USER_CANCELLED, RUN_TIMEOUT, INACTIVITY_TIMEOUT, SERVICE_SHUTDOWN }

    private static final class ActiveTurn {
        private final String turnId;
        private final AgentRuntime runtime;
        private final CompletableFuture<AgentTurnResult> future;
        private final long tokenBudget;
        private final long startedNanos;
        private volatile long lastActivityNanos;
        private final AtomicReference<StopReason> stopReason = new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile ScheduledFuture<?> runTimeout;
        private volatile ScheduledFuture<?> inactivityMonitor;

        private ActiveTurn(
                String turnId,
                AgentRuntime runtime,
                CompletableFuture<AgentTurnResult> future,
                long tokenBudget,
                long startedNanos) {
            this.turnId = turnId;
            this.runtime = runtime;
            this.future = future;
            this.tokenBudget = tokenBudget;
            this.startedNanos = startedNanos;
            this.lastActivityNanos = startedNanos;
        }

        private long elapsedMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        }

        private long inactiveMillis() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastActivityNanos);
        }

        private void touch() {
            lastActivityNanos = System.nanoTime();
        }
    }
}
