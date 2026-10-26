package com.example.poc.platform.web;

import com.example.poc.platform.api.ApiSpecService;
import com.example.poc.platform.api.ApiSpecService.ApiOperationRecord;
import com.example.poc.platform.api.ApiSpecService.ApiSpecReferenceRecord;
import com.example.poc.platform.api.ApiSpecService.ApiSpecRecord;
import com.example.poc.platform.openapi.OpenApiContractService;
import com.example.poc.platform.openapi.OpenApiContractService.ApiSpecContractRecord;
import com.example.poc.platform.openapi.OpenApiContractService.OperationContractRecord;
import com.example.poc.platform.openapi.OperationProcessingService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiSpecController {
    private final ApiSpecService apiSpecService;
    private final OpenApiContractService openApiContractService;
    private final OperationProcessingService processingService;

    public ApiSpecController(ApiSpecService apiSpecService, OpenApiContractService openApiContractService, OperationProcessingService processingService) {
        this.apiSpecService = apiSpecService;
        this.openApiContractService = openApiContractService;
        this.processingService = processingService;
    }

    @GetMapping("/projects/{projectId}/operations/{operationId}/processing")
    public OperationProcessingService.Settings getProcessing(@PathVariable("projectId") String projectId, @PathVariable("operationId") String operationId) {
        return processingService.get(projectId, operationId);
    }

    @PutMapping("/projects/{projectId}/operations/{operationId}/processing")
    public OperationProcessingService.Settings saveProcessing(@PathVariable("projectId") String projectId, @PathVariable("operationId") String operationId,
            @RequestBody OperationProcessingService.SaveRequest request) {
        return processingService.save(projectId, operationId, request);
    }

    @PostMapping("/projects/{projectId}/operations/{operationId}/processing/use-imported")
    public OperationProcessingService.Settings useImported(@PathVariable("projectId") String projectId, @PathVariable("operationId") String operationId, @RequestBody java.util.Map<String, Long> request) {
        if (!request.containsKey("revision")) throw new IllegalArgumentException("revision is required");
        return processingService.useImported(projectId, operationId, request.get("revision"));
    }

    @GetMapping("/projects/{projectId}/api-specs")
    public List<ApiSpecRecord> listSpecs(@PathVariable("projectId") String projectId) {
        return apiSpecService.listSpecs(projectId);
    }

    @PostMapping(path = "/projects/{projectId}/api-specs/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiSpecRecord importSpec(
            @PathVariable("projectId") String projectId,
            @RequestParam("file") MultipartFile file) {
        return apiSpecService.importSpec(projectId, file);
    }

    @GetMapping("/projects/{projectId}/operations")
    public List<ApiOperationRecord> listOperations(@PathVariable("projectId") String projectId) {
        return apiSpecService.listOperations(projectId);
    }

    @GetMapping("/projects/{projectId}/api-specs/{apiSpecId}/operations")
    public List<ApiOperationRecord> listOperationsBySpec(
            @PathVariable("projectId") String projectId,
            @PathVariable("apiSpecId") String apiSpecId) {
        return apiSpecService.listOperationsBySpec(projectId, apiSpecId);
    }

    @GetMapping("/projects/{projectId}/api-specs/{apiSpecId}/content")
    public ResponseEntity<String> getSpecContent(
            @PathVariable("projectId") String projectId,
            @PathVariable("apiSpecId") String apiSpecId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/yaml; charset=UTF-8")
                .body(apiSpecService.getSpecContent(projectId, apiSpecId));
    }

    @GetMapping("/projects/{projectId}/api-specs/{apiSpecId}/contract")
    public ApiSpecContractRecord getSpecContract(
            @PathVariable("projectId") String projectId,
            @PathVariable("apiSpecId") String apiSpecId) {
        return openApiContractService.getSpecContract(projectId, apiSpecId);
    }

    @GetMapping("/projects/{projectId}/operations/{operationId}/contract")
    public OperationContractRecord getOperationContract(
            @PathVariable("projectId") String projectId,
            @PathVariable("operationId") String operationId) {
        return openApiContractService.getOperationContract(projectId, operationId);
    }

    @GetMapping("/projects/{projectId}/api-specs/{apiSpecId}/references")
    public List<ApiSpecReferenceRecord> listReferences(
            @PathVariable("projectId") String projectId,
            @PathVariable("apiSpecId") String apiSpecId) {
        return apiSpecService.listReferences(projectId, apiSpecId);
    }

    @DeleteMapping("/projects/{projectId}/api-specs/{apiSpecId}")
    public ResponseEntity<Void> deleteSpec(
            @PathVariable("projectId") String projectId,
            @PathVariable("apiSpecId") String apiSpecId) {
        apiSpecService.deleteSpec(projectId, apiSpecId);
        return ResponseEntity.noContent().build();
    }
}
