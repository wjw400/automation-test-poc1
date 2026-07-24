package com.example.poc.mockservice.activity;

import java.time.Instant;

public record ApiActivityRecord(
        String id,
        Instant startedAt,
        String channel,
        String operation,
        String method,
        String path,
        String profile,
        String outcome,
        int statusCode,
        long durationMs,
        Object wireRequest,
        Object decryptedRequest,
        Object businessResult,
        Object wireResponse,
        String errorMessage) {
}
