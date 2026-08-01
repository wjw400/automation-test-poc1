package com.example.poc.platform.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.context.AgentContextSelection;
import com.example.poc.platform.agent.AgentTaskService.StartedTurn;
import com.example.poc.platform.agent.runtime.AgentRuntime;
import com.example.poc.platform.agent.runtime.AgentRuntimeCapabilities;
import com.example.poc.platform.agent.runtime.AgentRuntimeRegistry;
import com.example.poc.platform.agent.runtime.AgentTurnRequest;
import com.example.poc.platform.agent.runtime.AgentTurnResult;
import com.example.poc.platform.agent.capability.AgentCapabilityService;
import com.example.poc.platform.agent.capability.AgentCapabilityService.AgentTurnPreparation;
import com.example.poc.platform.agent.context.AgentContextSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AgentTurnCoordinatorTest {
    private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void close() {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    @Test
    void cancelsTheRuntimeAndPersistsTimeoutFailure() throws Exception {
        AgentTaskService taskService = mock(AgentTaskService.class);
        AgentRuntimeRegistry registry = mock(AgentRuntimeRegistry.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        AgentCapabilityService capabilityService = mock(AgentCapabilityService.class);
        CompletableFuture<AgentTurnResult> pending = new CompletableFuture<>();
        AgentTaskRecord task = task("RUNNING", 100);
        AgentTurnRequest request = new AgentTurnRequest("task-1", "turn", "model", 100, List.of());

        when(taskService.get("task-1")).thenReturn(task);
        when(taskService.get("task-1", "tester")).thenReturn(task);
        when(capabilityService.prepare(eq(task), any(), eq("wait"))).thenAnswer(invocation -> {
            String turnId = invocation.getArgument(1);
            return new AgentTurnPreparation(
                    new AgentContextSnapshot(
                            "snapshot", "task-1", turnId, "project-1", null, null,
                            Instant.now(), List.of()),
                    "", List.of(), java.util.Map.of(), List.of());
        });
        when(taskService.beginTurn(eq("task-1"), any(), eq("wait"))).thenReturn(new StartedTurn(task, request));
        when(registry.require("FAKE")).thenReturn(runtime);
        when(runtime.startTurn(any(), any())).thenReturn(pending);
        when(runtime.cancel("task-1")).thenReturn(true);

        AgentProperties properties = new AgentProperties();
        properties.setTurnTimeout(Duration.ofMillis(20));
        AgentTurnCoordinator coordinator = new AgentTurnCoordinator(
                taskService,
                registry,
                properties,
                new AgentRuntimeResources(executor, scheduler),
                capabilityService);

        coordinator.submit("task-1", "wait");
        Thread.sleep(100);

        verify(runtime).cancel("task-1");
        verify(taskService).failTurn(eq("task-1"), any(), eq("TURN_TIMEOUT"), any(), eq(true));
        assertThat(pending).isCancelled();
    }

    private AgentTaskRecord task(String status, long tokenBudget) {
        Instant now = Instant.now();
        return new AgentTaskRecord(
                "task-1", "project-1", "Task", status,
                null, null, "DRAFT_ONLY", "FAKE", "model", "tester",
                1, tokenBudget, AgentContextSelection.empty(), 0, now, now, null);
    }
}
