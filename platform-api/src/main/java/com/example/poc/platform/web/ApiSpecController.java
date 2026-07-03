package com.example.poc.platform.web;

import com.example.poc.platform.api.ApiSpecService;
import com.example.poc.platform.api.ApiSpecService.ApiOperationRecord;
import com.example.poc.platform.api.ApiSpecService.ApiSpecRecord;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ApiSpecController(ApiSpecService apiSpecService) {
        this.apiSpecService = apiSpecService;
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
}
