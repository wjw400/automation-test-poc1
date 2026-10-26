package com.example.poc.platform.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.persistence.JsonCodec;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "RUN_DEEPSEEK_INTEGRATION_TEST", matches = "true")
class DeepSeekLiveIntegrationTest {
    @Test
    void streamsARealDeepSeekResponseWhenExplicitlyEnabled() throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertThat(apiKey).as("DEEPSEEK_API_KEY must be set for the live test").isNotBlank();

        AgentProperties properties = new AgentProperties();
        properties.getDeepseek().setApiKey(apiKey);
        String model = System.getenv("DEEPSEEK_MODEL");
        if (model != null && !model.isBlank()) properties.getDeepseek().setModel(model);
        properties.setModelRequestTimeout(Duration.ofSeconds(60));
        DeepSeekAgentRuntime runtime = new DeepSeekAgentRuntime(
                properties,
                new JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build()),
                new AgentContextRedactor());

        AgentTurnResult result = runtime.startTurn(
                        new AgentTurnRequest(
                                "live-task", "live-turn", properties.getDeepseek().getModel(), 64,
                                List.of(new AgentConversationMessage("USER", "Reply with exactly: agent-ready"))),
                        ignored -> { })
                .get(90, TimeUnit.SECONDS);

        assertThat(result.content()).containsIgnoringCase("agent-ready");
    }
}
