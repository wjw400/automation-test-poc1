package com.example.poc.platform.web;

import com.example.poc.platform.catalog.PlatformCatalogService;
import com.example.poc.platform.catalog.PlatformCatalogService.CaseRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.EnvironmentRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.FolderRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.ProfileRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.ProjectRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.ResourceReferenceRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.SuiteRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.UpsertCaseRequest;
import com.example.poc.platform.catalog.PlatformCatalogService.UpsertEnvironmentRequest;
import com.example.poc.platform.catalog.PlatformCatalogService.UpsertFolderRequest;
import com.example.poc.platform.catalog.PlatformCatalogService.UpsertProfileRequest;
import com.example.poc.platform.catalog.PlatformCatalogService.UpsertProjectRequest;
import com.example.poc.platform.catalog.PlatformCatalogService.UpsertSuiteRequest;
import jakarta.validation.Valid;
import java.util.List;
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
public class CatalogController {
    private final PlatformCatalogService catalogService;

    public CatalogController(PlatformCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/projects")
    public List<ProjectRecord> listProjects() {
        return catalogService.listProjects();
    }

    @PostMapping("/projects")
    public ProjectRecord createProject(@Valid @RequestBody UpsertProjectRequest request) {
        return catalogService.createProject(request);
    }

    @GetMapping("/projects/{projectId}")
    public ProjectRecord getProject(@PathVariable("projectId") String projectId) {
        return catalogService.getProject(projectId);
    }

    @GetMapping("/projects/{projectId}/environments")
    public List<EnvironmentRecord> listEnvironments(@PathVariable("projectId") String projectId) {
        return catalogService.listEnvironments(projectId);
    }

    @PostMapping("/projects/{projectId}/environments")
    public EnvironmentRecord createEnvironment(@PathVariable("projectId") String projectId, @Valid @RequestBody UpsertEnvironmentRequest request) {
        return catalogService.saveEnvironment(projectId, request);
    }

    @PutMapping("/projects/{projectId}/environments/{environmentId}")
    public EnvironmentRecord updateEnvironment(
            @PathVariable("projectId") String projectId,
            @PathVariable("environmentId") String environmentId,
            @Valid @RequestBody UpsertEnvironmentRequest request) {
        return catalogService.saveEnvironment(projectId, new UpsertEnvironmentRequest(
                environmentId,
                request.name(),
                request.baseUrl(),
                request.endpoints(),
                request.variables(),
                request.status()));
    }

    @GetMapping("/projects/{projectId}/environments/{environmentId}/references")
    public List<ResourceReferenceRecord> listEnvironmentReferences(
            @PathVariable("projectId") String projectId,
            @PathVariable("environmentId") String environmentId) {
        return catalogService.listEnvironmentReferences(projectId, environmentId);
    }

    @DeleteMapping("/projects/{projectId}/environments/{environmentId}")
    public void deleteEnvironment(
            @PathVariable("projectId") String projectId,
            @PathVariable("environmentId") String environmentId) {
        catalogService.deleteEnvironment(projectId, environmentId);
    }

    @GetMapping("/projects/{projectId}/profiles")
    public List<ProfileRecord> listProfiles(@PathVariable("projectId") String projectId) {
        return catalogService.listProfiles(projectId);
    }

    @PostMapping("/projects/{projectId}/profiles")
    public ProfileRecord createProfile(@PathVariable("projectId") String projectId, @Valid @RequestBody UpsertProfileRequest request) {
        return catalogService.saveProfile(projectId, request);
    }

    @PutMapping("/projects/{projectId}/profiles/{profileId}")
    public ProfileRecord updateProfile(
            @PathVariable("projectId") String projectId,
            @PathVariable("profileId") String profileId,
            @Valid @RequestBody UpsertProfileRequest request) {
        return catalogService.saveProfile(projectId, new UpsertProfileRequest(
                profileId,
                request.name(),
                request.type(),
                request.verificationPublicKey(),
                request.signingPrivateKey(),
                request.signingPrivateKeyPassphrase(),
                request.encryptionPublicKey(),
                request.decryptionPrivateKey(),
                request.decryptionPrivateKeyPassphrase(),
                request.environmentIds(),
                request.enabled()));
    }

    @GetMapping("/projects/{projectId}/profiles/{profileId}/references")
    public List<ResourceReferenceRecord> listProfileReferences(
            @PathVariable("projectId") String projectId,
            @PathVariable("profileId") String profileId) {
        return catalogService.listProfileReferences(projectId, profileId);
    }

    @DeleteMapping("/projects/{projectId}/profiles/{profileId}")
    public void deleteProfile(
            @PathVariable("projectId") String projectId,
            @PathVariable("profileId") String profileId) {
        catalogService.deleteProfile(projectId, profileId);
    }

    @GetMapping("/projects/{projectId}/folders")
    public List<FolderRecord> listFolders(@PathVariable("projectId") String projectId) {
        return catalogService.listFolders(projectId);
    }

    @PostMapping("/projects/{projectId}/folders")
    public FolderRecord createFolder(@PathVariable("projectId") String projectId, @Valid @RequestBody UpsertFolderRequest request) {
        return catalogService.saveFolder(projectId, request);
    }

    @PutMapping("/projects/{projectId}/folders/{folderId}")
    public FolderRecord updateFolder(
            @PathVariable("projectId") String projectId,
            @PathVariable("folderId") String folderId,
            @Valid @RequestBody UpsertFolderRequest request) {
        return catalogService.saveFolder(projectId, new UpsertFolderRequest(folderId, request.name(), request.parentFolderId()));
    }

    @DeleteMapping("/folders/{folderId}")
    public void deleteFolder(@PathVariable("folderId") String folderId) {
        catalogService.deleteFolder(folderId);
    }

    @GetMapping("/projects/{projectId}/cases")
    public List<CaseRecord> listCases(@PathVariable("projectId") String projectId) {
        return catalogService.listCases(projectId);
    }

    @GetMapping("/cases/{caseId}")
    public CaseRecord getCase(@PathVariable("caseId") String caseId) {
        return catalogService.getCase(caseId);
    }

    @PostMapping("/projects/{projectId}/cases")
    public CaseRecord createCase(@PathVariable("projectId") String projectId, @Valid @RequestBody UpsertCaseRequest request) {
        return catalogService.saveCase(projectId, request);
    }

    @PutMapping("/projects/{projectId}/cases/{caseId}")
    public CaseRecord updateCase(
            @PathVariable("projectId") String projectId,
            @PathVariable("caseId") String caseId,
            @Valid @RequestBody UpsertCaseRequest request) {
        return catalogService.saveCase(projectId, new UpsertCaseRequest(
                caseId,
                request.name(),
                request.code(),
                request.description(),
                request.folderId(),
                request.defaultEnvironmentId(),
                request.defaultProfileId(),
                request.tags(),
                request.dslVersion(),
                request.content(),
                request.status()));
    }

    @DeleteMapping("/cases/{caseId}")
    public void deleteCase(@PathVariable("caseId") String caseId) {
        catalogService.deleteCase(caseId);
    }

    @GetMapping("/projects/{projectId}/suites")
    public List<SuiteRecord> listSuites(@PathVariable("projectId") String projectId) {
        return catalogService.listSuites(projectId);
    }

    @GetMapping("/suites/{suiteId}")
    public SuiteRecord getSuite(@PathVariable("suiteId") String suiteId) {
        return catalogService.getSuite(suiteId);
    }

    @PostMapping("/projects/{projectId}/suites")
    public SuiteRecord createSuite(@PathVariable("projectId") String projectId, @Valid @RequestBody UpsertSuiteRequest request) {
        return catalogService.saveSuite(projectId, request);
    }

    @PutMapping("/projects/{projectId}/suites/{suiteId}")
    public SuiteRecord updateSuite(
            @PathVariable("projectId") String projectId,
            @PathVariable("suiteId") String suiteId,
            @Valid @RequestBody UpsertSuiteRequest request) {
        return catalogService.saveSuite(projectId, new UpsertSuiteRequest(
                suiteId,
                request.name(),
                request.description(),
                request.status(),
                request.cases()));
    }

    @DeleteMapping("/suites/{suiteId}")
    public void deleteSuite(@PathVariable("suiteId") String suiteId) {
        catalogService.deleteSuite(suiteId);
    }
}
