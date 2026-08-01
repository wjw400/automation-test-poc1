package com.example.poc.platform.web;

import com.example.poc.platform.api.ApiSpecService.ApiSpecInUseException;
import com.example.poc.platform.agent.scenario.ScenarioCommandException;
import com.example.poc.platform.agent.scenario.ScenarioDraftConflictException;
import com.example.poc.platform.agent.AgentBudgetExceededException;
import com.example.poc.platform.agent.AgentTaskConflictException;
import com.example.poc.platform.agent.runtime.AgentRuntimeException;
import com.example.poc.platform.catalog.PlatformCatalogService.ResourceInUseException;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleForbidden(SecurityException exception) {
        return Map.of(
                "error", "FORBIDDEN",
                "message", exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(NoSuchElementException exception) {
        return Map.of(
                "error", "NOT_FOUND",
                "message", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(Exception exception) {
        return Map.of(
                "error", "BAD_REQUEST",
                "message", exception.getMessage());
    }

    @ExceptionHandler(ScenarioCommandException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleScenarioCommand(ScenarioCommandException exception) {
        return Map.of(
                "error", exception.getCode(),
                "message", exception.getMessage(),
                "commandIndex", exception.getCommandIndex(),
                "path", exception.getPath());
    }

    @ExceptionHandler(ScenarioDraftConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleScenarioDraftConflict(ScenarioDraftConflictException exception) {
        return Map.of(
                "error", "SCENARIO_DRAFT_CONFLICT",
                "reason", exception.getReason(),
                "message", exception.getMessage(),
                "expectedRevision", exception.getExpectedRevision(),
                "actualRevision", exception.getActualRevision());
    }

    @ExceptionHandler(AgentTaskConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleAgentTaskConflict(AgentTaskConflictException exception) {
        return Map.of(
                "error", "AGENT_TASK_CONFLICT",
                "reason", exception.getReason(),
                "message", exception.getMessage());
    }

    @ExceptionHandler(AgentBudgetExceededException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public Map<String, Object> handleAgentBudgetExceeded(AgentBudgetExceededException exception) {
        return Map.of(
                "error", "AGENT_BUDGET_EXCEEDED",
                "message", exception.getMessage());
    }

    @ExceptionHandler(AgentRuntimeException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleAgentRuntime(AgentRuntimeException exception) {
        return Map.of(
                "error", exception.getCode(),
                "message", exception.getMessage(),
                "retryable", exception.isRetryable());
    }

    @ExceptionHandler(ApiSpecInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleApiSpecInUse(ApiSpecInUseException exception) {
        return Map.of(
                "error", "API_SPEC_IN_USE",
                "message", exception.getMessage(),
                "references", exception.getReferences());
    }

    @ExceptionHandler(ResourceInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleResourceInUse(ResourceInUseException exception) {
        return Map.of(
                "error", "RESOURCE_IN_USE",
                "message", exception.getMessage(),
                "references", exception.getReferences());
    }
}
