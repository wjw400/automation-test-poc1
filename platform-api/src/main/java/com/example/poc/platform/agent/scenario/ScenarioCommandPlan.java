package com.example.poc.platform.agent.scenario;

import java.util.List;

public record ScenarioCommandPlan(
        String targetType,
        String targetId,
        long baseRevision,
        List<ScenarioCommand> commands,
        List<String> assumptions,
        List<String> warnings,
        List<Citation> citations) {

    public ScenarioCommandPlan {
        commands = commands == null ? List.of() : List.copyOf(commands);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }

    public record Citation(String knowledgeId, String scope, String targetId) {}
}
