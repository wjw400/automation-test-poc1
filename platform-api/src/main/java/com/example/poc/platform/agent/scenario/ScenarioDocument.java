package com.example.poc.platform.agent.scenario;

import com.example.poc.engine.ApiTestCase;
import java.util.List;
import java.util.Objects;

public record ScenarioDocument(ApiTestCase scenario, List<String> stepIds) {
    public ScenarioDocument {
        Objects.requireNonNull(scenario, "scenario");
        stepIds = List.copyOf(Objects.requireNonNull(stepIds, "stepIds"));
        if (scenario.getSteps().size() != stepIds.size()) {
            throw new IllegalArgumentException("Step reference count must match Scenario step count");
        }
    }
}
