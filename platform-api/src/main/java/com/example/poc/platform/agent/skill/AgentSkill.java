package com.example.poc.platform.agent.skill;

import java.util.List;

public record AgentSkill(
        String name,
        String description,
        String version,
        String riskLevel,
        List<String> requiredTools,
        List<String> matchTerms,
        boolean enabled,
        String instructions,
        String source) {
    public AgentSkill {
        requiredTools = List.copyOf(requiredTools);
        matchTerms = List.copyOf(matchTerms);
    }
}
