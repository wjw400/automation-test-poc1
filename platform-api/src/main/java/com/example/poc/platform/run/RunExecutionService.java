package com.example.poc.platform.run;

import com.example.poc.engine.ApiCaseRunner;
import com.example.poc.engine.ApiTestCase;
import com.example.poc.engine.CryptoProfile;
import com.example.poc.engine.RuntimeSpec;
import com.example.poc.engine.report.ActionExecutionReport;
import com.example.poc.engine.report.ApiCaseExecutionReport;
import com.example.poc.engine.report.ApiStepExecutionReport;
import com.example.poc.engine.report.ReportRequest;
import com.example.poc.engine.report.ReportResponse;
import com.example.poc.engine.report.ValidationReport;
import com.example.poc.platform.catalog.PlatformCatalogService;
import com.example.poc.platform.catalog.PlatformCatalogService.CaseRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.EnvironmentRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.ProfileRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.SuiteCaseRecord;
import com.example.poc.platform.catalog.PlatformCatalogService.SuiteRecord;
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.RunEntity;
import com.example.poc.platform.persistence.entity.StepRunEntity;
import com.example.poc.platform.persistence.entity.SuiteRunEntity;
import com.example.poc.platform.persistence.repo.RunRepository;
import com.example.poc.platform.persistence.repo.StepRunRepository;
import com.example.poc.platform.persistence.repo.SuiteRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunExecutionService {
    private final PlatformCatalogService catalogService;
    private final RunRepository runRepository;
    private final StepRunRepository stepRunRepository;
    private final SuiteRunRepository suiteRunRepository;
    private final JsonCodec jsonCodec;
    private final ApiCaseRunner apiCaseRunner = new ApiCaseRunner();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public RunExecutionService(
            PlatformCatalogService catalogService,
            RunRepository runRepository,
            StepRunRepository stepRunRepository,
            SuiteRunRepository suiteRunRepository,
            JsonCodec jsonCodec) {
        this.catalogService = catalogService;
        this.runRepository = runRepository;
        this.stepRunRepository = stepRunRepository;
        this.suiteRunRepository = suiteRunRepository;
        this.jsonCodec = jsonCodec;
    }

    @Transactional(readOnly = true)
    public List<RunRecord> listRuns() {
        return runRepository.findAllByOrderByStartedAtDesc().stream()
                .map(run -> toRunRecord(run, stepRunRepository.findByRunIdOrderByStepIndexAsc(run.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RunRecord getRun(String runId) {
        RunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> new NoSuchElementException("Run not found: " + runId));
        return toRunRecord(run, stepRunRepository.findByRunIdOrderByStepIndexAsc(runId));
    }

    @Transactional(readOnly = true)
    public List<SuiteRunRecord> listSuiteRuns() {
        return suiteRunRepository.findAllByOrderByStartedAtDesc().stream()
                .map(this::toSuiteRunRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public SuiteRunRecord getSuiteRun(String suiteRunId) {
        SuiteRunEntity suiteRun = suiteRunRepository.findById(suiteRunId)
                .orElseThrow(() -> new NoSuchElementException("Suite run not found: " + suiteRunId));
        return toSuiteRunRecord(suiteRun);
    }

    @Transactional
    public RunRecord startRun(@Valid StartRunRequest request) {
        return executeCase(request, Map.of(), null, null, null, null);
    }

    @Transactional
    public SuiteRunRecord startSuiteRun(@Valid StartSuiteRunRequest request) {
        SuiteRecord suite = catalogService.getSuite(request.suiteId());
        List<SuiteCaseRecord> enabledCases = suite.cases().stream()
                .filter(SuiteCaseRecord::enabled)
                .toList();
        if (enabledCases.isEmpty()) {
            throw new IllegalArgumentException("Suite does not contain any enabled scenarios");
        }

        Instant startedAt = Instant.now();
        SuiteRunEntity suiteRun = new SuiteRunEntity();
        suiteRun.setProjectId(suite.projectId());
        suiteRun.setSuiteId(suite.id());
        suiteRun.setSuiteName(suite.name());
        suiteRun.setTriggeredBy(normalizeTriggeredBy(request.triggeredBy()));
        suiteRun.setStatus("RUNNING");
        suiteRun.setStartedAt(startedAt);
        suiteRun.setFinishedAt(startedAt);
        suiteRun.setDurationMs(0);
        suiteRun = suiteRunRepository.save(suiteRun);

        List<RunRecord> childRuns = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (SuiteCaseRecord suiteCase : enabledCases) {
            try {
                RunRecord childRun = executeCase(
                        new StartRunRequest(
                                suiteCase.caseId(),
                                suiteCase.environmentId(),
                                suiteCase.profileId(),
                                suiteRun.getTriggeredBy()),
                        suiteCase.variableOverrides(),
                        suiteRun.getId(),
                        suiteCase.id(),
                        suiteCase.displayName(),
                        suiteCase.order());
                childRuns.add(childRun);
                if (!"PASSED".equalsIgnoreCase(childRun.status())
                        && childRun.errorMessage() != null
                        && !childRun.errorMessage().isBlank()) {
                    errors.add((suiteCase.displayName() == null || suiteCase.displayName().isBlank()
                            ? suiteCase.caseName()
                            : suiteCase.displayName()) + ": " + childRun.errorMessage());
                }
            } catch (RuntimeException error) {
                RunRecord failedRun = saveFailedSuiteCaseRun(suite, suiteRun, suiteCase, error);
                childRuns.add(failedRun);
                errors.add((suiteCase.displayName() == null || suiteCase.displayName().isBlank()
                        ? suiteCase.caseName()
                        : suiteCase.displayName()) + ": " + error.getMessage());
            }
        }

        Instant finishedAt = Instant.now();
        boolean passed = childRuns.stream().allMatch(run -> "PASSED".equalsIgnoreCase(run.status()));
        suiteRun.setStatus(passed ? "PASSED" : "FAILED");
        suiteRun.setFinishedAt(finishedAt);
        suiteRun.setDurationMs(Math.max(0, finishedAt.toEpochMilli() - startedAt.toEpochMilli()));
        suiteRun.setErrorMessage(errors.isEmpty() ? null : String.join("\n", errors));
        suiteRun = suiteRunRepository.save(suiteRun);
        return toSuiteRunRecord(suiteRun, childRuns);
    }

    private RunRecord executeCase(
            StartRunRequest request,
            Map<String, Object> variableOverrides,
            String suiteRunId,
            String suiteCaseId,
            String suiteDisplayName,
            Integer suiteCaseOrder) {
        CaseRecord testCase = catalogService.getCase(request.caseId());
        String environmentId = firstNonBlank(request.environmentId(), testCase.defaultEnvironmentId());
        String profileId = firstNonBlank(request.profileId(), testCase.defaultProfileId());
        if (environmentId == null || profileId == null) {
            throw new IllegalArgumentException(
                    "Scenario does not have a complete default environment and profile");
        }
        EnvironmentRecord environment = catalogService.getEnvironment(environmentId);
        ProfileRecord profile = catalogService.getProfile(profileId);

        if (!environment.projectId().equals(testCase.projectId())) {
            throw new IllegalArgumentException("Environment does not belong to the same project as the case");
        }
        if (!profile.projectId().equals(testCase.projectId())) {
            throw new IllegalArgumentException("Profile does not belong to the same project as the case");
        }
        if (!"ACTIVE".equalsIgnoreCase(environment.status())) {
            throw new IllegalArgumentException("Environment is inactive: " + environment.name());
        }
        if (!profile.enabled()) {
            throw new IllegalArgumentException("Profile is disabled: " + profile.name());
        }
        if (!profile.environmentIds().contains(environment.id())) {
            throw new IllegalArgumentException(
                    "Profile " + profile.name() + " is not available in environment " + environment.name());
        }

        String executableContent = prepareExecutableCase(testCase, environment, profile, variableOverrides);
        ApiCaseExecutionReport report = apiCaseRunner.executeInline(testCase.code() + ".yaml", executableContent);

        RunEntity runEntity = new RunEntity();
        runEntity.setProjectId(testCase.projectId());
        runEntity.setCaseId(testCase.id());
        runEntity.setSuiteRunId(suiteRunId);
        runEntity.setSuiteCaseId(suiteCaseId);
        runEntity.setSuiteDisplayName(suiteDisplayName);
        runEntity.setSuiteCaseOrder(suiteCaseOrder);
        runEntity.setEnvironmentId(environment.id());
        runEntity.setEnvironmentName(environment.name());
        runEntity.setProfileId(profile.id());
        runEntity.setProfileName(profile.name());
        runEntity.setTriggeredBy(normalizeTriggeredBy(request.triggeredBy()));
        runEntity.setStatus(report.getStatus());
        runEntity.setStartedAt(report.getStartedAt());
        runEntity.setFinishedAt(report.getFinishedAt());
        runEntity.setDurationMs(report.getDurationMs());
        runEntity.setErrorMessage(report.getErrorMessage());
        runEntity = runRepository.save(runEntity);

        RunEntity finalRunEntity = runEntity;
        List<StepRunEntity> stepEntities = report.getSteps().stream()
                .map(step -> toStepRunEntity(finalRunEntity.getId(), step))
                .toList();
        stepRunRepository.saveAll(stepEntities);
        return toRunRecord(runEntity, stepEntities);
    }

    private String prepareExecutableCase(
            CaseRecord managedCase,
            EnvironmentRecord environment,
            ProfileRecord profile,
            Map<String, Object> variableOverrides) {
        try {
            ApiTestCase testCase = yamlMapper.readValue(managedCase.content(), ApiTestCase.class);

            Map<String, Object> variables = new LinkedHashMap<>(testCase.getVariables());
            if (environment != null) {
                variables.putAll(environment.variables());
                variables.put("baseUrl", environment.baseUrl());
                environment.endpoints().forEach(
                        (apiSpecId, baseUrl) -> variables.put(apiSpecId + ".baseUrl", baseUrl));
            }
            variables.putAll(variableOverrides == null ? Map.of() : variableOverrides);
            testCase.setVariables(variables);

            if (profile != null) {
                CryptoProfile cryptoProfile = toCryptoProfile(profile);
                Map<String, CryptoProfile> profiles = new LinkedHashMap<>(testCase.getProfiles());
                profiles.put(profile.name(), cryptoProfile);
                testCase.setProfiles(profiles);
                RuntimeSpec runtime = testCase.getRuntime();
                if (runtime == null) {
                    runtime = new RuntimeSpec();
                    testCase.setRuntime(runtime);
                }
                runtime.setProfile(profile.name());
            }

            return yamlMapper.writeValueAsString(testCase);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to prepare executable case " + managedCase.code() + ": " + e.getOriginalMessage(),
                    e);
        }
    }

    private CryptoProfile toCryptoProfile(ProfileRecord profile) {
        CryptoProfile cryptoProfile = new CryptoProfile();
        cryptoProfile.setSenderPublicKey(profile.senderPublicKey());
        cryptoProfile.setSenderPrivateKey(profile.senderPrivateKey());
        cryptoProfile.setSenderPrivateKeyPassphrase(profile.senderPrivateKeyPassphrase());
        cryptoProfile.setRecipientPublicKey(profile.recipientPublicKey());
        cryptoProfile.setRecipientPrivateKey(profile.recipientPrivateKey());
        cryptoProfile.setRecipientPrivateKeyPassphrase(profile.recipientPrivateKeyPassphrase());
        return cryptoProfile;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }

    private String normalizeTriggeredBy(String triggeredBy) {
        return triggeredBy == null || triggeredBy.isBlank() ? "manual" : triggeredBy;
    }

    private RunRecord saveFailedSuiteCaseRun(
            SuiteRecord suite,
            SuiteRunEntity suiteRun,
            SuiteCaseRecord suiteCase,
            RuntimeException error) {
        CaseRecord testCase = catalogService.getCase(suiteCase.caseId());
        Instant now = Instant.now();
        RunEntity run = new RunEntity();
        run.setProjectId(suite.projectId());
        run.setCaseId(suiteCase.caseId());
        run.setSuiteRunId(suiteRun.getId());
        run.setSuiteCaseId(suiteCase.id());
        run.setSuiteDisplayName(suiteCase.displayName());
        run.setSuiteCaseOrder(suiteCase.order());
        run.setEnvironmentId(firstNonBlank(suiteCase.environmentId(), testCase.defaultEnvironmentId()));
        run.setProfileId(firstNonBlank(suiteCase.profileId(), testCase.defaultProfileId()));
        run.setTriggeredBy(suiteRun.getTriggeredBy());
        run.setStatus("FAILED");
        run.setStartedAt(now);
        run.setFinishedAt(now);
        run.setDurationMs(0);
        run.setErrorMessage(error.getMessage());
        run = runRepository.save(run);
        return toRunRecord(run, List.of());
    }

    private StepRunEntity toStepRunEntity(String runId, ApiStepExecutionReport step) {
        StepRunEntity entity = new StepRunEntity();
        entity.setRunId(runId);
        entity.setStepIndex(step.getIndex());
        entity.setStepName(step.getName());
        entity.setProfileName(step.getProfile());
        entity.setStatus(step.getStatus());
        entity.setErrorMessage(step.getErrorMessage());
        entity.setRequestJson(jsonCodec.write(step.getRequest()));
        entity.setResponseJson(jsonCodec.write(step.getResponse()));
        entity.setActionsJson(jsonCodec.write(step.getActions()));
        entity.setDecryptedPayloadsJson(jsonCodec.write(step.getDecryptedPayloads()));
        entity.setValidationsJson(jsonCodec.write(step.getValidations()));
        entity.setExtractedValuesJson(jsonCodec.write(step.getExtractedValues()));
        return entity;
    }

    private RunRecord toRunRecord(RunEntity runEntity, List<StepRunEntity> stepEntities) {
        return new RunRecord(
                runEntity.getId(),
                runEntity.getProjectId(),
                runEntity.getCaseId(),
                runEntity.getSuiteRunId(),
                runEntity.getSuiteCaseId(),
                runEntity.getSuiteDisplayName(),
                runEntity.getSuiteCaseOrder(),
                runEntity.getEnvironmentId(),
                runEntity.getEnvironmentName(),
                runEntity.getProfileId(),
                runEntity.getProfileName(),
                runEntity.getTriggeredBy(),
                runEntity.getStatus(),
                runEntity.getStartedAt(),
                runEntity.getFinishedAt(),
                runEntity.getDurationMs(),
                runEntity.getErrorMessage(),
                stepEntities.stream().map(this::toStepRecord).toList());
    }

    private SuiteRunRecord toSuiteRunRecord(SuiteRunEntity entity) {
        List<RunRecord> childRuns = runRepository.findBySuiteRunIdOrderBySuiteCaseOrderAsc(entity.getId()).stream()
                .map(run -> toRunRecord(run, stepRunRepository.findByRunIdOrderByStepIndexAsc(run.getId())))
                .toList();
        return toSuiteRunRecord(entity, childRuns);
    }

    private SuiteRunRecord toSuiteRunRecord(SuiteRunEntity entity, List<RunRecord> childRuns) {
        return new SuiteRunRecord(
                entity.getId(),
                entity.getProjectId(),
                entity.getSuiteId(),
                entity.getSuiteName(),
                entity.getTriggeredBy(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getDurationMs(),
                entity.getErrorMessage(),
                childRuns);
    }

    private StepRecord toStepRecord(StepRunEntity entity) {
        return new StepRecord(
                entity.getStepIndex(),
                entity.getStepName(),
                entity.getProfileName(),
                entity.getStatus(),
                entity.getErrorMessage(),
                jsonCodec.read(entity.getRequestJson(), new TypeReference<ReportRequest>() {}, null),
                jsonCodec.read(entity.getResponseJson(), new TypeReference<ReportResponse>() {}, null),
                jsonCodec.read(entity.getActionsJson(), new TypeReference<List<ActionExecutionReport>>() {}, List.of()),
                jsonCodec.readObjectMap(entity.getDecryptedPayloadsJson()),
                jsonCodec.read(entity.getValidationsJson(), new TypeReference<List<ValidationReport>>() {}, List.of()),
                jsonCodec.readObjectMap(entity.getExtractedValuesJson()));
    }

    public record StartRunRequest(
            @NotBlank String caseId,
            String environmentId,
            String profileId,
            String triggeredBy) {
    }

    public record StartSuiteRunRequest(
            @NotBlank String suiteId,
            String triggeredBy) {
    }

    public record RunRecord(
            String id,
            String projectId,
            String caseId,
            String suiteRunId,
            String suiteCaseId,
            String suiteDisplayName,
            Integer suiteCaseOrder,
            String environmentId,
            String environmentName,
            String profileId,
            String profileName,
            String triggeredBy,
            String status,
            Instant startedAt,
            Instant finishedAt,
            long durationMs,
            String errorMessage,
            List<StepRecord> steps) {
    }

    public record SuiteRunRecord(
            String id,
            String projectId,
            String suiteId,
            String suiteName,
            String triggeredBy,
            String status,
            Instant startedAt,
            Instant finishedAt,
            long durationMs,
            String errorMessage,
            List<RunRecord> runs) {
    }

    public record StepRecord(
            int index,
            String name,
            String profile,
            String status,
            String errorMessage,
            ReportRequest request,
            ReportResponse response,
            List<ActionExecutionReport> actions,
            Map<String, Object> decryptedPayloads,
            List<ValidationReport> validations,
            Map<String, Object> extractedValues) {
    }
}
