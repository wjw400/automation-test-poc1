package com.example.poc.platform.web;

import com.example.poc.platform.api.ApiSpecService.ApiSpecInUseException;
import com.example.poc.platform.agent.scenario.ScenarioCommandException;
import com.example.poc.platform.agent.scenario.ScenarioDraftConflictException;
import com.example.poc.platform.agent.AgentBudgetExceededException;
import com.example.poc.platform.agent.AgentTaskConflictException;
import com.example.poc.platform.agent.runtime.AgentRuntimeException;
import com.example.poc.platform.catalog.PlatformCatalogService.ResourceInUseException;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({com.example.poc.platform.openapi.OperationProcessingService.ConfigurationConflictException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleProcessingConflict(Exception exception, HttpServletRequest request) {
        return errorResponse(request, "error", "CONFIGURATION_CONFLICT", "message", "Configuration changed; reload before saving");
    }
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final AgentContextRedactor redactor;

    public ApiExceptionHandler(AgentContextRedactor redactor) {
        this.redactor = redactor;
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleForbidden(SecurityException exception, HttpServletRequest request) {
        logHandled(request, "FORBIDDEN", exception);
        return errorResponse(request,
                "error", "FORBIDDEN",
                "message", exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(NoSuchElementException exception, HttpServletRequest request) {
        logHandled(request, "NOT_FOUND", exception);
        return errorResponse(request,
                "error", "NOT_FOUND",
                "message", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBadRequest(Exception exception, HttpServletRequest request) {
        logHandled(request, "BAD_REQUEST", exception);
        return errorResponse(request,
                "error", "BAD_REQUEST",
                "message", exception.getMessage());
    }

    @ExceptionHandler(ScenarioCommandException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleScenarioCommand(ScenarioCommandException exception, HttpServletRequest request) {
        logHandled(request, exception.getCode(), exception);
        return errorResponse(request,
                "error", exception.getCode(),
                "message", exception.getMessage(),
                "commandIndex", exception.getCommandIndex(),
                "path", exception.getPath());
    }

    @ExceptionHandler(ScenarioDraftConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleScenarioDraftConflict(
            ScenarioDraftConflictException exception,
            HttpServletRequest request) {
        logHandled(request, "SCENARIO_DRAFT_CONFLICT", exception);
        return errorResponse(request,
                "error", "SCENARIO_DRAFT_CONFLICT",
                "reason", exception.getReason(),
                "message", exception.getMessage(),
                "expectedRevision", exception.getExpectedRevision(),
                "actualRevision", exception.getActualRevision());
    }

    @ExceptionHandler(AgentTaskConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleAgentTaskConflict(AgentTaskConflictException exception, HttpServletRequest request) {
        logHandled(request, exception.getReason(), exception);
        return errorResponse(request,
                "error", "AGENT_TASK_CONFLICT",
                "reason", exception.getReason(),
                "message", exception.getMessage());
    }

    @ExceptionHandler(AgentBudgetExceededException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public Map<String, Object> handleAgentBudgetExceeded(
            AgentBudgetExceededException exception,
            HttpServletRequest request) {
        logHandled(request, "AGENT_BUDGET_EXCEEDED", exception);
        return errorResponse(request,
                "error", "AGENT_BUDGET_EXCEEDED",
                "message", exception.getMessage());
    }

    @ExceptionHandler(AgentRuntimeException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, Object> handleAgentRuntime(AgentRuntimeException exception, HttpServletRequest request) {
        logHandled(request, exception.getCode(), exception);
        return errorResponse(request,
                "error", exception.getCode(),
                "message", exception.getMessage(),
                "retryable", exception.isRetryable());
    }

    @ExceptionHandler(ApiSpecInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleApiSpecInUse(ApiSpecInUseException exception, HttpServletRequest request) {
        logHandled(request, "API_SPEC_IN_USE", exception);
        return errorResponse(request,
                "error", "API_SPEC_IN_USE",
                "message", exception.getMessage(),
                "references", exception.getReferences());
    }

    @ExceptionHandler(ResourceInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleResourceInUse(ResourceInUseException exception, HttpServletRequest request) {
        logHandled(request, "RESOURCE_IN_USE", exception);
        return errorResponse(request,
                "error", "RESOURCE_IN_USE",
                "message", exception.getMessage(),
                "references", exception.getReferences());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleDisconnectedAsyncRequest(
            AsyncRequestNotUsableException exception,
            HttpServletRequest request) {
        log.debug(
                "Async client disconnected requestId={} method={} path={} message={}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                safeMessage(exception));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception exception, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString();
        log.error(
                "Unhandled API error errorId={} requestId={} method={} path={} exception={} message={} stack={}",
                errorId,
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getName(),
                safeMessage(exception),
                safeStack(exception));
        return errorResponse(request,
                "error", "INTERNAL_SERVER_ERROR",
                "message", "An unexpected server error occurred",
                "errorId", errorId);
    }

    private void logHandled(HttpServletRequest request, String code, Exception exception) {
        log.warn(
                "API request rejected requestId={} method={} path={} code={} exception={} message={}",
                requestId(request),
                request.getMethod(),
                request.getRequestURI(),
                code,
                exception.getClass().getSimpleName(),
                safeMessage(exception));
    }

    private Map<String, Object> errorResponse(HttpServletRequest request, Object... entries) {
        Map<String, Object> response = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            response.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        response.put("requestId", requestId(request));
        return response;
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? "unknown" : String.valueOf(requestId);
    }

    private String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "<no message>";
        String safe = redactor.redactText(message).replace('\r', ' ').replace('\n', ' ');
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }

    private String safeStack(Throwable error) {
        StringBuilder stack = new StringBuilder();
        Throwable current = error;
        int causes = 0;
        int frames = 0;
        while (current != null && causes++ < 6 && frames < 40) {
            if (!stack.isEmpty()) stack.append(" <- ");
            stack.append(current.getClass().getName()).append(": ").append(safeMessage(current));
            for (StackTraceElement frame : current.getStackTrace()) {
                if (frames++ >= 40) break;
                stack.append("\n  at ").append(frame);
            }
            current = current.getCause();
        }
        return stack.toString();
    }
}
