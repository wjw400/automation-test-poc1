package com.example.poc.platform.agent;

public class AgentTaskConflictException extends RuntimeException {
    private final String reason;

    public AgentTaskConflictException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String getReason() { return reason; }
}
