package com.example.poc.platform.agent.suite;

import java.util.List;

public record SuiteValidationResult(boolean valid, List<ValidationIssue> issues) {
    public SuiteValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static SuiteValidationResult of(List<ValidationIssue> issues) {
        return new SuiteValidationResult(issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR), issues);
    }

    public record ValidationIssue(Severity severity, String code, String path, String message) {}
    public enum Severity { ERROR, WARNING, INFORMATION }
}
