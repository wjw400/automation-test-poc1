package com.example.poc.platform.agent.capability;

import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.context.AgentContextRedactor;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.ApiSpecEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.ApiSpecRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
public class AgentApiSpecReader {
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MAX_FRAGMENT_CHARACTERS = 16_000;
    private static final Set<String> ALLOWED_ROOTS = Set.of(
            "openapi", "info", "servers", "paths", "components", "security", "tags", "externalDocs");

    private final ApiSpecRepository specRepository;
    private final ApiOperationRepository operationRepository;
    private final AgentToolPolicy policy;
    private final AgentContextRedactor redactor;
    private final JsonCodec jsonCodec;
    private final ObjectMapper yamlMapper = YAMLMapper.builder().build();

    public AgentApiSpecReader(
            ApiSpecRepository specRepository,
            ApiOperationRepository operationRepository,
            AgentToolPolicy policy,
            AgentContextRedactor redactor,
            JsonCodec jsonCodec) {
        this.specRepository = specRepository;
        this.operationRepository = operationRepository;
        this.policy = policy;
        this.redactor = redactor;
        this.jsonCodec = jsonCodec;
    }

    public Map<String, Object> listSpecs(AgentTaskRecord task, Map<String, Object> input) {
        String query = text(input, "query").toLowerCase(Locale.ROOT);
        List<Map<String, Object>> specs = new ArrayList<>();
        for (ApiSpecEntity spec : specRepository.findByProjectIdOrderByUpdatedAtDesc(task.projectId())) {
            if (!query.isBlank() && !searchable(spec.getName(), spec.getTitle(), spec.getSpecVersion()).contains(query)) {
                continue;
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("apiSpecRefId", spec.getId());
            value.put("name", spec.getName());
            value.put("title", spec.getTitle());
            value.put("specVersion", spec.getSpecVersion());
            value.put("operationCount", operationRepository
                    .findByApiSpecIdOrderByMethodAscPathAsc(spec.getId()).size());
            value.put("revision", spec.getUpdatedAt() == null ? "unknown" : spec.getUpdatedAt().toString());
            specs.add(value);
        }
        return Map.of("specs", specs, "count", specs.size());
    }

    public Map<String, Object> searchOperations(AgentTaskRecord task, Map<String, Object> input) {
        String apiSpecRefId = text(input, "apiSpecRefId");
        String query = text(input, "query").toLowerCase(Locale.ROOT);
        String method = text(input, "method").toUpperCase(Locale.ROOT);
        String path = text(input, "path").toLowerCase(Locale.ROOT);
        List<ApiOperationEntity> candidates;
        if (apiSpecRefId.isBlank()) {
            candidates = operationRepository.findByProjectIdOrderByTagNameAscMethodAscPathAsc(task.projectId());
        } else {
            policy.requireProjectApiSpec(task, apiSpecRefId);
            candidates = operationRepository.findByApiSpecIdOrderByMethodAscPathAsc(apiSpecRefId);
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        for (ApiOperationEntity operation : candidates) {
            if (!task.projectId().equals(operation.getProjectId())) continue;
            if (!method.isBlank() && !method.equalsIgnoreCase(operation.getMethod())) continue;
            if (!path.isBlank() && !safe(operation.getPath()).toLowerCase(Locale.ROOT).contains(path)) continue;
            if (!query.isBlank() && !searchable(
                    operation.getOperationId(), operation.getSummary(), operation.getDescription(),
                    operation.getMethod(), operation.getPath(), operation.getTagName()).contains(query)) {
                continue;
            }
            matches.add(operationSummary(operation));
            if (matches.size() >= MAX_SEARCH_RESULTS) break;
        }
        return Map.of(
                "operations", matches,
                "count", matches.size(),
                "truncated", matches.size() == MAX_SEARCH_RESULTS,
                "trustLevel", "UNTRUSTED_API_SPEC_CONTENT");
    }

    public Map<String, Object> getOperation(
            AgentTaskRecord task,
            Map<String, Object> input,
            boolean allowProjectWide) {
        String operationRefId = requiredText(input, "operationRefId");
        ApiOperationEntity operation = allowProjectWide
                ? policy.requireProjectOperation(task, operationRefId)
                : policy.requireLinkedOperation(task, operationRefId);
        ApiSpecEntity spec = policy.requireProjectApiSpec(task, operation.getApiSpecId());
        Map<String, Object> value = operationSummary(operation);
        value.put("apiSpecName", spec.getName());
        value.put("apiSpecVersion", spec.getSpecVersion());
        value.put("description", operation.getDescription());
        value.put("definition", jsonCodec.readObjectMap(operation.getRawOperationJson()));
        value.put("requestBody", jsonCodec.readObjectMap(operation.getRequestBodyJson()));
        value.put("responses", jsonCodec.readObjectMap(operation.getResponsesJson()));
        value.put("dslOperation", dslOperation(operation, spec));
        value.put("trustLevel", "UNTRUSTED_API_SPEC_CONTENT");
        return Map.of("operation", value);
    }

    private Map<String, Object> dslOperation(ApiOperationEntity operation, ApiSpecEntity spec) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", operation.getId());
        value.put("apiSpecId", operation.getApiSpecId());
        value.put("apiSpecName", spec.getName());
        value.put("operationId", operation.getOperationId());
        value.put("summary", operation.getSummary());
        value.put("service", operation.getTagName());
        value.put("method", operation.getMethod());
        value.put("path", operation.getPath());
        return value;
    }

    public Map<String, Object> readFragment(AgentTaskRecord task, Map<String, Object> input) {
        ApiSpecEntity spec = policy.requireProjectApiSpec(task, requiredText(input, "apiSpecRefId"));
        String pointer = requiredText(input, "pointer");
        Object document = parse(spec);
        Object fragment = readPointer(document, pointer);
        Object safeFragment = redactor.redact(fragment);
        String serialized = safeFragment == null ? "null" : jsonCodec.write(safeFragment);
        if (serialized.length() > MAX_FRAGMENT_CHARACTERS) {
            throw new IllegalArgumentException(
                    "API Spec fragment is too large; request a narrower JSON Pointer");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiSpecRefId", spec.getId());
        result.put("name", spec.getName());
        result.put("specVersion", spec.getSpecVersion());
        result.put("revision", spec.getUpdatedAt() == null ? "unknown" : spec.getUpdatedAt().toString());
        result.put("pointer", pointer);
        result.put("fragment", safeFragment);
        result.put("trustLevel", "UNTRUSTED_API_SPEC_CONTENT");
        return result;
    }

    private Map<String, Object> operationSummary(ApiOperationEntity operation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("operationRefId", operation.getId());
        value.put("apiSpecRefId", operation.getApiSpecId());
        value.put("operationId", operation.getOperationId());
        value.put("method", operation.getMethod());
        value.put("path", operation.getPath());
        value.put("tag", operation.getTagName());
        value.put("summary", operation.getSummary());
        return value;
    }

    private Object parse(ApiSpecEntity spec) {
        try {
            return yamlMapper.readValue(spec.getContent(), Object.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Stored API Spec cannot be parsed: " + spec.getName(), exception);
        }
    }

    private Object readPointer(Object document, String pointer) {
        if (!pointer.startsWith("/") || pointer.length() == 1) {
            throw new IllegalArgumentException("pointer must identify a specific API Spec fragment");
        }
        String[] encodedTokens = pointer.substring(1).split("/", -1);
        String root = decodeToken(encodedTokens[0]);
        if (!ALLOWED_ROOTS.contains(root)) {
            throw new SecurityException("Agent Tool denied: API Spec pointer root is not allowed");
        }
        Object current = document;
        for (String encodedToken : encodedTokens) {
            String token = decodeToken(encodedToken);
            if (current instanceof Map<?, ?> map) {
                if (!map.containsKey(token)) throw missing(pointer);
                current = map.get(token);
            } else if (current instanceof List<?> list) {
                int index = parseIndex(token, pointer);
                if (index >= list.size()) throw missing(pointer);
                current = list.get(index);
            } else {
                throw missing(pointer);
            }
        }
        return current;
    }

    private int parseIndex(String token, String pointer) {
        try {
            int index = Integer.parseInt(token);
            if (index < 0) throw missing(pointer);
            return index;
        } catch (NumberFormatException exception) {
            throw missing(pointer);
        }
    }

    private String decodeToken(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    private NoSuchElementException missing(String pointer) {
        return new NoSuchElementException("API Spec fragment not found: " + pointer);
    }

    private String requiredText(Map<String, Object> input, String field) {
        String value = text(input, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private String text(Map<String, Object> input, String field) {
        Object value = input.get(field);
        return value instanceof String text ? text.trim() : "";
    }

    private String searchable(String... values) {
        return String.join("\n", java.util.Arrays.stream(values).map(this::safe).toList())
                .toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
