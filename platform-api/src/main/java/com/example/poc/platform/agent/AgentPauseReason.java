package com.example.poc.platform.agent;

import java.util.Optional;

public enum AgentPauseReason {
    EXECUTION_ALLOWANCE_REACHED,
    POSSIBLE_LOOP,
    OUTPUT_ALLOWANCE_REACHED,
    RUN_WINDOW_REACHED,
    INACTIVITY_DETECTED,
    RECOVERY_EXHAUSTED,
    SERVICE_INTERRUPTED;

    public static Optional<AgentPauseReason> fromRuntimeCode(String code) {
        return switch (code == null ? "" : code) {
            case "TOOL_CALL_BUDGET_EXCEEDED", "TURN_BUDGET_EXCEEDED" -> Optional.of(EXECUTION_ALLOWANCE_REACHED);
            case "REPEATED_TOOL_CALL", "NO_PROGRESS" -> Optional.of(POSSIBLE_LOOP);
            case "TOKEN_BUDGET_EXCEEDED" -> Optional.of(OUTPUT_ALLOWANCE_REACHED);
            case "RUN_TIMEOUT" -> Optional.of(RUN_WINDOW_REACHED);
            case "INACTIVITY_TIMEOUT" -> Optional.of(INACTIVITY_DETECTED);
            case "MODEL_RATE_LIMITED", "MODEL_TIMEOUT", "MODEL_UNAVAILABLE" -> Optional.of(RECOVERY_EXHAUSTED);
            case "SERVICE_RESTARTED", "SERVICE_SHUTDOWN" -> Optional.of(SERVICE_INTERRUPTED);
            default -> Optional.empty();
        };
    }
}
