package com.example.poc.mockservice.activity;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class ApiActivityService {
    private static final int MAX_RECORDS = 200;

    private final AtomicLong sequence = new AtomicLong();
    private final Deque<ApiActivityRecord> records = new ArrayDeque<>();

    public ApiActivityRecord record(
            Instant startedAt,
            String channel,
            String operation,
            String method,
            String path,
            String profile,
            int statusCode,
            Object wireRequest,
            Object decryptedRequest,
            Object businessResult,
            Object wireResponse,
            String errorMessage) {
        ApiActivityRecord record = new ApiActivityRecord(
                "A" + sequence.incrementAndGet(),
                startedAt,
                channel,
                operation,
                method,
                path,
                profile,
                errorMessage == null ? "SUCCEEDED" : "FAILED",
                statusCode,
                Math.max(0, java.time.Duration.between(startedAt, Instant.now()).toMillis()),
                wireRequest,
                decryptedRequest,
                businessResult,
                wireResponse,
                errorMessage);
        synchronized (records) {
            records.addFirst(record);
            while (records.size() > MAX_RECORDS) {
                records.removeLast();
            }
        }
        return record;
    }

    public List<Map<String, Object>> listSummaries() {
        synchronized (records) {
            return new ArrayList<>(records).stream().map(this::summary).toList();
        }
    }

    public Optional<ApiActivityRecord> find(String id) {
        synchronized (records) {
            return records.stream().filter(record -> record.id().equals(id)).findFirst();
        }
    }

    public void clear() {
        synchronized (records) {
            records.clear();
        }
    }

    private Map<String, Object> summary(ApiActivityRecord record) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", record.id());
        result.put("startedAt", record.startedAt());
        result.put("channel", record.channel());
        result.put("operation", record.operation());
        result.put("method", record.method());
        result.put("path", record.path());
        result.put("profile", record.profile());
        result.put("outcome", record.outcome());
        result.put("statusCode", record.statusCode());
        result.put("durationMs", record.durationMs());
        result.put("errorMessage", record.errorMessage());
        return result;
    }
}
