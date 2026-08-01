package com.example.poc.platform.agent.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioCommandDecoderTest {
    private final ScenarioCommandDecoder decoder = new ScenarioCommandDecoder(new ScenarioDslCodec());

    @Test
    void decodesStructuredKnowledgeCitations() {
        ScenarioCommandPlan plan = decoder.decode(Map.of(
                "targetType", "SCENARIO_DRAFT",
                "targetId", "draft-1",
                "baseRevision", 2,
                "commands", List.of(Map.of(
                        "type", "SetRequestHeader",
                        "stepId", "step-1",
                        "name", "X-Test",
                        "value", "true")),
                "citations", List.of(Map.of(
                        "knowledgeId", "knowledge-1",
                        "scope", "SCENARIO",
                        "targetId", "scenario-1"))));

        assertThat(plan.citations()).containsExactly(
                new ScenarioCommandPlan.Citation("knowledge-1", "SCENARIO", "scenario-1"));
    }

    @Test
    void rejectsScriptLikeFieldsFromCommandPlans() {
        assertThatThrownBy(() -> decoder.decode(Map.of(
                "targetType", "SCENARIO_DRAFT",
                "targetId", "draft-1",
                "baseRevision", 2,
                "commands", List.of(Map.of(
                        "type", "SetRequestHeader",
                        "stepId", "step-1",
                        "name", "X-Test",
                        "value", "true",
                        "script", "delete everything")))))
                .isInstanceOf(ScenarioCommandException.class)
                .hasMessageContaining("Unknown field");
    }
}
