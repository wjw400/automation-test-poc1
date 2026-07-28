package com.example.poc.platform.web;

import com.example.poc.platform.knowledge.TestKnowledgeService;
import com.example.poc.platform.knowledge.TestKnowledgeService.TestKnowledgeRecord;
import com.example.poc.platform.knowledge.TestKnowledgeService.UpsertTestKnowledgeRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestKnowledgeController {
    private final TestKnowledgeService knowledgeService;
    public TestKnowledgeController(TestKnowledgeService knowledgeService) { this.knowledgeService = knowledgeService; }

    @GetMapping("/projects/{projectId}/test-knowledge")
    public List<TestKnowledgeRecord> list(@PathVariable("projectId") String projectId) { return knowledgeService.list(projectId); }
    @GetMapping("/test-knowledge/{knowledgeId}")
    public TestKnowledgeRecord get(@PathVariable("knowledgeId") String knowledgeId) { return knowledgeService.get(knowledgeId); }
    @PostMapping("/projects/{projectId}/test-knowledge")
    public TestKnowledgeRecord create(@PathVariable("projectId") String projectId, @Valid @RequestBody UpsertTestKnowledgeRequest request) { return knowledgeService.save(projectId, request); }
    @PutMapping("/projects/{projectId}/test-knowledge/{knowledgeId}")
    public TestKnowledgeRecord update(@PathVariable("projectId") String projectId, @PathVariable("knowledgeId") String knowledgeId, @Valid @RequestBody UpsertTestKnowledgeRequest request) {
        return knowledgeService.save(projectId, new UpsertTestKnowledgeRequest(knowledgeId, request.name(), request.description(), request.scope(), request.targetId(), request.tags(), request.content(), request.status()));
    }
    @DeleteMapping("/projects/{projectId}/test-knowledge/{knowledgeId}")
    public ResponseEntity<Void> delete(@PathVariable("projectId") String projectId, @PathVariable("knowledgeId") String knowledgeId) {
        knowledgeService.delete(projectId, knowledgeId);
        return ResponseEntity.noContent().build();
    }
}
