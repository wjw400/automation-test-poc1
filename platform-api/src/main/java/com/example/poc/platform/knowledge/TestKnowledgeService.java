package com.example.poc.platform.knowledge;

import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.entity.ApiSpecEntity;
import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.ProjectEntity;
import com.example.poc.platform.persistence.entity.SuiteEntity;
import com.example.poc.platform.persistence.entity.TestKnowledgeEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.ApiSpecRepository;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.SuiteRepository;
import com.example.poc.platform.persistence.repo.TestKnowledgeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestKnowledgeService {
    private static final Set<String> SCOPES = Set.of("PROJECT", "API_SPEC", "OPERATION", "SCENARIO", "SUITE");
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED");

    private final TestKnowledgeRepository knowledgeRepository;
    private final ProjectRepository projectRepository;
    private final ApiSpecRepository apiSpecRepository;
    private final ApiOperationRepository operationRepository;
    private final CaseRepository caseRepository;
    private final SuiteRepository suiteRepository;
    private final JsonCodec jsonCodec;

    public TestKnowledgeService(TestKnowledgeRepository knowledgeRepository, ProjectRepository projectRepository,
            ApiSpecRepository apiSpecRepository, ApiOperationRepository operationRepository,
            CaseRepository caseRepository, SuiteRepository suiteRepository, JsonCodec jsonCodec) {
        this.knowledgeRepository = knowledgeRepository;
        this.projectRepository = projectRepository;
        this.apiSpecRepository = apiSpecRepository;
        this.operationRepository = operationRepository;
        this.caseRepository = caseRepository;
        this.suiteRepository = suiteRepository;
        this.jsonCodec = jsonCodec;
    }

    @Transactional(readOnly = true)
    public List<TestKnowledgeRecord> list(String projectId) {
        requireProject(projectId);
        return knowledgeRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream().map(this::toRecord).toList();
    }

    @Transactional(readOnly = true)
    public TestKnowledgeRecord get(String knowledgeId) { return toRecord(requireKnowledge(knowledgeId)); }

    @Transactional
    public TestKnowledgeRecord save(String projectId, @Valid UpsertTestKnowledgeRequest request) {
        requireProject(projectId);
        TestKnowledgeEntity entity = request.id() == null || request.id().isBlank()
                ? new TestKnowledgeEntity() : requireKnowledge(request.id());
        if (entity.getId() != null && !projectId.equals(entity.getProjectId())) {
            throw new IllegalArgumentException("Test knowledge does not belong to the selected project");
        }
        String scope = normalizeScope(request.scope());
        String targetId = request.targetId() == null || request.targetId().isBlank() ? null : request.targetId().trim();
        if ("PROJECT".equals(scope) && targetId != null) {
            throw new IllegalArgumentException("Project-level knowledge cannot target a specific asset");
        }
        if (!"PROJECT".equals(scope) && targetId == null) {
            throw new IllegalArgumentException("Select an asset for this knowledge scope");
        }
        validateTarget(projectId, scope, targetId);
        entity.setProjectId(projectId);
        entity.setName(request.name().trim());
        entity.setDescription(request.description() == null ? "" : request.description().trim());
        entity.setScope(scope);
        entity.setTargetId(targetId);
        entity.setTagsJson(jsonCodec.write(request.tags() == null ? List.of() : request.tags()));
        entity.setContent(request.content());
        entity.setStatus(normalizeStatus(request.status()));
        if (entity.getId() != null && entity.getVersion() != null) entity.setVersion(entity.getVersion() + 1);
        return toRecord(knowledgeRepository.save(entity));
    }

    @Transactional
    public void delete(String projectId, String knowledgeId) {
        TestKnowledgeEntity entity = requireKnowledge(knowledgeId);
        if (!projectId.equals(entity.getProjectId())) throw new IllegalArgumentException("Test knowledge does not belong to the selected project");
        knowledgeRepository.delete(entity);
    }

    private void validateTarget(String projectId, String scope, String targetId) {
        if (targetId == null) return;
        switch (scope) {
            case "API_SPEC" -> requireProjectAsset(apiSpecRepository.findById(targetId).orElseThrow(() -> notFound("API spec", targetId)), projectId);
            case "OPERATION" -> requireProjectAsset(operationRepository.findById(targetId).orElseThrow(() -> notFound("Operation", targetId)), projectId);
            case "SCENARIO" -> requireProjectAsset(caseRepository.findById(targetId).orElseThrow(() -> notFound("Scenario", targetId)), projectId);
            case "SUITE" -> requireProjectAsset(suiteRepository.findById(targetId).orElseThrow(() -> notFound("Suite", targetId)), projectId);
            default -> { }
        }
    }

    private void requireProjectAsset(Object asset, String projectId) {
        String assetProjectId;
        if (asset instanceof ApiSpecEntity value) {
            assetProjectId = value.getProjectId();
        } else if (asset instanceof ApiOperationEntity value) {
            assetProjectId = value.getProjectId();
        } else if (asset instanceof CaseEntity value) {
            assetProjectId = value.getProjectId();
        } else if (asset instanceof SuiteEntity value) {
            assetProjectId = value.getProjectId();
        } else {
            assetProjectId = null;
        }
        if (!projectId.equals(assetProjectId)) throw new IllegalArgumentException("Knowledge target does not belong to the selected project");
    }

    private NoSuchElementException notFound(String type, String id) { return new NoSuchElementException(type + " not found: " + id); }
    private ProjectEntity requireProject(String id) { return projectRepository.findById(id).orElseThrow(() -> notFound("Project", id)); }
    private TestKnowledgeEntity requireKnowledge(String id) { return knowledgeRepository.findById(id).orElseThrow(() -> notFound("Test knowledge", id)); }
    private String normalizeScope(String value) {
        String scope = value == null ? "PROJECT" : value.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) throw new IllegalArgumentException("Unsupported knowledge scope: " + value);
        return scope;
    }
    private String normalizeStatus(String value) {
        String status = value == null ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Unsupported knowledge status: " + value);
        return status;
    }
    private TestKnowledgeRecord toRecord(TestKnowledgeEntity entity) {
        return new TestKnowledgeRecord(entity.getId(), entity.getProjectId(), entity.getName(), entity.getDescription(),
                entity.getScope(), entity.getTargetId(), jsonCodec.readStringList(entity.getTagsJson()), entity.getContent(),
                entity.getStatus(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public record TestKnowledgeRecord(String id, String projectId, String name, String description, String scope,
            String targetId, List<String> tags, String content, String status, Integer version,
            Instant createdAt, Instant updatedAt) { }
    public record UpsertTestKnowledgeRequest(String id, @NotBlank String name, String description, @NotBlank String scope,
            String targetId, List<String> tags, @NotBlank String content, String status) { }
}
