package com.example.poc.platform.catalog;

import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.EnvironmentEntity;
import com.example.poc.platform.persistence.entity.FolderEntity;
import com.example.poc.platform.persistence.entity.ProfileEntity;
import com.example.poc.platform.persistence.entity.ProjectEntity;
import com.example.poc.platform.persistence.entity.SuiteCaseEntity;
import com.example.poc.platform.persistence.entity.SuiteEntity;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.EnvironmentRepository;
import com.example.poc.platform.persistence.repo.FolderRepository;
import com.example.poc.platform.persistence.repo.ProfileRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.SuiteCaseRepository;
import com.example.poc.platform.persistence.repo.SuiteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformCatalogService {
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProfileRepository profileRepository;
    private final FolderRepository folderRepository;
    private final CaseRepository caseRepository;
    private final SuiteRepository suiteRepository;
    private final SuiteCaseRepository suiteCaseRepository;
    private final JsonCodec jsonCodec;

    public PlatformCatalogService(
            ProjectRepository projectRepository,
            EnvironmentRepository environmentRepository,
            ProfileRepository profileRepository,
            FolderRepository folderRepository,
            CaseRepository caseRepository,
            SuiteRepository suiteRepository,
            SuiteCaseRepository suiteCaseRepository,
            JsonCodec jsonCodec) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.profileRepository = profileRepository;
        this.folderRepository = folderRepository;
        this.caseRepository = caseRepository;
        this.suiteRepository = suiteRepository;
        this.suiteCaseRepository = suiteCaseRepository;
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

        seedCase(project.getId(), "echo-create-user", "Echo Create User", "echo/create-user.yaml", "Echo");
        seedCase(project.getId(), "secure-mixed-response", "Secure Mixed Response", "mock/secure-mock-mixed-response.yaml", "Mock/Secure");
        migrateLegacyFolderPaths();
        ensureDemoSuite(project.getId());
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
    public List<FolderRecord> listFolders(String projectId) {
        requireProject(projectId);
        return folderRepository.findByProjectIdOrderByParentFolderIdAscSortOrderAscNameAsc(projectId).stream()
                .map(this::toFolderRecord)
                .toList();
    }

    @Transactional
    public FolderRecord saveFolder(String projectId, @Valid UpsertFolderRequest request) {
        requireProject(projectId);
        FolderEntity entity = request.id() == null || request.id().isBlank()
                ? new FolderEntity()
                : requireFolder(request.id());
        if (entity.getId() != null && !projectId.equals(entity.getProjectId())) {
            throw new IllegalArgumentException("Folder does not belong to the selected project");
        }

        String parentFolderId = blankToNull(request.parentFolderId());
        if (parentFolderId != null) {
            FolderEntity parent = requireFolder(parentFolderId);
            if (!projectId.equals(parent.getProjectId())) {
                throw new IllegalArgumentException("Parent folder does not belong to the selected project");
            }
            if (parentFolderId.equals(entity.getId()) || isDescendant(parentFolderId, entity.getId())) {
                throw new IllegalArgumentException("A folder cannot be moved inside itself or one of its descendants");
            }
        }

        String name = request.name().trim();
        boolean duplicate = folderRepository.findByProjectIdAndParentFolderIdOrderBySortOrderAscNameAsc(projectId, parentFolderId).stream()
                .anyMatch(folder -> !folder.getId().equals(entity.getId()) && folder.getName().equalsIgnoreCase(name));
        if (duplicate) {
            throw new IllegalArgumentException("A folder with this name already exists in the selected location");
        }

        entity.setProjectId(projectId);
        entity.setParentFolderId(parentFolderId);
        entity.setName(name);
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setSortOrder(folderRepository.findByProjectIdAndParentFolderIdOrderBySortOrderAscNameAsc(projectId, parentFolderId).size() + 1);
        }
        return toFolderRecord(folderRepository.save(entity));
    }

    @Transactional
    public void deleteFolder(String folderId) {
        FolderEntity folder = requireFolder(folderId);
        if (!folderRepository.findByProjectIdAndParentFolderIdOrderBySortOrderAscNameAsc(folder.getProjectId(), folderId).isEmpty()) {
            throw new IllegalArgumentException("Folder is not empty. Move or delete its subfolders first");
        }
        if (caseRepository.existsByFolderIdAndStatusNot(folderId, "DELETED")) {
            throw new IllegalArgumentException("Folder is not empty. Move or delete its scenarios first");
        }
        folderRepository.delete(folder);
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
        return caseRepository.findByProjectIdAndStatusNotOrderByUpdatedAtDesc(projectId, "DELETED").stream()
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
        if (entity.getId() != null && !projectId.equals(entity.getProjectId())) {
            throw new IllegalArgumentException("Case does not belong to the selected project");
        }
        String folderId = blankToNull(request.folderId());
        if (folderId != null && !projectId.equals(requireFolder(folderId).getProjectId())) {
            throw new IllegalArgumentException("Folder does not belong to the selected project");
        }
        entity.setProjectId(projectId);
        entity.setName(request.name());
        entity.setCode(request.code());
        entity.setDescription(request.description());
        entity.setFolderId(folderId);
        entity.setTagsJson(jsonCodec.write(request.tags() == null ? List.of() : request.tags()));
        entity.setDslVersion(request.dslVersion() == null || request.dslVersion().isBlank() ? "v1" : request.dslVersion());
        entity.setContent(request.content());
        entity.setStatus(defaultStatus(request.status()));
        return toCaseRecord(caseRepository.save(entity));
    }

    @Transactional
    public void deleteCase(String caseId) {
        CaseEntity testCase = requireCase(caseId);
        suiteCaseRepository.deleteByCaseId(testCase.getId());
        testCase.setStatus("DELETED");
        caseRepository.save(testCase);
    }

    @Transactional(readOnly = true)
    public List<SuiteRecord> listSuites(String projectId) {
        requireProject(projectId);
        return suiteRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .map(this::toSuiteRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public SuiteRecord getSuite(String suiteId) {
        return toSuiteRecord(requireSuite(suiteId));
    }

    @Transactional
    public SuiteRecord saveSuite(String projectId, @Valid UpsertSuiteRequest request) {
        requireProject(projectId);
        SuiteEntity entity = request.id() == null || request.id().isBlank()
                ? new SuiteEntity()
                : requireSuite(request.id());
        entity.setProjectId(projectId);
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setStatus(defaultStatus(request.status()));
        SuiteEntity saved = suiteRepository.save(entity);

        suiteCaseRepository.deleteBySuiteId(saved.getId());
        List<UpsertSuiteCaseRequest> cases = request.cases() == null ? List.of() : request.cases();
        for (int i = 0; i < cases.size(); i++) {
            UpsertSuiteCaseRequest suiteCaseRequest = cases.get(i);
            CaseEntity testCase = requireCase(suiteCaseRequest.caseId());
            if (!projectId.equals(testCase.getProjectId())) {
                throw new IllegalArgumentException("Case does not belong to project: " + suiteCaseRequest.caseId());
            }
            SuiteCaseEntity suiteCase = new SuiteCaseEntity();
            suiteCase.setSuiteId(saved.getId());
            suiteCase.setCaseId(suiteCaseRequest.caseId());
            suiteCase.setAlias(suiteCaseRequest.alias());
            suiteCase.setCaseOrder(suiteCaseRequest.order() == null ? i + 1 : suiteCaseRequest.order());
            suiteCase.setEnabled(suiteCaseRequest.enabled() == null || suiteCaseRequest.enabled());
            suiteCase.setVariableOverridesJson(jsonCodec.write(
                    suiteCaseRequest.variableOverrides() == null ? Map.of() : suiteCaseRequest.variableOverrides()));
            suiteCase.setEnvironmentId(blankToNull(suiteCaseRequest.environmentId()));
            suiteCase.setProfileId(blankToNull(suiteCaseRequest.profileId()));
            suiteCaseRepository.save(suiteCase);
        }

        return toSuiteRecord(saved);
    }

    @Transactional
    public void deleteSuite(String suiteId) {
        SuiteEntity suite = requireSuite(suiteId);
        suiteRepository.delete(suite);
    }

    @Transactional(readOnly = true)
    public ProjectRecord requireProjectRecord(String projectId) {
        return toProjectRecord(requireProject(projectId));
    }

    private void seedCase(String projectId, String code, String name, String relativePath, String folderPath) {
        if (caseRepository.findByProjectIdAndCode(projectId, code).isPresent()) {
            return;
        }
        String content = readOptionalFile("test-framework/src/test/resources/api-cases/" + relativePath);
        if (content == null || content.isBlank()) {
            return;
        }
        CaseEntity entity = new CaseEntity();
        entity.setProjectId(projectId);
        entity.setName(name);
        entity.setCode(code);
        entity.setDescription("Seeded from existing test-framework resources");
        entity.setFolderId(resolveFolderPath(projectId, folderPath));
        entity.setTagsJson(jsonCodec.write(List.of("seed")));
        entity.setDslVersion("v1");
        entity.setContent(content);
        entity.setStatus("ACTIVE");
        caseRepository.save(entity);
    }

    private void ensureDemoSuite(String projectId) {
        boolean exists = suiteRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .anyMatch(suite -> "Secure Smoke Suite".equals(suite.getName()));
        if (exists) {
            return;
        }
        List<CaseEntity> cases = new ArrayList<>();
        caseRepository.findByProjectIdAndCode(projectId, "secure-mixed-response")
                .filter(testCase -> !"DELETED".equals(testCase.getStatus()))
                .ifPresent(cases::add);
        caseRepository.findByProjectIdAndCode(projectId, "echo-create-user")
                .filter(testCase -> !"DELETED".equals(testCase.getStatus()))
                .ifPresent(cases::add);
        if (cases.isEmpty()) {
            return;
        }
        SuiteEntity suite = new SuiteEntity();
        suite.setProjectId(projectId);
        suite.setName("Secure Smoke Suite");
        suite.setDescription("Demo suite that references existing seeded cases");
        suite.setStatus("ACTIVE");
        SuiteEntity savedSuite = suiteRepository.save(suite);
        for (int i = 0; i < cases.size(); i++) {
            SuiteCaseEntity suiteCase = new SuiteCaseEntity();
            suiteCase.setSuiteId(savedSuite.getId());
            suiteCase.setCaseId(cases.get(i).getId());
            suiteCase.setAlias(cases.get(i).getName());
            suiteCase.setCaseOrder(i + 1);
            suiteCase.setEnabled(true);
            suiteCase.setVariableOverridesJson(jsonCodec.write(Map.of()));
            suiteCaseRepository.save(suiteCase);
        }
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

    private FolderEntity requireFolder(String folderId) {
        return folderRepository.findById(folderId)
                .orElseThrow(() -> new NoSuchElementException("Folder not found: " + folderId));
    }

    private CaseEntity requireCase(String caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new NoSuchElementException("Case not found: " + caseId));
    }

    private SuiteEntity requireSuite(String suiteId) {
        return suiteRepository.findById(suiteId)
                .orElseThrow(() -> new NoSuchElementException("Suite not found: " + suiteId));
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

    private FolderRecord toFolderRecord(FolderEntity entity) {
        return new FolderRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getParentFolderId(),
                entity.getName(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private CaseRecord toCaseRecord(CaseEntity entity) {
        return new CaseRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getCode(),
                entity.getDescription(),
                entity.getFolderId(),
                jsonCodec.readStringList(entity.getTagsJson()),
                entity.getDslVersion(),
                entity.getContent(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private SuiteRecord toSuiteRecord(SuiteEntity entity) {
        List<SuiteCaseRecord> cases = suiteCaseRepository.findBySuiteIdOrderByCaseOrderAsc(entity.getId()).stream()
                .map(this::toSuiteCaseRecord)
                .toList();
        return new SuiteRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                cases,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private SuiteCaseRecord toSuiteCaseRecord(SuiteCaseEntity entity) {
        CaseEntity testCase = requireCase(entity.getCaseId());
        return new SuiteCaseRecord(
                entity.getId(),
                entity.getCaseId(),
                testCase.getName(),
                testCase.getCode(),
                entity.getAlias(),
                entity.getCaseOrder(),
                entity.isEnabled(),
                jsonCodec.read(entity.getVariableOverridesJson(), new TypeReference<Map<String, Object>>() {}, Map.of()),
                entity.getEnvironmentId(),
                entity.getProfileId());
    }

    private String defaultStatus(String status) {
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void migrateLegacyFolderPaths() {
        caseRepository.findAll().forEach(testCase -> {
            if (testCase.getFolderId() == null && testCase.getFolderPath() != null && !testCase.getFolderPath().isBlank()) {
                testCase.setFolderId(resolveFolderPath(testCase.getProjectId(), testCase.getFolderPath()));
                caseRepository.save(testCase);
            }
        });
    }

    private String resolveFolderPath(String projectId, String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            return null;
        }
        String parentFolderId = null;
        for (String segment : Arrays.stream(folderPath.replace('\\', '/').split("/"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList()) {
            final String currentParentId = parentFolderId;
            FolderEntity folder = folderRepository.findByProjectIdAndParentFolderIdOrderBySortOrderAscNameAsc(projectId, currentParentId)
                    .stream()
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(segment))
                    .findFirst()
                    .orElseGet(() -> {
                        FolderEntity created = new FolderEntity();
                        created.setProjectId(projectId);
                        created.setParentFolderId(currentParentId);
                        created.setName(segment);
                        created.setSortOrder(folderRepository.findByProjectIdAndParentFolderIdOrderBySortOrderAscNameAsc(projectId, currentParentId).size() + 1);
                        return folderRepository.save(created);
                    });
            parentFolderId = folder.getId();
        }
        return parentFolderId;
    }

    private boolean isDescendant(String folderId, String possibleAncestorId) {
        if (possibleAncestorId == null) {
            return false;
        }
        Set<String> visited = new HashSet<>();
        String currentId = folderId;
        while (currentId != null && visited.add(currentId)) {
            if (currentId.equals(possibleAncestorId)) {
                return true;
            }
            currentId = requireFolder(currentId).getParentFolderId();
        }
        return false;
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

    public record FolderRecord(
            String id,
            String projectId,
            String parentFolderId,
            String name,
            Integer sortOrder,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CaseRecord(
            String id,
            String projectId,
            String name,
            String code,
            String description,
            String folderId,
            List<String> tags,
            String dslVersion,
            String content,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SuiteRecord(
            String id,
            String projectId,
            String name,
            String description,
            String status,
            List<SuiteCaseRecord> cases,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record SuiteCaseRecord(
            String id,
            String caseId,
            String caseName,
            String caseCode,
            String alias,
            Integer order,
            boolean enabled,
            Map<String, Object> variableOverrides,
            String environmentId,
            String profileId) {
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
            String folderId,
            List<String> tags,
            String dslVersion,
            @NotBlank String content,
            String status) {
    }

    public record UpsertFolderRequest(
            String id,
            @NotBlank String name,
            String parentFolderId) {
    }

    public record UpsertSuiteRequest(
            String id,
            @NotBlank String name,
            String description,
            String status,
            List<UpsertSuiteCaseRequest> cases) {
    }

    public record UpsertSuiteCaseRequest(
            String id,
            @NotBlank String caseId,
            String alias,
            Integer order,
            Boolean enabled,
            Map<String, Object> variableOverrides,
            String environmentId,
            String profileId) {
    }
}
