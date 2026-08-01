package com.example.poc.platform.agent.runtime;

public class AgentRuntimeException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public AgentRuntimeException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public AgentRuntimeException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() { return code; }
    public boolean isRetryable() { return retryable; }
}
