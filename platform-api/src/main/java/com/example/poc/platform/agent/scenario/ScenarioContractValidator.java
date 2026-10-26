package com.example.poc.platform.agent.scenario;

import static com.example.poc.platform.openapi.OperationAuthoringContractService.object;

import com.example.poc.engine.ApiStep;
import com.example.poc.engine.CaseActionExecutor;
import com.example.poc.engine.CaseDslDataSources;
import com.example.poc.engine.ExtractValueSpec;
import com.example.poc.platform.agent.scenario.ScenarioValidationResult.Severity;
import com.example.poc.platform.agent.scenario.ScenarioValidationResult.ValidationIssue;
import com.example.poc.platform.openapi.OperationAuthoringContractService;
import com.example.poc.platform.openapi.OperationAuthoringContractService.Contract;
import com.example.poc.platform.openapi.OperationAuthoringContractService.Response;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Static authoring checks, not a runtime or complete JSON Schema validator. */
@Component
public class ScenarioContractValidator {
    private static final Object UNKNOWN_BODY = new Object();
    private static final Pattern PATH_TOKEN = Pattern.compile("\\.([A-Za-z_$][\\w$-]*)|\\['([^']+)'\\]|\\[\"([^\"]+)\"\\]|\\[(\\d+|\\*)\\]");
    private final OperationAuthoringContractService contracts;
    private final ScenarioDslCodec codec;

    public ScenarioContractValidator(OperationAuthoringContractService contracts, ScenarioDslCodec codec) {
        this.contracts = contracts;
        this.codec = codec;
    }

    public void validate(ApiOperationEntity operation, ApiStep step, String path, List<ValidationIssue> issues) {
        Contract contract = contracts.describe(operation);
        if (contract == null) return;
        if (!contract.diagnostics().isEmpty()) issue(issues, Severity.WARNING, "API_SCHEMA_PARTIALLY_RESOLVED", path + "/operation",
                String.join("; ", contract.diagnostics()));
        boolean automaticRequest = "automatic".equals(step.getProcessingMode());
        if (step.getRequest() != null) {
            Object body = authoredBody(step, !automaticRequest);
            Map<String, Object> schema = automaticRequest ? contract.requestSchema() : contract.wireRequestSchema();
            boolean negative = expectsOnlyErrors(step);
            if (body != UNKNOWN_BODY && (body != null || contract.requestRequired()))
                validateValue(body, schema, path + "/request/body", negative ? Severity.WARNING : Severity.ERROR, issues);
        }
        if (step.getResponse() == null || step.getResponse().getStatus() == null) return;
        Set<Response> candidates = expectedResponses(contract, step);
        for (int index = 0; index < step.getResponse().getAssertions().size(); index++) {
            var assertion = step.getResponse().getAssertions().get(index);
            if ("notExists".equalsIgnoreCase(assertion.getOperator())) continue;
            validateResponsePath(step, candidates, assertion.getSource(), assertion.getPath(),
                    path + "/response/assertions/" + index, issues);
        }
        step.getResponse().getExtract().forEach((name, value) -> {
            // Structural validation reports malformed extraction definitions separately.
            try {
                var extraction = codec.convert(value, ExtractValueSpec.class, path);
                validateResponsePath(step, candidates, extraction.getSource(), extraction.getPath(),
                        path + "/response/extract/" + pointer(name), issues);
            } catch (ScenarioCommandException ignored) { }
        });
    }

    private Object authoredBody(ApiStep step, boolean includeWireActions) {
        Object body = step.getRequest().getBody();
        var actions = new ArrayList<>(step.getActions().getBeforeSend());
        if (includeWireActions) actions.addAll(step.getActions().getAfterRequestProcessing());
        for (var action : actions) {
            if (!action.isEnabled() || !"request.body".equals(action.getOutput().getTarget())) continue;
            body = CaseActionExecutor.BUILD_JSON.equals(action.getType()) ? action.getTemplate() : UNKNOWN_BODY;
        }
        return body;
    }

    private boolean expectsOnlyErrors(ApiStep step) {
        if (step.getResponse() == null || step.getResponse().getStatus() == null) return false;
        try {
            step.getResponse().getStatus().validate();
            for (int status = 100; status < 400; status++) if (step.getResponse().getStatus().matches(status)) return false;
            return true;
        } catch (IllegalArgumentException ignored) { return false; }
    }

    private Set<Response> expectedResponses(Contract contract, ApiStep step) {
        Set<Response> result = new LinkedHashSet<>();
        try {
            step.getResponse().getStatus().validate();
            for (int status = 100; status < 600; status++) {
                if (step.getResponse().getStatus().matches(status)) {
                    Response response = contract.response(status);
                    if (response != null) result.add(response);
                }
            }
        } catch (IllegalArgumentException ignored) { }
        return result;
    }

    private void validateResponsePath(ApiStep step, Set<Response> candidates, String source, String jsonPath,
                                      String path, List<ValidationIssue> issues) {
        if (source == null || jsonPath == null || candidates.isEmpty()) return;
        boolean business = CaseDslDataSources.BUSINESS_RESPONSE_BODY.equals(source) || "decrypted.business".equals(source);
        boolean raw = CaseDslDataSources.RESPONSE_BODY.equals(source) || CaseDslDataSources.WIRE_RESPONSE_BODY.equals(source);
        if (!business && !raw) return; // A manual named Action may produce an arbitrary shape.
        boolean inherited = !"manual".equals(step.getProcessingMode());
        if (business && step.getActions().getAfterReceive().stream().anyMatch(action -> action.isEnabled()
                && "decrypted.business".equals(action.getOutput().getTarget()))) return;
        boolean useBusiness = business && inherited;
        if (candidates.stream().anyMatch(response -> pathState(useBusiness ? response.schema() : response.wireSchema(), jsonPath) != PathState.MISSING)) return;
        boolean businessPath = raw && inherited && candidates.stream().anyMatch(response -> response.encrypted()
                && pathState(response.schema(), jsonPath) == PathState.DECLARED);
        if (businessPath) {
            issue(issues, Severity.ERROR, "BUSINESS_RESPONSE_SOURCE_REQUIRED", path + "/source",
                    jsonPath + " is a business response field. Use business.response.body; response.body contains the ciphertext envelope.");
        } else {
            Set<String> fields = new LinkedHashSet<>();
            candidates.forEach(response -> fields.addAll(object(shape(useBusiness ? response.schema() : response.wireSchema()).get("properties")).keySet()));
            issue(issues, Severity.ERROR, "RESPONSE_PATH_NOT_DECLARED", path + "/path",
                    "JSONPath " + jsonPath + " is not declared by the selected response schema. Root fields: " + fields
                            + ". Variable names need not match response fields; read the resolved operation.authoring contract.");
        }
    }

    private enum PathState { DECLARED, MISSING, UNKNOWN }
    private record PathPart(String property, boolean array) {}

    private PathState pathState(Map<String, Object> original, String path) {
        if (original.isEmpty() || !path.startsWith("$")) return PathState.UNKNOWN;
        var matcher = PATH_TOKEN.matcher(path);
        List<PathPart> parts = new ArrayList<>();
        int offset = 1;
        while (offset < path.length()) {
            if (!matcher.find(offset) || matcher.start() != offset) return PathState.UNKNOWN;
            String property = matcher.group(1) != null ? matcher.group(1) : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            parts.add(new PathPart(property, matcher.group(4) != null));
            offset = matcher.end();
        }
        Map<String, Object> schema = shape(original);
        for (PathPart part : parts) {
            if (schema.isEmpty() || schema.containsKey("$ref") || schema.containsKey("oneOf") || schema.containsKey("anyOf")) return PathState.UNKNOWN;
            if (part.array()) {
                if (!"array".equals(schema.get("type")) && !schema.containsKey("items")) return PathState.MISSING;
                schema = shape(object(schema.get("items")));
            } else {
                String name = part.property();
                Map<String, Object> properties = object(schema.get("properties"));
                if (!properties.containsKey(name)) {
                    if (schema.get("type") instanceof String type && !"object".equals(type) && properties.isEmpty()) return PathState.MISSING;
                    if (schema.get("additionalProperties") instanceof Map<?, ?>) schema = shape(object(schema.get("additionalProperties")));
                    else if (schema.containsKey("patternProperties") || Boolean.TRUE.equals(schema.get("additionalProperties")) || properties.isEmpty() && !Boolean.FALSE.equals(schema.get("additionalProperties"))) return PathState.UNKNOWN;
                    else return PathState.MISSING;
                } else schema = shape(object(properties.get(name)));
            }
        }
        return PathState.DECLARED;
    }

    private void validateValue(Object value, Map<String, Object> original, String path, Severity severity, List<ValidationIssue> issues) {
        Map<String, Object> schema = shape(original);
        if (schema.isEmpty() || schema.containsKey("$ref") || value instanceof String text && text.contains("${")) return;
        if (schema.containsKey("oneOf") || schema.containsKey("anyOf")) {
            issue(issues, Severity.WARNING, "REQUEST_SCHEMA_VARIANT_NOT_CHECKED", path, "Variant schema requires review; static body validation is partial.");
            return;
        }
        if (value == null && Boolean.TRUE.equals(schema.get("nullable"))) return;
        if (schema.get("type") instanceof String type && !matchesType(value, type)) {
            issue(issues, severity, "REQUEST_BODY_TYPE_MISMATCH", path, "Expected " + type + " according to the request schema");
            return;
        }
        if (schema.get("enum") instanceof List<?> allowed && !allowed.contains(value))
            issue(issues, severity, "REQUEST_BODY_ENUM_MISMATCH", path, "Value must be one of " + allowed);
        if (value instanceof Map<?, ?> body) {
            Map<String, Object> properties = object(schema.get("properties"));
            if (schema.get("required") instanceof List<?> required) for (Object name : required) {
                if (!body.containsKey(name) && !Boolean.TRUE.equals(object(properties.get(name)).get("readOnly")))
                    issue(issues, severity, "REQUEST_BODY_REQUIRED", path + "/" + pointer(String.valueOf(name)), "Missing required request field: " + name);
            }
            body.forEach((key, item) -> {
                String name = String.valueOf(key);
                if (properties.containsKey(name)) validateValue(item, object(properties.get(name)), path + "/" + pointer(name), severity, issues);
                else if (Boolean.FALSE.equals(schema.get("additionalProperties")) && !schema.containsKey("patternProperties"))
                    issue(issues, severity, "REQUEST_BODY_UNKNOWN_FIELD", path + "/" + pointer(name), "Request schema does not allow field: " + name + ". Allowed fields: " + properties.keySet());
                else if (schema.get("additionalProperties") instanceof Map<?, ?>)
                    validateValue(item, object(schema.get("additionalProperties")), path + "/" + pointer(name), severity, issues);
            });
        } else if (value instanceof List<?> values) {
            if (schema.get("minItems") instanceof Number min && values.size() < min.intValue())
                issue(issues, severity, "REQUEST_BODY_MIN_ITEMS", path, "Expected at least " + min + " array items");
            if (schema.get("maxItems") instanceof Number max && values.size() > max.intValue())
                issue(issues, severity, "REQUEST_BODY_MAX_ITEMS", path, "Expected at most " + max + " array items");
            for (int index = 0; index < values.size(); index++) validateValue(values.get(index), object(schema.get("items")), path + "/" + index, severity, issues);
        } else if (value instanceof Number number) {
            if (schema.get("minimum") instanceof Number min && number.doubleValue() < min.doubleValue())
                issue(issues, severity, "REQUEST_BODY_MINIMUM", path, "Expected a value at least " + min);
            if (schema.get("maximum") instanceof Number max && number.doubleValue() > max.doubleValue())
                issue(issues, severity, "REQUEST_BODY_MAXIMUM", path, "Expected a value at most " + max);
        } else if (value instanceof String text) {
            if (schema.get("minLength") instanceof Number min && text.codePointCount(0, text.length()) < min.intValue())
                issue(issues, severity, "REQUEST_BODY_MIN_LENGTH", path, "Expected a string length at least " + min);
        }
    }

    private boolean matchesType(Object value, String type) {
        return switch (type) {
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue());
            case "boolean" -> value instanceof Boolean;
            case "null" -> value == null;
            default -> true;
        };
    }

    private Map<String, Object> shape(Map<String, Object> schema) {
        if (!(schema.get("allOf") instanceof List<?> parts)) return schema;
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> properties = new LinkedHashMap<>();
        Set<Object> required = new LinkedHashSet<>();
        List<Object> all = new ArrayList<>(parts); all.add(schema);
        for (Object part : all) {
            Map<String, Object> entry = part == schema ? schema : shape(object(part));
            entry.forEach((key, item) -> { if (!"allOf".equals(key)) merged.put(key, item); });
            properties.putAll(object(entry.get("properties")));
            if (entry.get("required") instanceof List<?> names) required.addAll(names);
        }
        if (!properties.isEmpty()) merged.put("properties", properties);
        if (!required.isEmpty()) merged.put("required", List.copyOf(required));
        return merged;
    }

    private void issue(List<ValidationIssue> issues, Severity severity, String code, String path, String message) {
        issues.add(new ValidationIssue(severity, code, path, message));
    }

    private String pointer(String value) { return value.replace("~", "~0").replace("/", "~1"); }
}
