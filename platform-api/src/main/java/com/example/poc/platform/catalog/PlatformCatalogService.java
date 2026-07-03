package com.example.poc.platform.catalog;

import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.EnvironmentEntity;
import com.example.poc.platform.persistence.entity.ProfileEntity;
import com.example.poc.platform.persistence.entity.ProjectEntity;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.EnvironmentRepository;
import com.example.poc.platform.persistence.repo.ProfileRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformCatalogService {
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProfileRepository profileRepository;
    private final CaseRepository caseRepository;
    private final JsonCodec jsonCodec;

    public PlatformCatalogService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            ProfileRepository profileRepository,
            CaseRepository caseRepository,
            JsonCodec jsonCodec) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.profileRepository = profileRepository;
        this.caseRepository = caseRepository;
        this.jsonCodec = jsonCodec;
    }

    @PostConstruct
    @Transactional
    public void seedDemoData() {
        ProjectEntity project = projectRepository.findByCode("secure-api-poc")
                .orElseGet(() -> {
                    ProjectEntity entity = new ProjectEntity();
                    entity.setCode("secure-api-poc");
                    entity.setName("Secure API Automation POC");
                    entity.setDescription("Platform POC wired to the existing YAML runner");
                    entity.setStatus("ACTIVE");
                    return projectRepository.save(entity);
                });

        ensureEnvironment(project.getId());
        ensureProfile(project.getId());

        seedCase(project.getId(), "echo-create-user", "Echo Create User", "echo/create-user.yaml");
        seedCase(project.getId(), "secure-mixed-response", "Secure Mixed Response", "mock/secure-mock-mixed-response.yaml");
    }

    @Transactional(readOnly = true)
    public List<ProjectRecord> listProjects() {
        return projectRepository.findAll().stream()
                .sorted((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
                .map(this::toProjectRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectRecord getProject(String projectId) {
        return toProjectRecord(requireProject(projectId));
    }

    @Transactional(readOnly = true)
    public List<EnvironmentRecord> listEnvironments(String projectId) {
        requireProject(projectId);
        return environmentRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(this::toEnvironmentRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnvironmentRecord getEnvironment(String environmentId) {
        return toEnvironmentRecord(requireEnvironment(environmentId));
    }

    @Transactional
    public EnvironmentRecord saveEnvironment(String projectId, @Valid UpsertEnvironmentRequest request) {
        requireProject(projectId);
        EnvironmentEntity entity = request.id() == null || request.id().isBlank()
                ? new EnvironmentEntity()
                : requireEnvironment(request.id());
        entity.setProjectId(projectId);
        entity.setName(request.name());
        entity.setBaseUrl(request.baseUrl());
        entity.setVariablesJson(jsonCodec.write(request.variables() == null ? Map.of() : request.variables()));
        entity.setStatus(defaultStatus(request.status()));
        return toEnvironmentRecord(environmentRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ProfileRecord> listProfiles(String projectId) {
        requireProject(projectId);
        return profileRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(this::toProfileRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileRecord getProfile(String profileId) {
        return toProfileRecord(requireProfile(profileId));
    }

    @Transactional
    public ProfileRecord saveProfile(String projectId, @Valid UpsertProfileRequest request) {
        requireProject(projectId);
        ProfileEntity entity = request.id() == null || request.id().isBlank()
                ? new ProfileEntity()
                : requireProfile(request.id());
        entity.setProjectId(projectId);
        entity.setName(request.name());
        entity.setType(request.type() == null || request.type().isBlank() ? "PGP" : request.type());
        entity.setSenderPublicKey(request.senderPublicKey());
        entity.setSenderPrivateKey(request.senderPrivateKey());
        entity.setSenderPrivateKeyPassphrase(request.senderPrivateKeyPassphrase());
        entity.setRecipientPublicKey(request.recipientPublicKey());
        entity.setRecipientPrivateKey(request.recipientPrivateKey());
        entity.setRecipientPrivateKeyPassphrase(request.recipientPrivateKeyPassphrase());
        entity.setEnabled(request.enabled() == null || request.enabled());
        return toProfileRecord(profileRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CaseRecord> listCases(String projectId) {
        requireProject(projectId);
        return caseRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(this::toCaseRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public CaseRecord getCase(String caseId) {
        return toCaseRecord(requireCase(caseId));
    }

    @Transactional
    public CaseRecord saveCase(String projectId, @Valid UpsertCaseRequest request) {
        requireProject(projectId);
        CaseEntity entity = request.id() == null || request.id().isBlank()
                ? new CaseEntity()
                : requireCase(request.id());
        entity.setProjectId(projectId);
        entity.setName(request.name());
        entity.setCode(request.code());
        entity.setDescription(request.description());
        entity.setTagsJson(jsonCodec.write(request.tags() == null ? List.of() : request.tags()));
        entity.setDslVersion(request.dslVersion() == null || request.dslVersion().isBlank() ? "v1" : request.dslVersion());
        entity.setContent(request.content());
        entity.setStatus(defaultStatus(request.status()));
        return toCaseRecord(caseRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public ProjectRecord requireProjectRecord(String projectId) {
        return toProjectRecord(requireProject(projectId));
    }

    private void seedCase(String projectId, String code, String name, String relativePath) {
        String content = readOptionalFile("test-framework/src/test/resources/api-cases/" + relativePath);
        if (content == null || content.isBlank()) {
            return;
        }
        CaseEntity entity = caseRepository.findByProjectIdAndCode(projectId, code)
                .orElseGet(CaseEntity::new);
        entity.setProjectId(projectId);
        entity.setName(name);
        entity.setCode(code);
        entity.setDescription("Seeded from existing test-framework resources");
        entity.setTagsJson(jsonCodec.write(List.of("seed")));
        entity.setDslVersion("v1");
        entity.setContent(content);
        entity.setStatus("ACTIVE");
        caseRepository.save(entity);
    }

    private void ensureEnvironment(String projectId) {
        boolean exists = environmentRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                .anyMatch(environment -> "local-mock".equals(environment.getName()));
        if (exists) {
            return;
        }
        EnvironmentEntity environment = new EnvironmentEntity();
        environment.setProjectId(projectId);
        environment.setName("local-mock");
        environment.setBaseUrl("http://localhost:8081");
        environment.setVariablesJson(jsonCodec.write(Map.of("baseUrl", "http://localhost:8081")));
        environment.setStatus("ACTIVE");
        environmentRepository.save(environment);
    }

    private void ensureProfile(String projectId) {
        boolean exists = profileRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                .anyMatch(profile -> "local-secure-profile".equals(profile.getName()));
        if (exists) {
            return;
        }
        ProfileEntity profile = new ProfileEntity();
        profile.setProjectId(projectId);
        profile.setName("local-secure-profile");
        profile.setType("PGP");
        profile.setSenderPublicKey(readOptionalFile("test-framework/src/test/resources/keys/server-public.asc"));
        profile.setSenderPrivateKey(readOptionalFile("test-framework/src/test/resources/keys/client-private.asc"));
        profile.setSenderPrivateKeyPassphrase("demo-client-passphrase");
        profile.setRecipientPublicKey(readOptionalFile("test-framework/src/test/resources/keys/server-public.asc"));
        profile.setRecipientPrivateKey(readOptionalFile("test-framework/src/test/resources/keys/client-private.asc"));
        profile.setRecipientPrivateKeyPassphrase("demo-client-passphrase");
        profile.setEnabled(true);
        profileRepository.save(profile);
    }

    private ProjectEntity requireProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Project not found: " + projectId));
    }

    private EnvironmentEntity requireEnvironment(String environmentId) {
        return environmentRepository.findById(environmentId)
                .orElseThrow(() -> new NoSuchElementException("Environment not found: " + environmentId));
    }

    private ProfileEntity requireProfile(String profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new NoSuchElementException("Profile not found: " + profileId));
    }

    private CaseEntity requireCase(String caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Case not found: " + caseId));
    }

    private ProjectRecord toProjectRecord(ProjectEntity entity) {
        return new ProjectRecord(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private EnvironmentRecord toEnvironmentRecord(EnvironmentEntity entity) {
        return new EnvironmentRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getBaseUrl(),
                jsonCodec.readStringMap(entity.getVariablesJson()),
                entity.getStatus());
    }

    private ProfileRecord toProfileRecord(ProfileEntity entity) {
        return new ProfileRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getType(),
                entity.getSenderPublicKey(),
                entity.getSenderPrivateKey(),
                entity.getSenderPrivateKeyPassphrase(),
                entity.getRecipientPublicKey(),
                entity.getRecipientPrivateKey(),
                entity.getRecipientPrivateKeyPassphrase(),
                entity.isEnabled());
    }

    private CaseRecord toCaseRecord(CaseEntity entity) {
        return new CaseRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getCode(),
                entity.getDescription(),
                jsonCodec.readStringList(entity.getTagsJson()),
                entity.getDslVersion(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String defaultStatus(String status) {
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }

    private String readOptionalFile(String relativePath) {
        for (Path baseDir : candidateBaseDirs()) {
            Path path = baseDir.resolve(relativePath).normalize();
            if (Files.exists(path)) {
                try {
                    return Files.readString(path);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to read seed file: " + path, e);
                }
            }
        }
        return null;
    }

    private List<Path> candidateBaseDirs() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        return List.of(cwd, cwd.getParent() == null ? cwd : cwd.getParent());
    }

    public record ProjectRecord(
            String id,
            String code,
            String name,
            String description,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record EnvironmentRecord(
            String id,
            String projectId,
            String name,
            String baseUrl,
            Map<String, String> variables,
            String status) {
    }

    public record ProfileRecord(
            String id,
            String projectId,
            String name,
            String type,
            String senderPublicKey,
            String senderPrivateKey,
            String senderPrivateKeyPassphrase,
            String recipientPublicKey,
            String recipientPrivateKey,
            String recipientPrivateKeyPassphrase,
            boolean enabled) {
    }

    public record CaseRecord(
            String id,
            String projectId,
            String name,
            String code,
            String description,
            List<String> tags,
            String dslVersion,
            String content,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record UpsertEnvironmentRequest(
            String id,
            @NotBlank String name,
            @NotBlank String baseUrl,
            Map<String, String> variables,
            String status) {
    }

    public record UpsertProfileRequest(
            String id,
            @NotBlank String name,
            String type,
            String senderPublicKey,
            String senderPrivateKey,
            String senderPrivateKeyPassphrase,
            String recipientPublicKey,
            String recipientPrivateKey,
            String recipientPrivateKeyPassphrase,
            Boolean enabled) {
    }

    public record UpsertCaseRequest(
            String id,
            @NotBlank String name,
            @NotBlank String code,
            String description,
            List<String> tags,
            String dslVersion,
            @NotBlank String content,
            String status) {
    }
}
