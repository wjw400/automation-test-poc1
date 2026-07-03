package com.example.poc.platform.api;

import com.example.poc.platform.catalog.PlatformCatalogService;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.ApiSpecEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.ApiSpecRepository;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ApiSpecService {
    private final PlatformCatalogService catalogService;
    private final ApiSpecRepository apiSpecRepository;
    private final ApiOperationRepository apiOperationRepository;
    private final JsonCodec jsonCodec;

    public ApiSpecService(
            PlatformCatalogService catalogService,
            ApiSpecRepository apiSpecRepository,
            ApiOperationRepository apiOperationRepository,
            JsonCodec jsonCodec) {
        this.catalogService = catalogService;
        this.apiSpecRepository = apiSpecRepository;
        this.apiOperationRepository = apiOperationRepository;
        this.jsonCodec = jsonCodec;
    }

    @Transactional
    public ApiSpecRecord importSpec(String projectId, MultipartFile file) {
        catalogService.requireProjectRecord(projectId);
        String fileName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                ? "openapi.yaml"
                : file.getOriginalFilename();
        String content = readContent(file);
        OpenAPI openAPI = parseOpenApi(content, fileName);

        ApiSpecEntity specEntity = apiSpecRepository.findByProjectIdAndName(projectId, fileName)
                .orElseGet(ApiSpecEntity::new);
        specEntity.setProjectId(projectId);
        specEntity.setName(fileName);
        specEntity.setTitle(openAPI.getInfo() == null ? fileName : openAPI.getInfo().getTitle());
        specEntity.setSpecVersion(openAPI.getOpenapi());
        specEntity.setSourceFormat("yaml");
        specEntity.setContent(content);
        specEntity = apiSpecRepository.save(specEntity);

        apiOperationRepository.deleteByApiSpecId(specEntity.getId());
        List<ApiOperationEntity> operations = extractOperations(projectId, specEntity.getId(), openAPI);
        apiOperationRepository.saveAll(operations);
        return toApiSpecRecord(specEntity, operations.size());
    }

    public List<ApiSpecRecord> listSpecs(String projectId) {
        catalogService.requireProjectRecord(projectId);
        return apiSpecRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(entity -> toApiSpecRecord(entity, apiOperationRepository.findByApiSpecIdOrderByMethodAscPathAsc(entity.getId()).size()))
                .toList();
    }

    public List<ApiOperationRecord> listOperations(String projectId) {
        catalogService.requireProjectRecord(projectId);
        Map<String, ApiSpecEntity> specById = new LinkedHashMap<>();
        apiSpecRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)
                .forEach(spec -> specById.put(spec.getId(), spec));
        return apiOperationRepository.findByProjectIdOrderByTagNameAscMethodAscPathAsc(projectId).stream()
                .map(entity -> toApiOperationRecord(entity, specById.get(entity.getApiSpecId())))
                .toList();
    }

    public List<ApiOperationRecord> listOperationsBySpec(String projectId, String apiSpecId) {
        catalogService.requireProjectRecord(projectId);
        ApiSpecEntity spec = apiSpecRepository.findById(apiSpecId)
                .orElseThrow(() -> new NoSuchElementException("API spec not found: " + apiSpecId));
        if (!projectId.equals(spec.getProjectId())) {
            throw new IllegalArgumentException("API spec does not belong to the selected project");
        }
        return apiOperationRepository.findByApiSpecIdOrderByMethodAscPathAsc(apiSpecId).stream()
                .map(entity -> toApiOperationRecord(entity, spec))
                .toList();
    }

    public String getSpecContent(String projectId, String apiSpecId) {
        catalogService.requireProjectRecord(projectId);
        ApiSpecEntity spec = apiSpecRepository.findById(apiSpecId)
                .orElseThrow(() -> new NoSuchElementException("API spec not found: " + apiSpecId));
        if (!projectId.equals(spec.getProjectId())) {
            throw new IllegalArgumentException("API spec does not belong to the selected project");
        }
        return spec.getContent();
    }

    private List<ApiOperationEntity> extractOperations(String projectId, String apiSpecId, OpenAPI openAPI) {
        List<ApiOperationEntity> results = new ArrayList<>();
        if (openAPI.getPaths() == null) {
            return results;
        }

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            PathItem pathItem = pathEntry.getValue();
            if (pathItem == null || pathItem.readOperationsMap() == null) {
                continue;
            }

            for (Map.Entry<PathItem.HttpMethod, Operation> operationEntry : pathItem.readOperationsMap().entrySet()) {
                Operation operation = operationEntry.getValue();
                if (operation == null) {
                    continue;
                }
                ApiOperationEntity entity = new ApiOperationEntity();
                entity.setProjectId(projectId);
                entity.setApiSpecId(apiSpecId);
                entity.setTagName(operation.getTags() == null || operation.getTags().isEmpty() ? null : operation.getTags().get(0));
                entity.setMethod(operationEntry.getKey().name());
                entity.setPath(pathEntry.getKey());
                entity.setOperationId(operation.getOperationId());
                entity.setSummary(operation.getSummary());
                entity.setDescription(operation.getDescription());
                entity.setRequestBodyJson(jsonCodec.write(operation.getRequestBody()));
                entity.setResponsesJson(jsonCodec.write(operation.getResponses()));
                entity.setRawOperationJson(jsonCodec.write(operation));
                results.add(entity);
            }
        }
        return results;
    }

    private OpenAPI parseOpenApi(String content, String sourceName) {
        ParseOptions options = new ParseOptions();
        options.setResolve(false);
        options.setResolveFully(false);
        SwaggerParseResult result = new OpenAPIParser().readContents(content, null, options);
        if (result == null || result.getOpenAPI() == null) {
            throw new IllegalArgumentException("Unable to parse OpenAPI document: " + sourceName);
        }
        OpenAPI openAPI = result.getOpenAPI();
        if (openAPI.getOpenapi() == null || !openAPI.getOpenapi().startsWith("3.")) {
            throw new IllegalArgumentException("Only OpenAPI 3.x YAML files are supported");
        }
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
            String joined = String.join("; ", result.getMessages());
            throw new IllegalArgumentException("OpenAPI validation failed: " + joined);
        }
        return openAPI;
    }

    private String readContent(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read uploaded API spec", e);
        }
    }

    private ApiSpecRecord toApiSpecRecord(ApiSpecEntity entity, int operationCount) {
        return new ApiSpecRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getTitle(),
                entity.getSpecVersion(),
                entity.getSourceFormat(),
                operationCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ApiOperationRecord toApiOperationRecord(ApiOperationEntity entity, ApiSpecEntity spec) {
        return new ApiOperationRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getApiSpecId(),
                spec == null ? null : spec.getName(),
                entity.getTagName(),
                entity.getMethod(),
                entity.getPath(),
                entity.getOperationId(),
                entity.getSummary(),
                entity.getDescription(),
                jsonCodec.read(entity.getRequestBodyJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {}, null),
                jsonCodec.read(entity.getResponsesJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {}, Map.of()),
                entity.getCreatedAt());
    }

    public record ApiSpecRecord(
            String id,
            String projectId,
            String name,
            String title,
            String specVersion,
            String sourceFormat,
            int operationCount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ApiOperationRecord(
            String id,
            String projectId,
            String apiSpecId,
            String apiSpecName,
            String tagName,
            String method,
            String path,
            String operationId,
            String summary,
            String description,
            Object requestBody,
            Object responses,
            Instant createdAt) {
    }
}
