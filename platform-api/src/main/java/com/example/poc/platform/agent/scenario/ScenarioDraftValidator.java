package com.example.poc.platform.agent.scenario;

import com.example.poc.engine.ApiStep;
import com.example.poc.engine.ApiTestCase;
import com.example.poc.engine.CaseActionExecutor;
import com.example.poc.engine.CaseActionSpec;
import com.example.poc.engine.CaseDslDataSources;
import com.example.poc.engine.ExtractValueSpec;
import com.example.poc.engine.HttpStatusSpec;
import com.example.poc.engine.OperationSpec;
import com.example.poc.engine.ValidationSpec;
import com.example.poc.platform.agent.scenario.ScenarioValidationResult.Severity;
import com.example.poc.platform.agent.scenario.ScenarioValidationResult.ValidationIssue;
import com.example.poc.platform.persistence.entity.ApiOperationEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ScenarioDraftValidator {
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}/]+)}");
    private static final Pattern VARIABLE_REFERENCE = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern POSTMAN_VARIABLE_REFERENCE = Pattern.compile("\\{\\{[^{}]+}}");
    private static final Set<String> ASSERTION_OPERATORS = Set.of(
            "equals", "notequals", "exists", "notexists", "contains", "greaterthan", "lessthan");

    private final ApiOperationRepository operationRepository;
    private final ScenarioDslCodec codec;
    private final ScenarioContractValidator contractValidator;

    public ScenarioDraftValidator(ApiOperationRepository operationRepository, ScenarioDslCodec codec, ScenarioContractValidator contractValidator) {
        this.operationRepository = operationRepository;
        this.codec = codec;
        this.contractValidator = contractValidator;
    }

    public ScenarioValidationResult validate(String projectId, ScenarioDocument document) {
        List<ValidationIssue> issues = new ArrayList<>();
        ApiTestCase scenario = document.scenario();

        if (!"v1".equals(scenario.getDslVersion())) {
            error(issues, "INVALID_DSL_VERSION", "/dslVersion", "dslVersion must be v1");
        }
        if (blank(scenario.getName())) {
            error(issues, "SCENARIO_NAME_REQUIRED", "/name", "Scenario name is required");
        }
        if (!scenario.getProfiles().isEmpty()) {
            error(issues, "EMBEDDED_PROFILE_FORBIDDEN", "/profiles", "Drafts may only reference managed Profiles by name or id");
        }
        if (scenario.getSteps().isEmpty()) {
            warning(issues, "SCENARIO_HAS_NO_STEPS", "/steps", "Scenario does not contain any request steps");
        }
        if (new HashSet<>(document.stepIds()).size() != document.stepIds().size()) {
            error(issues, "DUPLICATE_STEP_REFERENCE", "/steps", "Draft contains duplicate Step references");
        }

        Map<String, Integer> extractionStep = extractionSteps(scenario);
        Set<String> scenarioVariables = scenario.getVariables().keySet();
        for (int index = 0; index < scenario.getSteps().size(); index++) {
            String stepId = document.stepIds().get(index);
            String path = "/steps/" + pointer(stepId);
            validateStep(projectId, scenario.getSteps().get(index), index, path, extractionStep, scenarioVariables, issues);
        }
        return ScenarioValidationResult.of(issues);
    }

    private void validateStep(
            String projectId,
            ApiStep step,
            int stepIndex,
            String path,
            Map<String, Integer> extractionStep,
            Set<String> scenarioVariables,
            List<ValidationIssue> issues) {
        if (blank(step.getName())) {
            error(issues, "STEP_NAME_REQUIRED", path + "/name", "Step name is required");
        }
        if (!"http".equals(step.getType())) {
            error(issues, "UNSUPPORTED_STEP_TYPE", path + "/type", "Only http Steps are supported");
        }
        validateOperation(projectId, step.getOperation(), path + "/operation", issues);

        if (step.getRequest() == null) {
            error(issues, "REQUEST_REQUIRED", path + "/request", "HTTP Step request is required");
        } else {
            validatePathParameters(step, path, issues);
            validateVariableSyntax(codec.toGeneric(step.getRequest()), path + "/request", issues);
        }
        validateActions(step.getActions().getBeforeSend(), true, path + "/actions/beforeSend", issues);
        validateActions(step.getActions().getAfterReceive(), false, path + "/actions/afterReceive", issues);
        validateActions(step.getActions().getAfterRequestProcessing(), true, path + "/actions/afterRequestProcessing", issues);
        if (!java.util.List.of("automatic", "manual", "wire").contains(step.getProcessingMode())) error(issues, "PROCESSING_MODE_INVALID", path + "/processingMode", "Choose automatic, manual, or wire");

        if (step.getResponse() == null) {
            error(issues, "RESPONSE_REQUIRED", path + "/response", "HTTP Step response is required");
        } else {
            validateStatus(step.getResponse().getStatus(), path + "/response/status", issues);
            Set<String> responseSources = CaseDslDataSources.responseSourceNames(step);
            validateAssertions(
                    step.getResponse().getAssertions(), responseSources, path + "/response/assertions", issues);
            validateExtractions(
                    step.getResponse().getExtract(), responseSources, path + "/response/extract", issues);
        }
        validateVariableOrder(step, stepIndex, path, extractionStep, scenarioVariables, issues);
        if (step.getOperation() != null && !blank(step.getOperation().getId())) {
            operationRepository.findById(step.getOperation().getId())
                    .filter(operation -> projectId.equals(operation.getProjectId()))
                    .ifPresent(operation -> contractValidator.validate(operation, step, path, issues));
        }
    }

    private void validateStatus(HttpStatusSpec status, String path, List<ValidationIssue> issues) {
        if (status == null) {
            error(issues, "RESPONSE_STATUS_REQUIRED", path, "Response status rule is required");
            return;
        }
        try {
            status.validate();
        } catch (IllegalArgumentException exception) {
            error(issues, "RESPONSE_STATUS_INVALID", path, exception.getMessage());
        }
    }

    private void validateOperation(
            String projectId,
            OperationSpec operation,
            String path,
            List<ValidationIssue> issues) {
        if (operation == null || blank(operation.getId())) {
            error(issues, "OPERATION_REQUIRED", path + "/id", "Step must reference an imported Operation");
            return;
        }
        ApiOperationEntity entity = operationRepository.findById(operation.getId()).orElse(null);
        if (entity == null) {
            error(issues, "OPERATION_NOT_FOUND", path + "/id", "Referenced Operation does not exist");
            return;
        }
        if (!projectId.equals(entity.getProjectId())) {
            error(issues, "OPERATION_OUTSIDE_PROJECT", path + "/id", "Referenced Operation belongs to another Project");
            return;
        }
        if (!equalsIgnoreCase(entity.getMethod(), operation.getMethod()) || !entity.getPath().equals(operation.getPath())) {
            warning(issues, "OPERATION_SNAPSHOT_STALE", path, "Operation method or path differs from the current imported API Spec");
        }
    }

    private void validatePathParameters(ApiStep step, String path, List<ValidationIssue> issues) {
        String operationPath = step.getOperation() == null ? null : step.getOperation().getPath();
        if (blank(operationPath)) {
            return;
        }
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PATH_PARAMETER.matcher(operationPath);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        for (String placeholder : placeholders) {
            if (!step.getRequest().getPath().containsKey(placeholder)) {
                error(issues, "PATH_PARAMETER_REQUIRED", path + "/request/path/" + pointer(placeholder), "Missing path parameter: " + placeholder);
            }
        }
        for (String configured : step.getRequest().getPath().keySet()) {
            if (!placeholders.contains(configured)) {
                warning(issues, "UNUSED_PATH_PARAMETER", path + "/request/path/" + pointer(configured), "Operation path does not contain this parameter");
            }
        }
    }

    private void validateActions(
            List<CaseActionSpec> actions,
            boolean beforeSend,
            String path,
            List<ValidationIssue> issues) {
        List<String> allowed = beforeSend
                ? CaseActionExecutor.BEFORE_SEND_TYPES
                : CaseActionExecutor.AFTER_RECEIVE_TYPES;
        for (int index = 0; index < actions.size(); index++) {
            CaseActionSpec action = actions.get(index);
            String actionPath = path + "/" + index;
            if (blank(action.getType())) {
                error(issues, "ACTION_TYPE_REQUIRED", actionPath + "/type", "Action type is required");
                continue;
            }
            if (!allowed.contains(action.getType())) {
                error(issues, "ACTION_STAGE_INVALID", actionPath + "/type", "Action is not supported in this stage");
                continue;
            }
            if (CaseActionExecutor.BUILD_JSON.equals(action.getType())) {
                if (action.getTemplate() == null) {
                    error(issues, "ACTION_TEMPLATE_REQUIRED", actionPath + "/template", "body.buildJson requires a template");
                }
                if (!"request.body".equals(action.getOutput().getTarget())) {
                    error(issues, "ACTION_TARGET_INVALID", actionPath + "/output/target", "body.buildJson must output to request.body");
                }
                continue;
            }
            if (blank(action.getInput().getSource())) {
                error(issues, "ACTION_SOURCE_REQUIRED", actionPath + "/input/source", "Action input source is required");
            }
            if (blank(action.getOutput().getTarget())) {
                error(issues, "ACTION_TARGET_REQUIRED", actionPath + "/output/target", "Action output target is required");
            }
            if (beforeSend) {
                String source = action.getInput().getSource();
                if (!blank(source) && !("request.body".equals(source)
                        || source.startsWith("variables.")
                        || source.startsWith("artifacts."))) {
                    error(issues, "ACTION_SOURCE_INVALID", actionPath + "/input/source", "Unsupported Before input source");
                }
                String target = action.getOutput().getTarget();
                if (!blank(target) && !("request.body".equals(target) || target.startsWith("artifacts."))) {
                    error(issues, "ACTION_TARGET_INVALID", actionPath + "/output/target", "Unsupported Before output target");
                }
            }
            if ((CaseActionExecutor.DECRYPT_AND_VERIFY.equals(action.getType()) || CaseActionExecutor.DECRYPT.equals(action.getType()))
                    && (!"response.body".equals(action.getInput().getSource())
                    || blank(action.getOutput().getTarget())
                    || !action.getOutput().getTarget().startsWith("decrypted."))) {
                error(issues, "DECRYPT_ACTION_INVALID", actionPath, "Decrypt Action must read response.body and output to decrypted.<name>");
            }
            if ((CaseActionExecutor.SIGN_AND_ENCRYPT.equals(action.getType()) || CaseActionExecutor.ENCRYPT.equals(action.getType()))
                    && !blank(action.getOutput().getFormat())
                    && !"text".equalsIgnoreCase(action.getOutput().getFormat())) {
                error(issues, "ENCRYPT_ACTION_FORMAT_INVALID", actionPath + "/output/format", "PGP encrypted output format must be text");
            }
        }
    }

    private void validateAssertions(
            List<ValidationSpec> assertions,
            Set<String> availableSources,
            String path,
            List<ValidationIssue> issues) {
        for (int index = 0; index < assertions.size(); index++) {
            ValidationSpec assertion = assertions.get(index);
            String assertionPath = path + "/" + index;
            if (blank(assertion.getSource())) {
                error(issues, "ASSERTION_SOURCE_REQUIRED", assertionPath + "/source", "Assertion source is required");
            } else if (!availableSources.contains(assertion.getSource())) {
                error(
                        issues,
                        "ASSERTION_SOURCE_INVALID",
                        assertionPath + "/source",
                        unavailableResponseSource(assertion.getSource(), availableSources));
            }
            if (blank(assertion.getPath())) {
                error(issues, "ASSERTION_PATH_REQUIRED", assertionPath + "/path", "Assertion path is required");
            }
            String operator = blank(assertion.getOperator()) ? "equals" : assertion.getOperator().toLowerCase();
            if (!ASSERTION_OPERATORS.contains(operator)) {
                error(issues, "ASSERTION_OPERATOR_INVALID", assertionPath + "/operator", "Unsupported assertion operator");
            }
        }
    }

    private void validateExtractions(
            Map<String, Object> extract,
            Set<String> availableSources,
            String path,
            List<ValidationIssue> issues) {
        for (Map.Entry<String, Object> entry : extract.entrySet()) {
            String extractionPath = path + "/" + pointer(entry.getKey());
            if (blank(entry.getKey())) {
                error(issues, "EXTRACTION_NAME_REQUIRED", extractionPath, "Extraction name is required");
                continue;
            }
            try {
                ExtractValueSpec spec = codec.convert(entry.getValue(), ExtractValueSpec.class, extractionPath);
                if (blank(spec.getSource()) || blank(spec.getPath())) {
                    error(issues, "EXTRACTION_INVALID", extractionPath, "Extraction requires source and path");
                } else if (!availableSources.contains(spec.getSource())) {
                    error(
                            issues,
                            "EXTRACTION_SOURCE_INVALID",
                            extractionPath + "/source",
                            unavailableResponseSource(spec.getSource(), availableSources));
                }
            } catch (ScenarioCommandException exception) {
                error(issues, "EXTRACTION_INVALID", extractionPath, exception.getMessage());
            }
        }
    }

    private Map<String, Integer> extractionSteps(ApiTestCase scenario) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < scenario.getSteps().size(); index++) {
            ApiStep step = scenario.getSteps().get(index);
            if (step.getResponse() != null) {
                for (String name : step.getResponse().getExtract().keySet()) {
                    result.putIfAbsent(name, index);
                }
            }
        }
        return result;
    }

    private void validateVariableOrder(
            ApiStep step,
            int stepIndex,
            String path,
            Map<String, Integer> extractionStep,
            Set<String> scenarioVariables,
            List<ValidationIssue> issues) {
        Set<String> references = new LinkedHashSet<>();
        collectVariableReferences(codec.toGeneric(step), references);
        for (String reference : references) {
            String rootName = reference.contains(".") ? reference.substring(0, reference.indexOf('.')) : reference;
            Integer producerIndex = extractionStep.get(rootName);
            if (!scenarioVariables.contains(rootName) && producerIndex != null && producerIndex >= stepIndex) {
                error(issues, "VARIABLE_USED_BEFORE_EXTRACTION", path, "Variable " + rootName + " is used before it is extracted");
            }
        }
    }

    private void collectVariableReferences(Object value, Set<String> references) {
        if (value instanceof String text) {
            Matcher matcher = VARIABLE_REFERENCE.matcher(text);
            while (matcher.find()) {
                references.add(matcher.group(1));
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> collectVariableReferences(item, references));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> collectVariableReferences(item, references));
        }
    }

    private void validateVariableSyntax(Object value, String path, List<ValidationIssue> issues) {
        if (value instanceof String text) {
            if (POSTMAN_VARIABLE_REFERENCE.matcher(text).find()) {
                error(
                        issues,
                        "UNSUPPORTED_VARIABLE_SYNTAX",
                        path,
                        "Postman-style placeholders are not supported; use ${variableName} instead of {{variableName}}");
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> validateVariableSyntax(item, path + "/" + pointer(String.valueOf(key)), issues));
            return;
        }
        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                validateVariableSyntax(list.get(index), path + "/" + index, issues);
            }
        }
    }

    private void error(List<ValidationIssue> issues, String code, String path, String message) {
        issues.add(new ValidationIssue(Severity.ERROR, code, path, message));
    }

    private void warning(List<ValidationIssue> issues, String code, String path, String message) {
        issues.add(new ValidationIssue(Severity.WARNING, code, path, message));
    }

    private String unavailableResponseSource(String source, Set<String> availableSources) {
        String detail = CaseDslDataSources.isResponseSourceSyntax(source)
                ? "is not produced by an enabled afterReceive Action"
                : "is not supported by Case DSL v1";
        return "Response source " + source + " " + detail + "; available sources: " + availableSources;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    private String pointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }
}
