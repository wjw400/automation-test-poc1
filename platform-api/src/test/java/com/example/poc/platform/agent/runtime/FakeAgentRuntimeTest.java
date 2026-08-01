package com.example.poc.platform.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.agent.AgentRuntimeResources;
import com.example.poc.platform.persistence.JsonCodec;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FakeAgentRuntimeTest {
    private final java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void close() {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    @Test
    void emitsPredictableDeltasAndArtifact() throws Exception {
        AgentProperties properties = new AgentProperties();
        properties.getFake().setChunkDelay(Duration.ZERO);
        FakeAgentRuntime runtime = new FakeAgentRuntime(
                properties,
                new AgentRuntimeResources(executor, scheduler),
                new JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build()));
        java.util.ArrayList<AgentRuntimeEvent> events = new java.util.ArrayList<>();

        AgentTurnResult result = runtime.startTurn(
                        new AgentTurnRequest(
                                "task-1", "turn-1", "fake-agent-v1", 100,
                                List.of(new AgentConversationMessage("USER", "hello"))),
                        events::add)
                .get(2, TimeUnit.SECONDS);

        assertThat(result.content()).isEqualTo("Fake agent response: hello");
        assertThat(result.artifacts()).singleElement()
                .satisfies(artifact -> assertThat(artifact.artifactType()).isEqualTo("RUNTIME_TRACE"));
        assertThat(events).isNotEmpty().allMatch(event -> "message.delta".equals(event.type()));
    }
}
