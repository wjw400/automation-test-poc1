package com.example.poc.platform.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.persistence.JsonCodec;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class DeepSeekAgentRuntimeTest {
    @Test
    void remainsUnavailableWithoutAnApiKey() {
        DeepSeekAgentRuntime runtime = new DeepSeekAgentRuntime(
                new AgentProperties(),
                new JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build()),
                new AgentContextRedactor());

        assertThat(runtime.capabilities().available()).isFalse();
        assertThat(runtime.startTurn(
                        new AgentTurnRequest(
                                "task-1", "turn-1", null, 100,
                                List.of(new AgentConversationMessage("USER", "hello"))),
                        ignored -> { }))
                .failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(AgentRuntimeException.class);
    }
}
