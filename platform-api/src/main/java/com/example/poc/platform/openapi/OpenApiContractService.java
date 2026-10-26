package com.example.poc.platform.openapi;

import com.example.poc.platform.catalog.PlatformCatalogService;
import com.example.poc.platform.pipeline.BuiltinPipelineCatalogService;
import com.example.poc.platform.pipeline.BuiltinPipelineCatalogService.PipelineCatalogRecord;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.ApiSpecEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.ApiSpecRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Builds the platform's business and wire-level view of an imported OpenAPI spec. */
@Service
public class OpenApiContractService {
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private final PlatformCatalogService catalogService;
    private final BuiltinPipelineCatalogService pipelineCatalogService;
    private final ApiSpecRepository apiSpecRepository;
    private final ApiOperationRepository apiOperationRepository;
    private final OperationProcessingService processingService;
    private final ObjectMapper yamlMapper = YAMLMapper.builder().build();

    public OpenApiContractService(
            PlatformCatalogService catalogService,
            BuiltinPipelineCatalogService pipelineCatalogService,
            ApiSpecRepository apiSpecRepository,
            ApiOperationRepository apiOperationRepository,
            OperationProcessingService processingService) {
        this.catalogService = catalogService;
        this.pipelineCatalogService = pipelineCatalogService;
        this.apiSpecRepository = apiSpecRepository;
        this.apiOperationRepository = apiOperationRepository;
        this.processingService = processingService;
    }

    public ApiSpecContractRecord getSpecContract(String projectId, String apiSpecId) {
        ApiSpecEntity spec = requireSpec(projectId, apiSpecId);
        return parse(spec);
    }

    public OperationContractRecord getOperationContract(String projectId, String operationId) {
        ApiOperationEntity operation = apiOperationRepository.findById(operationId)
                .orElseThrow(() -> new NoSuchElementException("API operation not found: " + operationId));
        if (!projectId.equals(operation.getProjectId())) {
            throw new IllegalArgumentException("API operation does not belong to the selected project");
        }
        return getSpecContract(projectId, operation.getApiSpecId()).operations().stream()
                .filter(contract -> operationId.equals(contract.id()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("API operation contract not found: " + operationId));
    }

    private ApiSpecContractRecord parse(ApiSpecEntity spec) {
        Map<String, Object> document = readDocument(spec.getContent());
        Map<String, Object> schemaRegistry = new LinkedHashMap<>();
        objectAt(document, "components", "schemas").forEach((name, schema) ->
                schemaRegistry.put("#/components/schemas/" + name.replace("~", "~0").replace("/", "~1"), schema));
        Map<String, ApiOperationEntity> entities = new LinkedHashMap<>();
        apiOperationRepository.findByApiSpecIdOrderByMethodAscPathAsc(spec.getId())
                .forEach(entity -> entities.put(key(entity.getMethod(), entity.getPath()), entity));

        List<ContractDiagnosticRecord> diagnostics = new ArrayList<>();
        List<OperationContractRecord> operations = new ArrayList<>();
        for (Map.Entry<String, Object> pathEntry : objectAt(document, "paths").entrySet()) {
            Map<String, Object> pathItem = object(pathEntry.getValue());
            List<Object> pathParameters = list(pathItem.get("parameters"));
            for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
                String method = methodEntry.getKey().toLowerCase(Locale.ROOT);
                if (!METHODS.contains(method)) {
                    continue;
                }
                operations.add(operation(
                        document,
                        entities.get(key(method, pathEntry.getKey())),
                        method,
                        pathEntry.getKey(),
                        object(methodEntry.getValue()),
                        pathParameters,
                        diagnostics));
            }
        }
        return new ApiSpecContractRecord(
                spec.getId(),
                spec.getTitle(),
                spec.getSpecVersion(),
                List.copyOf(operations),
                Map.copyOf(schemaRegistry),
                List.copyOf(diagnostics));
    }

    private OperationContractRecord operation(
            Map<String, Object> document,
            ApiOperationEntity entity,
            String method,
            String path,
            Map<String, Object> raw,
            List<Object> pathParameters,
            List<ContractDiagnosticRecord> specDiagnostics) {
        String location = method.toUpperCase(Locale.ROOT) + " " + path;
        List<ContractDiagnosticRecord> diagnostics = new ArrayList<>();
        Map<String, Object> processing = object(raw.get("x-platform-processing"));
        OperationProcessingService.Settings settings = entity == null ? null : processingService.settings(entity);
        if (settings != null && settings.configuration() != null) {
            Map<String, Object> effective = new LinkedHashMap<>();
            effective.put("request", normalizedRule(settings.configuration().getRequest()));
            Map<String, Object> responseRules = new LinkedHashMap<>();
            settings.configuration().getResponses().forEach((status, rule) -> responseRules.put(status, normalizedRule(rule)));
            effective.put("response", responseRules);
            processing = effective;
        }
        if (settings != null && settings.needsReview()) diagnostics.add(diagnostic("ERROR", "PROCESSING_REVIEW_REQUIRED", "Review processing settings after OpenAPI reimport", location));
        if (settings != null && settings.importError() != null && !"MANUAL".equals(settings.source())) diagnostics.add(diagnostic("ERROR", "INVALID_PROCESSING_EXTENSION", settings.importError(), location));
        List<Object> rawParameters = new ArrayList<>(pathParameters);
        rawParameters.addAll(list(raw.get("parameters")));
        List<ParameterContractRecord> parameters = rawParameters.stream()
                .map(value -> resolve(document, object(value), diagnostics, location + " parameter"))
                .filter(value -> !value.isEmpty())
                .map(value -> new ParameterContractRecord(
                        text(value.get("name")), text(value.get("in")), bool(value.get("required")),
                        value.get("schema"), value.get("example"), text(value.get("description"))))
                .toList();

        Map<String, Object> requestExtension = object(processing.get("request"));
        ContentContractRecord businessRequest = businessSchema(
                document, requestExtension.get("businessSchema"), diagnostics, location + " business request");
        ContentContractRecord wireRequest = requestBody(document, raw.get("requestBody"), diagnostics, location + " wire request");
        ProcessingContractRecord requestProcessing = processing(requestExtension, diagnostics, location + " request processing");

        List<ResponseContractRecord> responses = new ArrayList<>();
        Map<String, Object> responseExtensions = object(processing.get("response"));
        for (Map.Entry<String, Object> entry : object(raw.get("responses")).entrySet()) {
            String status = entry.getKey();
            Map<String, Object> response = resolve(document, object(entry.getValue()), diagnostics, location + " response " + status);
            Map<String, Object> responseExtension = object(responseExtensions.get(status));
            responses.add(new ResponseContractRecord(
                    status,
                    text(response.get("description")),
                    businessSchema(document, responseExtension.get("businessSchema"), diagnostics, location + " business response " + status),
                    content(document, object(response.get("content")), diagnostics, location + " wire response " + status),
                    processing(responseExtension, diagnostics, location + " response processing " + status)));
        }
        specDiagnostics.addAll(diagnostics);
        boolean hasResponseProcessing = responses.stream().anyMatch(response -> response.processing() != null)
                || (settings != null && settings.configuration() != null && settings.configuration().getResponses().values().stream().anyMatch(rule -> !"none".equals(rule.getProtection())));
        boolean hasRequestProcessing = requestProcessing != null
                || (settings != null && settings.configuration() != null && !"none".equals(settings.configuration().getRequest().getProtection()));
        boolean encrypted = (settings != null && settings.configuration() != null && settings.configuration().requiresProfile())
                || (requestProcessing != null && requestProcessing.pipeline() != null && requestProcessing.pipeline().requiresRuntimeProfile())
                || responses.stream().anyMatch(response -> response.processing() != null
                        && response.processing().pipeline() != null
                        && response.processing().pipeline().requiresRuntimeProfile());
        return new OperationContractRecord(
                entity == null ? null : entity.getId(),
                method.toUpperCase(Locale.ROOT), path, text(raw.get("operationId")), text(raw.get("summary")), text(raw.get("description")),
                strings(raw.get("tags")), parameters,
                new PayloadContractRecord(businessRequest, wireRequest, requestProcessing),
                List.copyOf(responses),
                new OperationCapabilitiesRecord(
                        businessRequest != null,
                        responses.stream().anyMatch(response -> response.business() != null),
                        hasRequestProcessing,
                        hasResponseProcessing,
                        encrypted,
                        businessRequest != null && diagnostics.stream().noneMatch(item -> "ERROR".equals(item.severity()))),
                Map.copyOf(raw), List.copyOf(diagnostics), settings);
    }

    private ContentContractRecord businessSchema(
            Map<String, Object> document, Object value,
            List<ContractDiagnosticRecord> diagnostics, String location) {
        return value == null ? null : schema("application/json", value, null, document, diagnostics, location);
    }

    private ContentContractRecord requestBody(Map<String, Object> document, Object value, List<ContractDiagnosticRecord> diagnostics, String location) {
        return content(document, object(resolve(document, object(value), diagnostics, location).get("content")), diagnostics, location);
    }

    private ContentContractRecord content(Map<String, Object> document, Map<String, Object> value, List<ContractDiagnosticRecord> diagnostics, String location) {
        if (value.isEmpty()) {
            return null;
        }
        Map.Entry<String, Object> entry = value.entrySet().iterator().next();
        Map<String, Object> media = object(entry.getValue());
        Object example = media.containsKey("example") ? media.get("example") : example(media.get("examples"));
        return schema(entry.getKey(), media.get("schema"), example, document, diagnostics, location);
    }

    private ContentContractRecord schema(
            String mediaType, Object value, Object example, Map<String, Object> document,
            List<ContractDiagnosticRecord> diagnostics, String location) {
        if (value == null) {
            return new ContentContractRecord(mediaType, null, null, example);
        }
        Map<String, Object> schema = object(value);
        String reference = text(schema.get("$ref"));
        if (reference != null && resolveReference(document, reference).isEmpty()) {
            diagnostics.add(diagnostic("WARNING", "UNRESOLVED_SCHEMA_REF", "Schema reference could not be resolved: " + reference, location));
        }
        return new ContentContractRecord(mediaType, schema, reference, example == null ? schema.get("example") : example);
    }

    private ProcessingContractRecord processing(Map<String, Object> value, List<ContractDiagnosticRecord> diagnostics, String location) {
        if (value.containsKey("protection")) return null;
        if (value.isEmpty()) {
            return null;
        }
        String pipelineReference = text(value.get("pipeline"));
        if (pipelineReference == null) {
            diagnostics.add(diagnostic("ERROR", "MISSING_PIPELINE_REFERENCE", "Processing requires a pipeline reference", location));
            return null;
        }
        PipelineCatalogRecord pipeline = pipelineCatalogService.find(pipelineReference).orElse(null);
        if (pipeline == null) {
            diagnostics.add(diagnostic("ERROR", "UNKNOWN_PIPELINE", "Built-in pipeline was not found: " + pipelineReference, location));
        }
        Map<String, Object> options = object(value.get("options"));
        Map<String, Object> inputs = object(options.get("inputs"));
        Map<String, Object> outputs = object(options.get("outputs"));
        Map<String, Object> parameters = object(options.get("parameters"));
        if (pipeline != null) {
            validateOptionNames(inputs, pipeline.inputs(), "inputs", diagnostics, location);
            validateOptionNames(outputs, pipeline.outputs(), "outputs", diagnostics, location);
            validateOptionNames(parameters, pipeline.parameters(), "parameters", diagnostics, location);
        }
        return new ProcessingContractRecord(pipelineReference, pipeline, inputs, outputs, parameters);
    }

    private void validateOptionNames(
            Map<String, Object> options, Map<String, ?> defined, String optionGroup,
            List<ContractDiagnosticRecord> diagnostics, String location) {
        options.keySet().stream()
                .filter(name -> !defined.containsKey(name))
                .forEach(name -> diagnostics.add(diagnostic("ERROR", "UNKNOWN_PIPELINE_" + optionGroup.toUpperCase(Locale.ROOT),
                        "Pipeline does not declare " + optionGroup + "." + name, location)));
    }

    private Map<String, Object> resolve(Map<String, Object> document, Map<String, Object> value, List<ContractDiagnosticRecord> diagnostics, String location) {
        String reference = text(value.get("$ref"));
        if (reference == null) {
            return value;
        }
        Map<String, Object> resolved = resolveReference(document, reference);
        if (resolved.isEmpty()) {
            diagnostics.add(diagnostic(reference.startsWith("#/") ? "WARNING" : "WARNING",
                    reference.startsWith("#/") ? "UNRESOLVED_REF" : "EXTERNAL_REF_NOT_RESOLVED",
                    "Reference could not be resolved: " + reference, location));
        }
        return resolved;
    }

    private Map<String, Object> resolveReference(Map<String, Object> document, String reference) {
        if (reference == null || !reference.startsWith("#/")) {
            return Map.of();
        }
        Object current = document;
        for (String segment : reference.substring(2).split("/")) {
            if (!(current instanceof Map<?, ?> map)) {
                return Map.of();
            }
            current = map.get(segment.replace("~1", "/").replace("~0", "~"));
        }
        return object(current);
    }

    private ApiSpecEntity requireSpec(String projectId, String apiSpecId) {
        catalogService.requireProjectRecord(projectId);
        ApiSpecEntity spec = apiSpecRepository.findById(apiSpecId)
                .orElseThrow(() -> new NoSuchElementException("API spec not found: " + apiSpecId));
        if (!projectId.equals(spec.getProjectId())) {
            throw new IllegalArgumentException("API spec does not belong to the selected project");
        }
        return spec;
    }

    private Map<String, Object> readDocument(String content) {
        try {
            return yamlMapper.readValue(content, new TypeReference<>() {});
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to parse stored OpenAPI document", exception);
        }
    }

    private static Map<String, Object> objectAt(Map<String, Object> root, String... path) {
        Object current = root;
        for (String segment : path) {
            current = object(current).get(segment);
        }
        return object(current);
    }

    private static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> target = new LinkedHashMap<>();
        source.forEach((key, child) -> target.put(String.valueOf(key), child));
        return target;
    }

    private static List<Object> list(Object value) {
        return value instanceof Collection<?> collection ? new ArrayList<>(collection) : List.of();
    }

    private static List<String> strings(Object value) {
        return list(value).stream().map(String::valueOf).toList();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String text(Object value, String fallback) {
        String text = text(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Object example(Object value) {
        Map<String, Object> examples = object(value);
        if (examples.isEmpty()) {
            return null;
        }
        return object(examples.values().iterator().next()).get("value");
    }

    private static String key(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + path;
    }

    private static ContractDiagnosticRecord diagnostic(String severity, String code, String message, String location) {
        return new ContractDiagnosticRecord(severity, code, message, location);
    }

    public record ApiSpecContractRecord(String apiSpecId, String title, String version,
                                        List<OperationContractRecord> operations, Map<String, Object> schemaRegistry,
                                        List<ContractDiagnosticRecord> diagnostics) { }

    public record OperationContractRecord(String id, String method, String path, String operationId, String summary,
                                           String description, List<String> tags, List<ParameterContractRecord> parameters,
                                           PayloadContractRecord request, List<ResponseContractRecord> responses,
                                           OperationCapabilitiesRecord capabilities, Map<String, Object> rawOperation,
                                           List<ContractDiagnosticRecord> diagnostics, OperationProcessingService.Settings processingSettings) { }

    private static Map<String, Object> normalizedRule(com.example.poc.engine.OperationProcessingSpec.MessageRule rule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protection", rule.getProtection());
        if (rule.getBusinessSchema() != null) result.put("businessSchema", rule.getBusinessSchema());
        return result;
    }

    public record ParameterContractRecord(String name, String location, boolean required, Object schema, Object example,
                                          String description) { }

    public record PayloadContractRecord(ContentContractRecord business, ContentContractRecord wire,
                                        ProcessingContractRecord processing) { }

    public record ContentContractRecord(String mediaType, Object schema, String schemaRef, Object example) { }

    public record ResponseContractRecord(String status, String description, ContentContractRecord business,
                                         ContentContractRecord wire, ProcessingContractRecord processing) { }

    public record ProcessingContractRecord(
            String pipelineReference,
            PipelineCatalogRecord pipeline,
            Map<String, Object> inputs,
            Map<String, Object> outputs,
            Map<String, Object> parameters) { }

    public record OperationCapabilitiesRecord(boolean hasBusinessRequest, boolean hasBusinessResponse,
                                               boolean hasRequestProcessing, boolean hasResponseProcessing,
                                               boolean encrypted, boolean canGenerateScenario) { }

    public record ContractDiagnosticRecord(String severity, String code, String message, String location) { }
}
