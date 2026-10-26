package com.example.poc.platform.openapi;

import com.example.poc.engine.OperationProcessingSpec;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.repo.ApiSpecRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** The effective, bounded contract used for authoring, rather than just wire-level $refs. */
@Service
public class OperationAuthoringContractService {
    private final ApiSpecRepository specs;
    private final OperationProcessingService processing;
    private final JsonCodec json;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    public OperationAuthoringContractService(ApiSpecRepository specs, OperationProcessingService processing, JsonCodec json) {
        this.specs = specs;
        this.processing = processing;
        this.json = json;
    }

    public Contract describe(ApiOperationEntity operation) {
        if (operation.getApiSpecId() == null) return null;
        var spec = specs.findById(operation.getApiSpecId()).orElseThrow();
        if (!spec.getProjectId().equals(operation.getProjectId())) throw new SecurityException("API Spec belongs to another Project");
        Map<String, Object> document = object(yaml.readValue(spec.getContent(), Object.class));
        Map<String, Object> raw = json.readObjectMap(operation.getRawOperationJson());
        Resolver resolver = new Resolver(document);
        var settings = processing.settings(operation);
        OperationProcessingSpec configuration = settings.configuration();
        var requestRule = configuration == null ? null : configuration.getRequest();
        Map<String, Object> requestBody = resolver.reference(object(raw.get("requestBody")));
        Map<String, Object> wireRequest = resolver.schema(contentSchema(requestBody));
        boolean encryptedRequest = requestRule != null && !"none".equals(requestRule.getProtection());
        Map<String, Object> request = requestRule != null && requestRule.getBusinessSchema() != null
                ? resolver.schema(object(requestRule.getBusinessSchema())) : encryptedRequest ? Map.of() : wireRequest;

        Map<String, Object> rawResponses = object(raw.get("responses"));
        Set<String> statuses = new LinkedHashSet<>(rawResponses.keySet());
        if (configuration != null) statuses.addAll(configuration.getResponses().keySet());
        Map<String, Response> responses = new LinkedHashMap<>();
        for (String status : statuses) {
            var rule = configuration == null ? null : select(configuration.getResponses(), status);
            boolean encrypted = rule != null && !"none".equals(rule.getProtection());
            Map<String, Object> wire = resolver.schema(contentSchema(resolver.reference(object(select(rawResponses, status)))));
            Map<String, Object> business = rule != null && rule.getBusinessSchema() != null
                    ? resolver.schema(object(rule.getBusinessSchema())) : encrypted ? Map.of() : wire;
            responses.put(status, new Response(business, wire, encrypted));
        }
        return new Contract(request, wireRequest, Boolean.TRUE.equals(requestBody.get("required")), encryptedRequest,
                Map.copyOf(responses), List.copyOf(resolver.diagnostics));
    }

    public record Response(Map<String, Object> schema, Map<String, Object> wireSchema, boolean encrypted) {}

    public record Contract(Map<String, Object> requestSchema, Map<String, Object> wireRequestSchema,
                           boolean requestRequired, boolean encryptedRequest, Map<String, Response> responses, List<String> diagnostics) {
        public Response response(int status) { return select(responses, String.valueOf(status)); }

        public Map<String, Object> agentView() {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("bodyKind", encryptedRequest ? "BUSINESS_JSON_BEFORE_ENCRYPTION" : "JSON_REQUEST");
            request.put("required", requestRequired);
            request.put("schema", requestSchema);
            request.put("example", schemaExample(requestSchema));
            request.put("wireSchema", wireRequestSchema);
            result.put("request", request);
            Map<String, Object> responseView = new LinkedHashMap<>();
            responses.forEach((status, response) -> responseView.put(status, Map.of(
                    "source", "business.response.body", "schema", response.schema(),
                    "wireSource", "wire.response.body", "wireSchema", response.wireSchema(),
                    "declaredRootFields", object(response.schema().get("properties")).keySet())));
            result.put("responses", responseView);
            result.put("diagnostics", diagnostics);
            result.put("guidance", "For automatic processing, author request.body from request.schema, not wireSchema. "
                    + "Use responses[status].source and its schema for business assertions/extraction. "
                    + "JSONPath is relative to that source; an extraction named orderId can read $.id. "
                    + "Never invent undocumented request fields or assume the variable name is the response field. "
                    + "Examples come from declared schema examples/defaults; they do not prove current resource availability. "
                    + "If an encrypted business schema is empty or a $ref remains unresolved, read a narrower API fragment or ask for the missing contract instead of guessing.");
            return result;
        }
    }

    private static Object schemaExample(Map<String, Object> schema) {
        if (schema.containsKey("example")) return schema.get("example");
        if (schema.containsKey("default")) return schema.get("default");
        Map<String, Object> properties = object(schema.get("properties"));
        if (!properties.isEmpty()) {
            Map<String, Object> example = new LinkedHashMap<>();
            properties.forEach((name, property) -> {
                Object value = schemaExample(object(property));
                if (value != null) example.put(name, value);
            });
            return example.isEmpty() ? null : example;
        }
        if ("array".equals(schema.get("type"))) {
            Object item = schemaExample(object(schema.get("items")));
            if (item != null) return List.of(item);
        }
        return null;
    }

    private static Map<String, Object> contentSchema(Map<String, Object> body) {
        Map<String, Object> content = object(body.get("content"));
        Object json = content.get("application/json");
        if (json == null) json = content.entrySet().stream().filter(entry -> entry.getKey().endsWith("+json"))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        return object(object(json).get("schema"));
    }

    private static <T> T select(Map<String, T> rules, String status) {
        if (rules.containsKey(status)) return rules.get(status);
        if (status.matches("[1-5][0-9]{2}")) {
            String range = status.substring(0, 1) + "XX";
            for (var entry : rules.entrySet()) if (range.equalsIgnoreCase(entry.getKey())) return entry.getValue();
        }
        return rules.get("default");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private static final class Resolver {
        private final Map<String, Object> document;
        private final Set<String> diagnostics = new LinkedHashSet<>();
        private int remaining = 4_000;

        private Resolver(Map<String, Object> document) { this.document = document; }

        private Map<String, Object> reference(Map<String, Object> value) {
            Set<String> seen = new LinkedHashSet<>();
            while (value.get("$ref") instanceof String ref && seen.add(ref) && seen.size() <= 24) {
                Map<String, Object> target = resolve(ref);
                if (target.isEmpty()) break;
                value = target;
            }
            return value;
        }

        private Map<String, Object> resolve(String ref) {
            if (!ref.startsWith("#/")) { diagnostics.add("External schema reference is not expanded: " + ref); return Map.of(); }
            Object current = document;
            for (String token : ref.substring(2).split("/", -1)) {
                current = object(current).get(token.replace("~1", "/").replace("~0", "~"));
                if (current == null) { diagnostics.add("Unresolved schema reference: " + ref); return Map.of(); }
            }
            return object(current);
        }

        private Map<String, Object> schema(Map<String, Object> value) { return schema(value, Set.of(), 0); }

        private Map<String, Object> schema(Map<String, Object> value, Set<String> seen, int depth) {
            if (value.isEmpty()) return value;
            if (--remaining < 0 || depth > 24) { diagnostics.add("Schema expansion limit reached; read a narrower API fragment"); return Map.of("$ref", "UNEXPANDED_LIMIT"); }
            if (value.get("$ref") instanceof String ref) {
                if (seen.contains(ref)) { diagnostics.add("Recursive schema reference retained: " + ref); return Map.of("$ref", ref); }
                var target = resolve(ref);
                if (target.isEmpty()) return value;
                Set<String> next = new LinkedHashSet<>(seen); next.add(ref);
                Map<String, Object> merged = new LinkedHashMap<>(target);
                value.forEach((key, item) -> { if (!"$ref".equals(key)) merged.put(key, item); });
                return schema(merged, next, depth + 1);
            }
            Map<String, Object> result = new LinkedHashMap<>(value);
            Map<String, Object> properties = object(value.get("properties"));
            if (!properties.isEmpty()) {
                Map<String, Object> resolved = new LinkedHashMap<>();
                for (var entry : properties.entrySet()) {
                    if (remaining <= 0) {
                        diagnostics.add("Schema expansion limit reached; read a narrower API fragment");
                        return Map.of("$ref", "UNEXPANDED_LIMIT");
                    }
                    resolved.put(entry.getKey(), schema(object(entry.getValue()), seen, depth + 1));
                }
                result.put("properties", resolved);
            }
            for (String key : List.of("items", "additionalProperties", "not")) {
                if (value.get(key) instanceof Map<?, ?>) result.put(key, schema(object(value.get(key)), seen, depth + 1));
            }
            for (String key : List.of("allOf", "oneOf", "anyOf")) {
                if (value.get(key) instanceof List<?> entries) {
                    List<Object> resolved = new ArrayList<>();
                    entries.forEach(item -> resolved.add(schema(object(item), seen, depth + 1)));
                    result.put(key, resolved);
                }
            }
            return result;
        }
    }
}
