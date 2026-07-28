package com.example.poc.platform.web;

import com.example.poc.platform.api.ApiSpecService.ApiSpecInUseException;
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
