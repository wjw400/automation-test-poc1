package com.example.poc.platform.agent.suite;

import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.EnvironmentEntity;
import com.example.poc.platform.persistence.entity.ProfileEntity;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.EnvironmentRepository;
import com.example.poc.platform.persistence.repo.ProfileRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SuiteDraftValidator {
    private final CaseRepository caseRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProfileRepository profileRepository;

    public SuiteDraftValidator(CaseRepository caseRepository, EnvironmentRepository environmentRepository, ProfileRepository profileRepository) {
        this.caseRepository = caseRepository; this.environmentRepository = environmentRepository; this.profileRepository = profileRepository;
    }

    public SuiteValidationResult validate(String projectId, SuiteDraftDocument document) {
        List<SuiteValidationResult.ValidationIssue> issues = new ArrayList<>();
        if (document.name().isBlank()) issue(issues, "ERROR", "SUITE_NAME_REQUIRED", "/name", "Suite name is required");
        if (document.name().length() > 200) issue(issues, "ERROR", "SUITE_NAME_TOO_LONG", "/name", "Suite name must be 200 characters or fewer");
        if (!Set.of("ACTIVE", "INACTIVE").contains(document.status())) issue(issues, "ERROR", "SUITE_STATUS_INVALID", "/status", "Suite status must be ACTIVE or INACTIVE");
        if (document.entries().isEmpty()) issue(issues, "WARNING", "SUITE_EMPTY", "/entries", "The Suite does not contain any Scenario entries");
        Set<String> ids = new HashSet<>(); boolean anyEnabled = false;
        for (int i = 0; i < document.entries().size(); i++) {
            SuiteDraftDocument.Entry entry = document.entries().get(i); String path = "/entries/" + i;
            if (entry.id() == null || entry.id().isBlank()) issue(issues, "ERROR", "ENTRY_ID_REQUIRED", path + "/id", "Suite entry id is required");
            else if (!ids.add(entry.id())) issue(issues, "ERROR", "ENTRY_ID_DUPLICATE", path + "/id", "Suite entry ids must be unique");
            anyEnabled |= entry.enabled();
            CaseEntity scenario = entry.scenarioId() == null ? null : caseRepository.findById(entry.scenarioId()).orElse(null);
            if (scenario == null || !projectId.equals(scenario.getProjectId()) || "DELETED".equalsIgnoreCase(scenario.getStatus())) issue(issues, "ERROR", "SCENARIO_NOT_AVAILABLE", path + "/scenarioId", "Scenario is not available in this project");
            checkContext(projectId, entry, path, issues);
        }
        if (!document.entries().isEmpty() && !anyEnabled) issue(issues, "WARNING", "SUITE_ALL_DISABLED", "/entries", "All Suite entries are disabled");
        return SuiteValidationResult.of(issues);
    }

    private void checkContext(String projectId, SuiteDraftDocument.Entry entry, String path, List<SuiteValidationResult.ValidationIssue> issues) {
        EnvironmentEntity environment = null;
        if (entry.environmentId() != null && !entry.environmentId().isBlank()) {
            environment = environmentRepository.findById(entry.environmentId()).orElse(null);
            if (environment == null || !projectId.equals(environment.getProjectId())) issue(issues, "ERROR", "ENVIRONMENT_NOT_AVAILABLE", path + "/environmentId", "Environment is not available in this project");
        }
        if (entry.profileId() != null && !entry.profileId().isBlank()) {
            ProfileEntity profile = profileRepository.findById(entry.profileId()).orElse(null);
            if (profile == null || !projectId.equals(profile.getProjectId()) || !profile.isEnabled()) issue(issues, "ERROR", "PROFILE_NOT_AVAILABLE", path + "/profileId", "Profile is not available in this project");
            else if (environment != null && !profile.getEnvironmentIds().contains(environment.getId())) issue(issues, "ERROR", "PROFILE_ENVIRONMENT_MISMATCH", path + "/profileId", "Profile is not available in the selected environment");
        }
    }
    private void issue(List<SuiteValidationResult.ValidationIssue> issues, String severity, String code, String path, String message) { issues.add(new SuiteValidationResult.ValidationIssue(SuiteValidationResult.Severity.valueOf(severity), code, path, message)); }
}
