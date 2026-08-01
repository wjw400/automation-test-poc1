package com.example.poc.platform.agent.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.persistence.JsonCodec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolCallBudgetTest {
    private final JsonCodec jsonCodec = new JsonCodec(tools.jackson.databind.json.JsonMapper.builder().build());

    @Test
    void stopsAfterTheConfiguredToolCallLimit() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxToolCalls(2);
        AgentToolCallBudget budget = new AgentToolCallBudget(properties, jsonCodec);

        budget.begin("one", Map.of());
        budget.begin("two", Map.of());

        assertThatThrownBy(() -> budget.begin("three", Map.of()))
                .isInstanceOf(AgentRuntimeException.class)
                .extracting(error -> ((AgentRuntimeException) error).getCode())
                .isEqualTo("TOOL_CALL_BUDGET_EXCEEDED");
    }

    @Test
    void stopsRepeatedCallsAndIdenticalNoProgressResults() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxRepeatedToolCalls(1);
        AgentToolCallBudget repeated = new AgentToolCallBudget(properties, jsonCodec);
        repeated.begin("read", Map.of("id", "same"));
        assertThatThrownBy(() -> repeated.begin("read", Map.of("id", "same")))
                .isInstanceOf(AgentRuntimeException.class)
                .extracting(error -> ((AgentRuntimeException) error).getCode())
                .isEqualTo("REPEATED_TOOL_CALL");

        properties.setMaxRepeatedToolCalls(10);
        properties.setMaxNoProgressToolCalls(2);
        AgentToolCallBudget noProgress = new AgentToolCallBudget(properties, jsonCodec);
        var first = noProgress.begin("read", Map.of("page", 1));
        noProgress.succeeded(first, Map.of("value", "same"), 0);
        var second = noProgress.begin("read", Map.of("page", 1));
        noProgress.succeeded(second, Map.of("value", "same"), 0);
        var third = noProgress.begin("read", Map.of("page", 1));
        assertThatThrownBy(() -> noProgress.succeeded(third, Map.of("value", "same"), 0))
                .isInstanceOf(AgentRuntimeException.class)
                .extracting(error -> ((AgentRuntimeException) error).getCode())
                .isEqualTo("NO_PROGRESS");
    }

    @Test
    void treatsDifferentInputsAsProgressEvenWhenResultsAreIdentical() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxRepeatedToolCalls(10);
        properties.setMaxNoProgressToolCalls(2);
        AgentToolCallBudget budget = new AgentToolCallBudget(properties, jsonCodec);

        assertThatCode(() -> {
            var first = budget.begin("search", Map.of("query", "one"));
            budget.succeeded(first, Map.of("matches", java.util.List.of()), 0);
            var second = budget.begin("search", Map.of("query", "two"));
            budget.succeeded(second, Map.of("matches", java.util.List.of()), 0);
            var third = budget.begin("search", Map.of("query", "three"));
            budget.succeeded(third, Map.of("matches", java.util.List.of()), 0);
        }).doesNotThrowAnyException();
    }

    @Test
    void stopsDifferentInputsThatKeepProducingTheSameToolError() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxRepeatedToolCalls(10);
        properties.setMaxNoProgressToolCalls(2);
        AgentToolCallBudget budget = new AgentToolCallBudget(properties, jsonCodec);

        var first = budget.begin("applyScenarioCommands", Map.of("field", "beforeStepId"));
        budget.failed(first, "UNKNOWN_COMMAND_FIELD");
        var second = budget.begin("applyScenarioCommands", Map.of("field", "targetStepId"));
        budget.failed(second, "UNKNOWN_COMMAND_FIELD");
        var third = budget.begin("applyScenarioCommands", Map.of("field", "position"));

        assertThatThrownBy(() -> budget.failed(third, "UNKNOWN_COMMAND_FIELD"))
                .isInstanceOf(AgentRuntimeException.class)
                .extracting(error -> ((AgentRuntimeException) error).getCode())
                .isEqualTo("NO_PROGRESS");
    }
}
