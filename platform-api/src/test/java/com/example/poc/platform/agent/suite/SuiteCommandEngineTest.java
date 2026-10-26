package com.example.poc.platform.agent.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.poc.platform.agent.scenario.ScenarioCommandException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SuiteCommandEngineTest {
    private final SuiteCommandEngine engine = new SuiteCommandEngine();

    @Test
    void appliesEntryChangesWithoutChangingExistingEntryIdentity() {
        SuiteDraftDocument source = new SuiteDraftDocument("Fulfillment", "", "ACTIVE", List.of(
                new SuiteDraftDocument.Entry("entry-a", "scenario-a", "Create order", true, Map.of(), null, null),
                new SuiteDraftDocument.Entry("entry-b", "scenario-b", "Query order", true, Map.of(), null, null)));

        SuiteDraftDocument updated = engine.apply(source, List.of(
                new SuiteCommand.MoveSuiteEntry("entry-b", 0),
                new SuiteCommand.SetSuiteEntryEnabled("entry-a", false),
                new SuiteCommand.SetSuiteEntryVariableOverrides("entry-b", Map.of("orderId", "${createdOrderId}"))));

        assertThat(updated.entries()).extracting(SuiteDraftDocument.Entry::id).containsExactly("entry-b", "entry-a");
        assertThat(updated.entries().get(0).variableOverrides()).containsEntry("orderId", "${createdOrderId}");
        assertThat(updated.entries().get(1).enabled()).isFalse();
    }

    @Test
    void rejectsCommandsForUnknownEntry() {
        SuiteDraftDocument source = new SuiteDraftDocument("Fulfillment", "", "ACTIVE", List.of());

        assertThatThrownBy(() -> engine.apply(source, List.of(new SuiteCommand.RemoveSuiteEntry("missing"))))
                .isInstanceOf(ScenarioCommandException.class)
                .hasMessageContaining("Suite entry does not exist");
    }
}
