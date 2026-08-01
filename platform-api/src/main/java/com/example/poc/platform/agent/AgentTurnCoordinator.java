package com.example.poc.platform.agent;

import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.AgentTaskService.StartedTurn;
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
import org.springframework.stereotype.Service;

@Service
public class AgentTurnCoordinator {
    private final AgentTaskService taskService;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final AgentProperties properties;
    private final AgentRuntimeResources resources;
    private final AgentCapabilityService capabilityService;
    private final Map<String, ActiveTurn> activeTurns = new ConcurrentHashMap<>();

    public AgentTurnCoordinator(
            AgentTaskService taskService,
            AgentRuntimeRegistry runtimeRegistry,
            AgentProperties properties,
            AgentRuntimeResources resources,
            AgentCapabilityService capabilityService) {
        this.taskService = taskService;
        this.runtimeRegistry = runtimeRegistry;
        this.properties = properties;
        this.resources = resources;
        this.capabilityService = capabilityService;
    }

    public AgentTaskRecord submit(String taskId, String content) {
        return submit(taskId, taskService.get(taskId).createdBy(), content);
    }

    public AgentTaskRecord submit(String taskId, String actorId, String content) {
        AgentTaskRecord existing = taskService.get(taskId, actorId);
        if (existing.iterationCount() >= properties.getMaxTurns()) {
            return taskService.exhaustTurnBudget(taskId);
        }
        AgentRuntime runtime = runtimeRegistry.require(existing.runtimeType());
        String turnId = UUID.randomUUID().toString();
        AgentTurnPreparation preparation = capabilityService.prepare(existing, turnId, content);
        StartedTurn started = taskService.beginTurn(taskId, turnId, content);
        taskService.recordTurnPreparation(
                taskId, turnId, preparation.snapshot(), preparation.skillVersions());

        CompletableFuture<AgentTurnResult> future;
        try {
            future = runtime.startTurn(
                    started.runtimeRequest().withCapabilities(
                            preparation.systemContext(), preparation.skillNames(), preparation.tools()),
                    event -> taskService.recordRuntimeEvent(taskId, turnId, event));
        } catch (Throwable error) {
            taskService.failTurn(taskId, turnId, "RUNTIME_START_FAILED", "Agent runtime could not start", false);
            throw error;
        }

        ActiveTurn active = new ActiveTurn(turnId, runtime, future, started.task().tokenBudget());
        ActiveTurn previous = activeTurns.putIfAbsent(taskId, active);
        if (previous != null) {
            runtime.cancel(taskId);
            future.cancel(true);
            taskService.failTurn(taskId, turnId, "COORDINATOR_CONFLICT", "Agent turn could not be scheduled", true);
            throw new AgentTaskConflictException("TASK_ALREADY_RUNNING", "Agent Task already has a running turn");
        }
        active.timeout = resources.scheduler().schedule(
                () -> timeout(taskId, active),
                properties.getTurnTimeout().toMillis(),
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

    private void timeout(String taskId, ActiveTurn active) {
        if (!active.stopReason.compareAndSet(null, StopReason.TIMEOUT)) return;
        active.runtime.cancel(taskId);
        active.future.cancel(true);
        finish(taskId, active, null, new CancellationException("Agent turn timed out"));
    }

    private void finish(String taskId, ActiveTurn active, AgentTurnResult result, Throwable error) {
        if (!active.finished.compareAndSet(false, true)) return;
        if (active.timeout != null) active.timeout.cancel(false);
        try {
            StopReason stopReason = active.stopReason.get();
            if (stopReason == StopReason.USER_CANCELLED) {
                taskService.cancelTurn(taskId, active.turnId);
            } else if (stopReason == StopReason.TIMEOUT) {
                taskService.failTurn(taskId, active.turnId, "TURN_TIMEOUT", "Agent turn timed out", true);
            } else if (stopReason == StopReason.SERVICE_SHUTDOWN) {
                taskService.failTurn(
                        taskId, active.turnId, "SERVICE_SHUTDOWN", "Agent turn stopped because the service is shutting down", true);
            } else if (error != null) {
                Throwable cause = unwrap(error);
                if (cause instanceof AgentRuntimeException runtimeError) {
                    taskService.failTurn(
                            taskId, active.turnId, runtimeError.getCode(), runtimeError.getMessage(), runtimeError.isRetryable());
                } else {
                    taskService.failTurn(
                            taskId, active.turnId, "RUNTIME_FAILURE", "Agent runtime failed", false);
                }
            } else if (result.completionTokens() > active.tokenBudget) {
                taskService.failTurn(
                        taskId,
                        active.turnId,
                        "TOKEN_BUDGET_EXCEEDED",
                        "Agent response exceeded the configured token budget",
                        false);
            } else {
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

    private enum StopReason { USER_CANCELLED, TIMEOUT, SERVICE_SHUTDOWN }

    private static final class ActiveTurn {
        private final String turnId;
        private final AgentRuntime runtime;
        private final CompletableFuture<AgentTurnResult> future;
        private final long tokenBudget;
        private final AtomicReference<StopReason> stopReason = new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile ScheduledFuture<?> timeout;

        private ActiveTurn(
                String turnId,
                AgentRuntime runtime,
                CompletableFuture<AgentTurnResult> future,
                long tokenBudget) {
            this.turnId = turnId;
            this.runtime = runtime;
            this.future = future;
            this.tokenBudget = tokenBudget;
        }
    }
}
