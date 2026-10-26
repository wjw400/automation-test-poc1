package com.example.poc.platform.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.agent.capability.AgentTool;
import com.example.poc.platform.agent.capability.AgentToolDefinition;
import com.example.poc.platform.agent.capability.AgentToolResult;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.persistence.JsonCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class DeepSeekAgentRuntimeTest {
    @Test
    void returnsExactCommandErrorLocationToTheModelAndEventStream() {
        JsonCodec json = new JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build());
        AgentTool tool = new AgentTool() {
            @Override public AgentToolDefinition definition() {
                return new AgentToolDefinition("applyNewScenarioCommands", "Apply commands", "{}", "{}",
                        "DRAFT_WRITE", "UPDATE_DRAFT", 1_000, 10_000);
            }
            @Override public AgentToolResult execute(Map<String, Object> input) {
                throw new com.example.poc.platform.agent.scenario.ScenarioCommandException(
                        "INVALID_COMMAND_FIELD", "Expected a non-blank string", 0, "plan.commands[0].stepId");
            }
        };
        List<AgentRuntimeEvent> events = new ArrayList<>();
        var request = new AgentTurnRequest("task-1", "turn-1", null, 100,
                List.of(new AgentConversationMessage("USER", "repair")));
        String result = runtime().callback(tool, request, events::add,
                new AgentToolCallBudget(new AgentProperties(), json)).call("{}");
        assertThat(json.readObjectMap(result)).containsEntry("ok", false);
        Map<?, ?> error = (Map<?, ?>) json.readObjectMap(result).get("error");
        assertThat(error.get("path")).isEqualTo("plan.commands[0].stepId");
        assertThat(error.get("commandIndex")).isEqualTo(0);
        assertThat(error.get("code")).isEqualTo("INVALID_COMMAND_FIELD");
        assertThat(events.get(events.size() - 1).payload())
                .containsEntry("path", "plan.commands[0].stepId").containsEntry("commandIndex", 0);
    }

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

    @Test
    void preservesAgentRuntimeErrorsWrappedByTheModelClient() {
        DeepSeekAgentRuntime runtime = runtime();
        AgentRuntimeException noProgress = new AgentRuntimeException(
                "NO_PROGRESS", "Agent Tool calls are no longer making progress", false);

        AgentRuntimeException classified = runtime.classify(
                new RuntimeException("model callback failed", new RuntimeException("tool failed", noProgress)));

        assertThat(classified).isSameAs(noProgress);
    }

    @Test
    void classifiesProviderErrorsFromNestedCauses() {
        DeepSeekAgentRuntime runtime = runtime();

        AgentRuntimeException classified = runtime.classify(
                new RuntimeException("stream failed", new IllegalStateException("HTTP 429 rate limit")));

        assertThat(classified.getCode()).isEqualTo("MODEL_RATE_LIMITED");
        assertThat(classified.isRetryable()).isTrue();
    }

    @Test
    void returnsRecoverableToolErrorWhenModelArgumentsContainTruncatedJson() {
        DeepSeekAgentRuntime runtime = runtime();
        AgentProperties properties = new AgentProperties();
        AgentToolCallBudget budget = new AgentToolCallBudget(
                properties,
                new JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build()));
        AtomicBoolean executed = new AtomicBoolean();
        List<AgentRuntimeEvent> events = new ArrayList<>();
        AgentTool tool = new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return new AgentToolDefinition(
                        "createScenario",
                        "Create a scenario",
                        "{\"type\":\"object\"}",
                        "{\"type\":\"object\"}",
                        "scenario",
                        "WRITE",
                        1_000,
                        10_000);
            }

            @Override
            public AgentToolResult execute(Map<String, Object> input) {
                executed.set(true);
                return new AgentToolResult(Map.of(), List.of());
            }
        };
        AgentTurnRequest request = new AgentTurnRequest(
                "task-1", "turn-1", null, 100, List.of(new AgentConversationMessage("USER", "create")));
        ToolCallback callback = runtime.callback(tool, request, events::add, budget);

        String result = callback.call("{\"name\":\"unfinished");

        assertThat(result).contains("\"ok\":false").contains("TOOL_INPUT_INVALID");
        assertThat(executed).isFalse();
        assertThat(events)
                .extracting(AgentRuntimeEvent::type)
                .containsExactly("tool.failed");
        assertThat(events.get(0).payload())
                .containsEntry("toolName", "createScenario")
                .containsEntry("code", "TOOL_INPUT_INVALID")
                .containsEntry("callSequence", 1);
    }

    private DeepSeekAgentRuntime runtime() {
        return new DeepSeekAgentRuntime(
                new AgentProperties(),
                new JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build()),
                new AgentContextRedactor());
    }
}
