package com.example.poc.platform.agent.scenario;

import java.util.List;

public record ScenarioValidationResult(boolean valid, List<ValidationIssue> issues) {
    public ScenarioValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static ScenarioValidationResult of(List<ValidationIssue> issues) {
        boolean valid = issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        return new ScenarioValidationResult(valid, issues);
    }

    public record ValidationIssue(Severity severity, String code, String path, String message) {}

    public enum Severity {
        ERROR,
        WARNING,
        INFORMATION
    }
}
