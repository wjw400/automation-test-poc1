package com.example.poc.platform.pipeline;

import com.example.poc.engine.CaseActionExecutor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Read-only catalog of processing pipelines bundled with the application.
 *
 * <p>The catalog deliberately loads only classpath resources. Pipeline YAML may compose known
 * platform actions but cannot name classes, inject scripts, or load external implementations.</p>
 */
@Service
public class BuiltinPipelineCatalogService {
    private static final String RESOURCE_PATTERN = "classpath*:META-INF/platform/pipelines/*.yaml";
    private static final String API_VERSION = "platform.example.com/v1";
    private static final Pattern PIPELINE_ID = Pattern.compile("[a-z][a-z0-9-]*(/[a-z][a-z0-9-]*)+");
    private static final Set<String> KNOWN_OPERATORS = Set.of(
            CaseActionExecutor.SIGN_AND_ENCRYPT + "@1",
            CaseActionExecutor.DECRYPT_AND_VERIFY + "@1",
            CaseActionExecutor.BUILD_JSON + "@1",
            CaseActionExecutor.ENCODE_BASE64 + "@1");

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, PipelineCatalogRecord> byReference = new LinkedHashMap<>();
    private final Map<String, String> sourceByReference = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(RESOURCE_PATTERN);
            for (Resource resource : resources) {
                PipelineCatalogRecord pipeline = parse(resource);
                PipelineCatalogRecord duplicate = byReference.putIfAbsent(pipeline.reference(), pipeline);
                if (duplicate != null) {
                    throw new IllegalStateException("Duplicate built-in pipeline " + pipeline.reference()
                            + " declared by " + sourceByReference.get(pipeline.reference())
                            + " and " + resource.getDescription());
                }
                sourceByReference.put(pipeline.reference(), resource.getDescription());
            }
            if (byReference.isEmpty()) {
                throw new IllegalStateException("No built-in processing pipelines were found");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the built-in pipeline catalog", exception);
        }
    }

    public List<PipelineCatalogRecord> list() {
        return List.copyOf(byReference.values());
    }

    public Optional<PipelineCatalogRecord> find(String reference) {
        return Optional.ofNullable(byReference.get(reference));
    }

    private PipelineCatalogRecord parse(Resource resource) throws IOException {
        Map<String, Object> root;
        try (InputStream input = resource.getInputStream()) {
            root = yamlMapper.readValue(input, new TypeReference<>() { });
        }
        String source = resource.getDescription();
        require(API_VERSION.equals(text(root.get("apiVersion"))), source + " must declare apiVersion " + API_VERSION);
        require("Pipeline".equals(text(root.get("kind"))), source + " must declare kind Pipeline");

        Map<String, Object> metadata = object(root.get("metadata"));
        String id = text(metadata.get("id"));
        int version = number(metadata.get("version"), 0);
        require(id != null && PIPELINE_ID.matcher(id).matches(), source + " has an invalid metadata.id");
        require(version > 0, source + " must declare a positive metadata.version");

        Map<String, Object> spec = object(root.get("spec"));
        Map<String, PipelinePortRecord> inputs = ports(spec.get("inputs"), source, "inputs");
        Map<String, PipelinePortRecord> outputs = ports(spec.get("outputs"), source, "outputs");
        Map<String, PipelineParameterRecord> parameters = parameters(spec.get("parameters"), source);
        List<PipelineStepRecord> steps = steps(spec.get("steps"), source);
        List<PipelineFlowEdgeRecord> flow = flow(spec.get("flow"), source, inputs, outputs, parameters, steps);
        require(!inputs.isEmpty(), source + " must declare at least one input");
        require(!outputs.isEmpty(), source + " must declare at least one output");
        require(!steps.isEmpty(), source + " must declare at least one step");
        require(!flow.isEmpty(), source + " must declare its parameter and data flow");

        return new PipelineCatalogRecord(
                id,
                version,
                text(metadata.get("displayName"), id),
                text(metadata.get("description")),
                text(spec.get("direction"), "REQUEST"),
                bool(spec.get("requiresRuntimeProfile")),
                Map.copyOf(inputs),
                Map.copyOf(outputs),
                Map.copyOf(parameters),
                List.copyOf(steps),
                List.copyOf(flow));
    }

    private Map<String, PipelinePortRecord> ports(Object value, String source, String kind) {
        Map<String, PipelinePortRecord> result = new LinkedHashMap<>();
        object(value).forEach((name, rawPort) -> {
            Map<String, Object> port = object(rawPort);
            String type = text(port.get("type"));
            require(type != null, source + " " + kind + "." + name + " requires a type");
            result.put(name, new PipelinePortRecord(type, text(port.get("defaultLocation")), text(port.get("description"))));
        });
        return result;
    }

    private Map<String, PipelineParameterRecord> parameters(Object value, String source) {
        Map<String, PipelineParameterRecord> result = new LinkedHashMap<>();
        object(value).forEach((name, rawParameter) -> {
            Map<String, Object> parameter = object(rawParameter);
            String type = text(parameter.get("type"));
            require(type != null, source + " parameters." + name + " requires a type");
            List<Object> values = list(parameter.get("enum"));
            Object defaultValue = parameter.get("default");
            if (!values.isEmpty() && defaultValue != null) {
                require(values.contains(defaultValue), source + " parameters." + name + " default must be in enum");
            }
            result.put(name, new PipelineParameterRecord(type, defaultValue, List.copyOf(values), text(parameter.get("description"))));
        });
        return result;
    }

    private List<PipelineStepRecord> steps(Object value, String source) {
        List<PipelineStepRecord> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (Object rawStep : list(value)) {
            Map<String, Object> step = object(rawStep);
            String id = text(step.get("id"));
            String uses = text(step.get("uses"));
            require(id != null && ids.add(id), source + " steps require unique ids");
            require(uses != null && KNOWN_OPERATORS.contains(uses), source + " uses an unsupported operator: " + uses);
            result.add(new PipelineStepRecord(id, uses, text(step.get("displayName"), uses), text(step.get("description"))));
        }
        return result;
    }

    private List<PipelineFlowEdgeRecord> flow(
            Object value,
            String source,
            Map<String, PipelinePortRecord> inputs,
            Map<String, PipelinePortRecord> outputs,
            Map<String, PipelineParameterRecord> parameters,
            List<PipelineStepRecord> steps) {
        List<PipelineFlowEdgeRecord> result = new ArrayList<>();
        Map<String, Integer> stepOrder = new LinkedHashMap<>();
        for (int index = 0; index < steps.size(); index++) {
            stepOrder.put(steps.get(index).id(), index);
        }
        Set<String> targets = new LinkedHashSet<>();
        for (Object rawEdge : list(value)) {
            Map<String, Object> edge = object(rawEdge);
            String from = text(edge.get("from"));
            String to = text(edge.get("to"));
            require(from != null && to != null, source + " flow entries require from and to");
            FlowEndpoint fromEndpoint = flowEndpoint(from, source);
            FlowEndpoint toEndpoint = flowEndpoint(to, source);
            require(fromEndpoint.readable(), source + " flow source is not readable: " + from);
            require(toEndpoint.writable(), source + " flow target is not writable: " + to);
            validateFlowReference(fromEndpoint, source, inputs, outputs, parameters, stepOrder);
            validateFlowReference(toEndpoint, source, inputs, outputs, parameters, stepOrder);
            require(targets.add(to), source + " flow target is bound more than once: " + to);
            if ("steps".equals(fromEndpoint.group()) && "steps".equals(toEndpoint.group())) {
                require(stepOrder.get(fromEndpoint.name()) < stepOrder.get(toEndpoint.name()),
                        source + " flow must follow declared step order: " + from + " -> " + to);
            }
            result.add(new PipelineFlowEdgeRecord(from, to));
        }
        inputs.keySet().forEach(name -> requireFlowSource(result, "inputs." + name, source));
        parameters.keySet().forEach(name -> requireFlowSource(result, "parameters." + name, source));
        outputs.keySet().forEach(name -> requireFlowTarget(result, "outputs." + name, source));
        return result;
    }

    private FlowEndpoint flowEndpoint(String reference, String source) {
        String[] parts = reference.split("\\.");
        require(parts.length == 2 || parts.length == 3, source + " has an invalid flow endpoint: " + reference);
        String group = parts[0];
        if ("steps".equals(group)) {
            require(parts.length == 3, source + " step flow endpoint requires a port: " + reference);
            return new FlowEndpoint(group, parts[1], parts[2]);
        }
        require(parts.length == 2, source + " top-level flow endpoint is invalid: " + reference);
        require(Set.of("inputs", "outputs", "parameters").contains(group),
                source + " has an unknown flow endpoint group: " + group);
        return new FlowEndpoint(group, parts[1], null);
    }

    private void validateFlowReference(
            FlowEndpoint endpoint,
            String source,
            Map<String, PipelinePortRecord> inputs,
            Map<String, PipelinePortRecord> outputs,
            Map<String, PipelineParameterRecord> parameters,
            Map<String, Integer> stepOrder) {
        boolean known = switch (endpoint.group()) {
            case "inputs" -> inputs.containsKey(endpoint.name());
            case "outputs" -> outputs.containsKey(endpoint.name());
            case "parameters" -> parameters.containsKey(endpoint.name());
            case "steps" -> stepOrder.containsKey(endpoint.name());
            default -> false;
        };
        require(known, source + " flow references an unknown endpoint: " + endpoint.reference());
    }

    private void requireFlowSource(List<PipelineFlowEdgeRecord> flow, String reference, String source) {
        require(flow.stream().anyMatch(edge -> reference.equals(edge.from())),
                source + " does not show how " + reference + " is used");
    }

    private void requireFlowTarget(List<PipelineFlowEdgeRecord> flow, String reference, String source) {
        require(flow.stream().anyMatch(edge -> reference.equals(edge.to())),
                source + " does not show how " + reference + " is produced");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    private static List<Object> list(Object value) {
        return value instanceof Collection<?> collection ? new ArrayList<>(collection) : List.of();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String text(Object value, String fallback) {
        String text = text(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    public record PipelineCatalogRecord(
            String id,
            int version,
            String displayName,
            String description,
            String direction,
            boolean requiresRuntimeProfile,
            Map<String, PipelinePortRecord> inputs,
            Map<String, PipelinePortRecord> outputs,
            Map<String, PipelineParameterRecord> parameters,
            List<PipelineStepRecord> steps,
            List<PipelineFlowEdgeRecord> flow) {
        @JsonProperty
        public String reference() {
            return id + "@" + version;
        }
    }

    public record PipelinePortRecord(String type, String defaultLocation, String description) { }

    public record PipelineParameterRecord(String type, Object defaultValue, List<Object> enumValues, String description) { }

    public record PipelineStepRecord(String id, String uses, String displayName, String description) { }

    public record PipelineFlowEdgeRecord(String from, String to) { }

    private record FlowEndpoint(String group, String name, String port) {
        boolean readable() {
            return !"outputs".equals(group);
        }

        boolean writable() {
            return "steps".equals(group) || "outputs".equals(group);
        }

        String reference() {
            return port == null ? group + "." + name : group + "." + name + "." + port;
        }
    }
}
