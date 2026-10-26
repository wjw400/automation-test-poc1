package com.example.poc.platform.openapi;

import com.example.poc.engine.OperationProcessingSpec;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class OperationProcessingService {
    private final ApiOperationRepository operations;
    private final JsonCodec json;
    private final ObjectMapper mapper;
    public OperationProcessingService(ApiOperationRepository operations, JsonCodec json, ObjectMapper mapper) {
        this.operations = operations; this.json = json; this.mapper = mapper;
    }

    public ApiOperationEntity requireOperation(String projectId, String operationId) {
        ApiOperationEntity operation = operations.findById(operationId).orElseThrow(() -> new NoSuchElementException("API operation not found: " + operationId));
        if (!projectId.equals(operation.getProjectId())) throw new IllegalArgumentException("API operation belongs to another project");
        return operation;
    }

    public Settings get(String projectId, String operationId) { return settings(requireOperation(projectId, operationId)); }

    public Settings settings(ApiOperationEntity operation) {
        Imported imported = imported(operation);
        OperationProcessingSpec effective = operation.getProcessingJson() == null ? imported.configuration() : decode(operation.getProcessingJson());
        return new Settings(effective, imported.configuration(), operation.getProcessingSource() == null ? "IMPORTED" : operation.getProcessingSource(),
                operation.getProcessingRevision(), operation.isProcessingNeedsReview(), imported.error());
    }

    @Transactional
    public Settings save(String projectId, String operationId, SaveRequest request) {
        ApiOperationEntity operation = requireOperation(projectId, operationId);
        checkRevision(operation, request.revision());
        if (request.configuration() == null) throw new IllegalArgumentException("Processing configuration is required; use protection none to disable it");
        request.configuration().validate();
        operation.setProcessingJson(json.write(request.configuration()));
        operation.setProcessingSource("MANUAL");
        operation.setProcessingRevision(operation.getProcessingRevision() + 1);
        operation.setProcessingNeedsReview(false);
        operations.saveAndFlush(operation);
        return settings(operation);
    }

    @Transactional
    public Settings useImported(String projectId, String operationId, long revision) {
        ApiOperationEntity operation = requireOperation(projectId, operationId);
        checkRevision(operation, revision);
        Imported imported = imported(operation);
        if (imported.error() != null) throw new IllegalArgumentException(imported.error());
        operation.setProcessingJson(null);
        operation.setProcessingSource("IMPORTED");
        operation.setProcessingRevision(operation.getProcessingRevision() + 1);
        operation.setProcessingNeedsReview(false);
        operations.saveAndFlush(operation);
        return settings(operation);
    }

    private void checkRevision(ApiOperationEntity operation, long revision) {
        if (operation.getProcessingRevision() != revision) throw new ConfigurationConflictException("Processing configuration changed; reload before saving");
    }

    public void applyImport(ApiOperationEntity operation, Map<String, Object> raw, boolean changed) {
        boolean previouslyConfigured = operation.getImportedProcessingJson() != null || operation.getProcessingJson() != null
                || operation.getProcessingImportError() != null || normalize(json.readObjectMap(operation.getRawOperationJson())).configuration() != null;
        Imported incoming = normalize(raw);
        operation.setImportedProcessingJson(json.write(incoming.configuration()));
        operation.setProcessingImportError(incoming.error());
        if (operation.getProcessingSource() == null) operation.setProcessingSource("IMPORTED");
        if (changed && (previouslyConfigured || incoming.configuration() != null || incoming.error() != null)) operation.setProcessingNeedsReview(true);
        operation.setProcessingRevision(operation.getProcessingRevision() + 1);
    }

    private Imported imported(ApiOperationEntity operation) {
        if (operation.getImportedProcessingJson() != null || operation.getProcessingImportError() != null) {
            return new Imported(decode(operation.getImportedProcessingJson()), operation.getProcessingImportError());
        }
        return normalize(json.readObjectMap(operation.getRawOperationJson()));
    }

    public Imported normalize(Map<String, Object> raw) {
        Object extension = raw.get("x-platform-processing");
        if (extension == null) return new Imported(null, null);
        try {
            Map<String, Object> value = object(extension);
            checkKeys(value, java.util.Set.of("version", "request", "response", "responses"));
            if (value.isEmpty()) throw new IllegalArgumentException("x-platform-processing must be a non-empty object");
            if (value.containsKey("response") && value.containsKey("responses")) throw new IllegalArgumentException("Use only responses, not both response and responses");
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("version", value.getOrDefault("version", 1));
            canonical.put("request", normalizeRule(object(value.get("request")), true));
            Map<String, Object> responses = new LinkedHashMap<>();
            object(value.getOrDefault("responses", value.get("response"))).forEach((status, rule) -> responses.put(status, normalizeRule(object(rule), false)));
            canonical.put("responses", responses);
            OperationProcessingSpec configuration = mapper.convertValue(canonical, OperationProcessingSpec.class);
            configuration.validate();
            return new Imported(configuration, null);
        } catch (RuntimeException error) {
            return new Imported(null, "Invalid x-platform-processing: " + error.getMessage());
        }
    }

    private Map<String, Object> normalizeRule(Map<String, Object> rule, boolean request) {
        if (rule.containsKey("pipeline")) {
            checkKeys(rule, java.util.Set.of("pipeline", "options", "businessSchema"));
            String expected = request ? "system/pgp-json-envelope-request@1" : "system/pgp-json-envelope-response@1";
            if (!expected.equals(rule.get("pipeline"))) throw new IllegalArgumentException("Unsupported processing pipeline: " + rule.get("pipeline"));
            Map<String, Object> options = object(rule.get("options"));
            checkKeys(options, java.util.Set.of("inputs", "outputs", "parameters"));
            Map<String, Object> parameters = object(options.get("parameters"));
            checkKeys(parameters, request ? java.util.Set.of("armored", "envelopePointer") : java.util.Set.of("payloadPointer"));
            if (Boolean.FALSE.equals(parameters.get("armored"))) throw new IllegalArgumentException("Only ASCII-armored PGP is supported");
            if (!object(options.get("inputs")).isEmpty() || !object(options.get("outputs")).isEmpty()) throw new IllegalArgumentException("Migrate pipeline port overrides to explicit ciphertext locations");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("protection", request ? "pgp-sign-encrypt" : "pgp-decrypt-verify");
            result.put("ciphertext", Map.of("location", "json-pointer", "pointer", parameters.getOrDefault(request ? "envelopePointer" : "payloadPointer", "/payload")));
            if (rule.containsKey("businessSchema")) result.put("businessSchema", rule.get("businessSchema"));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>(rule);
        checkKeys(result, java.util.Set.of("protection", "ciphertext", "businessSchema", "envelope"));
        if (!result.containsKey("protection")) result.put("protection", "none");
        if (result.containsKey("ciphertext")) {
            Map<String, Object> location = new LinkedHashMap<>(object(result.get("ciphertext")));
            checkKeys(location, java.util.Set.of("location", "pointer", "field"));
            if ("json-field".equals(location.get("location"))) {
                Object field = location.remove("field");
                if (!(field instanceof String text) || text.isEmpty()) throw new IllegalArgumentException("Ciphertext field is required");
                location.put("location", "json-pointer");
                location.put("pointer", "/" + text.replace("~", "~0").replace("/", "~1"));
            }
            result.put("ciphertext", location);
        }
        return result;
    }

    private OperationProcessingSpec decode(String value) { return json.read(value, new TypeReference<OperationProcessingSpec>() {}, null); }
    private static void checkKeys(Map<String, Object> value, java.util.Set<String> allowed) {
        for (String key : value.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unsupported processing property: " + key);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected an object");
        return (Map<String, Object>) value;
    }

    public record Imported(OperationProcessingSpec configuration, String error) {}
    public record Settings(OperationProcessingSpec configuration, OperationProcessingSpec importedConfiguration, String source,
                           long revision, boolean needsReview, String importError) {}
    public record SaveRequest(long revision, OperationProcessingSpec configuration) {}
    public static class ConfigurationConflictException extends RuntimeException {
        public ConfigurationConflictException(String message) { super(message); }
    }
}
