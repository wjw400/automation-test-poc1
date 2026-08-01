package com.example.poc.platform.agent.runtime;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface AgentRuntime {
    String type();
    AgentRuntimeCapabilities capabilities();
    CompletableFuture<AgentTurnResult> startTurn(
            AgentTurnRequest request,
            Consumer<AgentRuntimeEvent> eventSink);
    boolean cancel(String taskId);
}
