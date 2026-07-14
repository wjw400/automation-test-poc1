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
import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.RunEntity;
import com.example.poc.platform.persistence.entity.StepRunEntity;
import com.example.poc.platform.persistence.repo.RunRepository;
import com.example.poc.platform.persistence.repo.StepRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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
    private final JsonCodec jsonCodec;
    private final ApiCaseRunner apiCaseRunner = new ApiCaseRunner();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public RunExecutionService(
            PlatformCatalogService catalogService,
            RunRepository runRepository,
            StepRunRepository stepRunRepository,
            JsonCodec jsonCodec) {
        this.catalogService = catalogService;
        this.runRepository = runRepository;
        this.stepRunRepository = stepRunRepository;
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

    @Transactional
    public RunRecord startRun(@Valid StartRunRequest request) {
        CaseRecord testCase = catalogService.getCase(request.caseId());
        EnvironmentRecord environment = request.environmentId() == null || request.environmentId().isBlank()
                ? null
                : catalogService.getEnvironment(request.environmentId());
        ProfileRecord profile = request.profileId() == null || request.profileId().isBlank()
                ? null
                : catalogService.getProfile(request.profileId());

        if (environment != null && !environment.projectId().equals(testCase.projectId())) {
            throw new IllegalArgumentException("Environment does not belong to the same project as the case");
        }
        if (profile != null && !profile.projectId().equals(testCase.projectId())) {
            throw new IllegalArgumentException("Profile does not belong to the same project as the case");
        }

        String executableContent = prepareExecutableCase(testCase, environment, profile);
        ApiCaseExecutionReport report = apiCaseRunner.executeInline(testCase.code() + ".yaml", executableContent);

        RunEntity runEntity = new RunEntity();
        runEntity.setProjectId(testCase.projectId());
        runEntity.setCaseId(testCase.id());
        runEntity.setEnvironmentId(environment == null ? null : environment.id());
        runEntity.setProfileId(profile == null ? null : profile.id());
        runEntity.setTriggeredBy(request.triggeredBy() == null || request.triggeredBy().isBlank() ? "manual" : request.triggeredBy());
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

    private String prepareExecutableCase(CaseRecord managedCase, EnvironmentRecord environment, ProfileRecord profile) {
        try {
            ApiTestCase testCase = yamlMapper.readValue(managedCase.content(), ApiTestCase.class);

            Map<String, Object> variables = new LinkedHashMap<>(testCase.getVariables());
            if (environment != null) {
                variables.putAll(environment.variables());
                variables.put("baseUrl", environment.baseUrl());
            }
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
            throw new IllegalArgumentException("Failed to prepare executable case " + managedCase.code(), e);
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
                runEntity.getEnvironmentId(),
                runEntity.getProfileId(),
                runEntity.getTriggeredBy(),
                runEntity.getStatus(),
                runEntity.getStartedAt(),
                runEntity.getFinishedAt(),
                runEntity.getDurationMs(),
                runEntity.getErrorMessage(),
                stepEntities.stream().map(this::toStepRecord).toList());
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

    public record RunRecord(
            String id,
            String projectId,
            String caseId,
            String environmentId,
            String profileId,
            String triggeredBy,
            String status,
            Instant startedAt,
            Instant finishedAt,
            long durationMs,
            String errorMessage,
            List<StepRecord> steps) {
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
