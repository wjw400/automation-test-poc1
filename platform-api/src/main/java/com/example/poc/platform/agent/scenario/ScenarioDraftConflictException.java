package com.example.poc.platform.agent.scenario;

public class ScenarioDraftConflictException extends RuntimeException {
    private final String reason;
    private final long expectedRevision;
    private final long actualRevision;

    public ScenarioDraftConflictException(
            String reason,
            String message,
            long expectedRevision,
            long actualRevision) {
        super(message);
        this.reason = reason;
        this.expectedRevision = expectedRevision;
        this.actualRevision = actualRevision;
    }

    public String getReason() { return reason; }
    public long getExpectedRevision() { return expectedRevision; }
    public long getActualRevision() { return actualRevision; }
}
