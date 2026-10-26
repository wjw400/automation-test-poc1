package com.example.poc.platform.agent;

/**
 * The immutable authority boundary assigned when an Agent task is created.
 */
public enum AgentPermissionMode {
    READ_ONLY,
    REVIEW_REQUIRED,
    AUTO_APPLY;

    public static AgentPermissionMode defaultForTarget(String targetType) {
        return "SCENARIO".equals(targetType) || "SUITE".equals(targetType) ? REVIEW_REQUIRED : READ_ONLY;
    }
}
