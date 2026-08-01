package com.example.poc.engine;

import com.example.poc.engine.report.ActionExecutionReport;
import com.example.poc.engine.report.ApiCaseExecutionReport;
import com.example.poc.engine.report.ApiCaseReportWriter;
import com.example.poc.engine.report.ApiStepExecutionReport;
import com.example.poc.engine.report.ReportRequest;
import com.example.poc.engine.report.ReportResponse;
import com.example.poc.engine.report.ValidationReport;
import com.example.poc.pgp.PgpCryptoService;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class ApiCaseRunner {
    private final ObjectMapper yamlMapper = YAMLMapper.builder().build();
    private final ObjectMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final VariableResolver variableResolver = new VariableResolver();
    private final ApiCaseReportWriter reportWriter = new ApiCaseReportWriter();
    private final CaseActionExecutor actionExecutor = new CaseActionExecutor(
            jsonMapper,
            variableResolver,
            new PgpCryptoService());

    public ApiCaseExecutionReport run(String caseFile) {
        return runInternal(caseFile, () -> loadCase(caseFile), true);
    }

    public ApiCaseExecutionReport executeInline(String caseRef, String caseContent) {
        return runInternal(caseRef, () -> loadInlineCase(caseRef, caseContent), false);
    }

    public ApiTestCase parseInlineCase(String caseRef, String caseContent) {
        return loadInlineCase(caseRef, caseContent);
    }

    private ApiCaseExecutionReport runInternal(
            String caseRef,
            Supplier<ApiTestCase> caseLoader,
            boolean propagateFailure) {
        ApiCaseExecutionReport report = new ApiCaseExecutionReport();
        report.setCaseFile(caseRef);
        report.setStartedAt(Instant.now());
        Throwable failure = null;
        TestContext context = new TestContext();

        try {
            ApiTestCase testCase = caseLoader.get();
            report.setCaseName(testCase.getName());
            testCase.getVariables().forEach(
                    (key, value) -> context.put(key, variableResolver.resolveObject(value, context)));

            for (int i = 0; i < testCase.getSteps().size(); i++) {
                ApiStep step = testCase.getSteps().get(i);
                ApiStepExecutionReport stepReport = new ApiStepExecutionReport();
                stepReport.setIndex(i + 1);
                stepReport.setName(stepName(step, i));
                stepReport.setProfile(resolveProfileName(testCase, step));
                report.getSteps().add(stepReport);

                try {
                    executeStep(testCase, step, context, stepReport);
                } catch (Throwable stepFailure) {
                    stepReport.setStatus("FAILED");
                    stepReport.setErrorMessage(stepFailure.getMessage());
                    rethrow(stepFailure);
                }
            }
        } catch (Throwable e) {
            failure = e;
        } finally {
            report.setFinishedAt(Instant.now());
            report.setDurationMs(Duration.between(report.getStartedAt(), report.getFinishedAt()).toMillis());
            if (failure != null) {
                report.setStatus("FAILED");
                report.setErrorMessage(failure.getMessage());
            }
            if (report.getCaseName() == null || report.getCaseName().isBlank()) {
                report.setCaseName(caseRef);
            }
            reportWriter.write(report);
        }

        if (propagateFailure) {
            rethrow(failure);
        }
        return report;
    }

    private void executeStep(
            ApiTestCase testCase,
            ApiStep step,
            TestContext context,
            ApiStepExecutionReport stepReport) throws IOException, InterruptedException {
        CryptoProfile profile = resolveProfile(testCase, step, context);
        PreparedRequest prepared = prepareRequest(step, context);
        ActionExecutionState actionState = new ActionExecutionState();
        actionState.setRequestBody(prepared.body());

        executeActions(
                "beforeSend",
                step.getActions().getBeforeSend(),
                actionState,
                context,
                profile,
                stepReport);

        HttpResponse<String> response = send(prepared, actionState.getRequestBody(), stepReport);
        stepReport.setResponse(toReportResponse(response));
        actionState.setResponseBody(response.body());

        executeActions(
                "afterReceive",
                step.getActions().getAfterReceive(),
                actionState,
                context,
                profile,
                stepReport);
        stepReport.getDecryptedPayloads().putAll(actionState.getDecryptedPayloads());

        assertResponse(testCase, step, response, context, stepReport, actionState.getDecryptedPayloads());
        extractValues(step, response.body(), context, stepReport, actionState.getDecryptedPayloads());
    }

    private ApiTestCase loadCase(String caseFile) {
        String resourcePath = "api-cases/" + caseFile;
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("API case resource not found: " + resourcePath);
            }
            ApiTestCase testCase = yamlMapper.readValue(inputStream, ApiTestCase.class);
            validate(testCase, caseFile);
            return testCase;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load API case: " + resourcePath, e);
        }
    }

    private ApiTestCase loadInlineCase(String caseRef, String caseContent) {
        try {
            ApiTestCase testCase = yamlMapper.readValue(caseContent, ApiTestCase.class);
            validate(testCase, caseRef);
            return testCase;
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to parse API case: " + caseRef, e);
        }
    }

    private void validate(ApiTestCase testCase, String caseRef) {
        if (!"v1".equals(testCase.getDslVersion())) {
            throw new IllegalArgumentException("dslVersion must be v1 in " + caseRef);
        }
        if (testCase.getSteps().isEmpty()) {
            throw new IllegalArgumentException("Missing steps in " + caseRef);
        }
        for (int i = 0; i < testCase.getSteps().size(); i++) {
            ApiStep step = testCase.getSteps().get(i);
            String prefix = "Step " + (i + 1) + " in " + caseRef;
            if (!"http".equals(step.getType())) {
                throw new IllegalArgumentException(prefix + " has unsupported type " + step.getType());
            }
            if (step.getOperation() == null || isBlank(step.getOperation().getMethod())) {
                throw new IllegalArgumentException(prefix + " is missing operation.method");
            }
            if (isBlank(step.getOperation().getPath())) {
                throw new IllegalArgumentException(prefix + " is missing operation.path");
            }
            if (step.getRequest() == null) {
                throw new IllegalArgumentException(prefix + " is missing request");
            }
            if (step.getResponse() == null) {
                throw new IllegalArgumentException(prefix + " is missing response");
            }
            validateActions(step.getActions().getBeforeSend(), "beforeSend", prefix);
            validateActions(step.getActions().getAfterReceive(), "afterReceive", prefix);
            validateAssertions(step.getResponse().getAssertions(), prefix);
            validateExtract(step.getResponse().getExtract(), prefix);

            if (hasCryptoAction(step)) {
                String profileName = resolveProfileName(testCase, step);
                if (isBlank(profileName)) {
                    throw new IllegalArgumentException(prefix + " has a crypto action but no Step or Scenario profile");
                }
                if (!testCase.getProfiles().containsKey(profileName)) {
                    throw new IllegalArgumentException(prefix + " references unknown profile " + profileName);
                }
            }
        }
    }

    private void validateActions(List<CaseActionSpec> actions, String stage, String prefix) {
        List<String> allowed = "beforeSend".equals(stage)
                ? CaseActionExecutor.BEFORE_SEND_TYPES
                : CaseActionExecutor.AFTER_RECEIVE_TYPES;
        for (int i = 0; i < actions.size(); i++) {
            CaseActionSpec action = actions.get(i);
            String actionPrefix = prefix + " actions." + stage + "[" + i + "]";
            if (isBlank(action.getType())) {
                throw new IllegalArgumentException(actionPrefix + " is missing type");
            }
            if (!allowed.contains(action.getType())) {
                throw new IllegalArgumentException(actionPrefix + " does not support " + action.getType());
            }
            if (CaseActionExecutor.BUILD_JSON.equals(action.getType())) {
                if (action.getTemplate() == null) {
                    throw new IllegalArgumentException(actionPrefix + " is missing template");
                }
                if (!"request.body".equals(action.getOutput().getTarget())) {
                    throw new IllegalArgumentException(actionPrefix + " must output to request.body");
                }
                continue;
            }
            if (isBlank(action.getInput().getSource())) {
                throw new IllegalArgumentException(actionPrefix + " is missing input.source");
            }
            if (isBlank(action.getOutput().getTarget())) {
                throw new IllegalArgumentException(actionPrefix + " is missing output.target");
            }
            if ("beforeSend".equals(stage)) {
                String source = action.getInput().getSource();
                if (!("request.body".equals(source)
                        || source.startsWith("variables.")
                        || source.startsWith("artifacts."))) {
                    throw new IllegalArgumentException(actionPrefix + " has unsupported Before input source " + source);
                }
                String target = action.getOutput().getTarget();
                if (!("request.body".equals(target) || target.startsWith("artifacts."))) {
                    throw new IllegalArgumentException(actionPrefix + " has unsupported Before output target " + target);
                }
            }
            if (CaseActionExecutor.DECRYPT_AND_VERIFY.equals(action.getType())
                    && (!"response.body".equals(action.getInput().getSource())
                    || !action.getOutput().getTarget().startsWith("decrypted."))) {
                throw new IllegalArgumentException(
                        actionPrefix + " must read response.body and output to decrypted.<name>");
            }
            if (CaseActionExecutor.SIGN_AND_ENCRYPT.equals(action.getType())
                    && !isBlank(action.getOutput().getFormat())
                    && !"text".equalsIgnoreCase(action.getOutput().getFormat())) {
                throw new IllegalArgumentException(actionPrefix + " only supports text output");
            }
        }
    }

    private void validateAssertions(List<ValidationSpec> assertions, String prefix) {
        for (ValidationSpec assertion : assertions) {
            if (isBlank(assertion.getSource())) {
                throw new IllegalArgumentException(prefix + " has an assertion without source");
            }
            if (isBlank(assertion.getPath())) {
                throw new IllegalArgumentException(prefix + " has an assertion without path");
            }
        }
    }

    private void validateExtract(Map<String, Object> extract, String prefix) {
        for (Map.Entry<String, Object> entry : extract.entrySet()) {
            ExtractValueSpec spec = toExtractSpec(entry.getValue());
            if (isBlank(spec.getSource()) || isBlank(spec.getPath())) {
                throw new IllegalArgumentException(prefix + " extract " + entry.getKey() + " requires source and path");
            }
        }
    }

    private PreparedRequest prepareRequest(ApiStep step, TestContext context) {
        String method = variableResolver.resolveString(step.getOperation().getMethod(), context).toUpperCase();
        URI uri = buildUri(step, context);
        Map<String, String> headers = new LinkedHashMap<>();
        step.getRequest().getHeaders().forEach(
                (name, value) -> headers.put(name, variableResolver.resolveString(value, context)));
        Object body = variableResolver.resolveObject(step.getRequest().getBody(), context);
        return new PreparedRequest(method, uri, headers, body);
    }

    private URI buildUri(ApiStep step, TestContext context) {
        String baseUrl = resolveBaseUrl(step.getOperation(), context);
        String path = step.getOperation().getPath();
        for (Map.Entry<String, Object> entry : step.getRequest().getPath().entrySet()) {
            Object resolvedValue = variableResolver.resolveObject(entry.getValue(), context);
            path = path.replace("{" + entry.getKey() + "}", encodePathSegment(String.valueOf(resolvedValue)));
        }
        path = variableResolver.resolveString(path, context);
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String query = step.getRequest().getQuery().entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "="
                        + encode(String.valueOf(variableResolver.resolveObject(entry.getValue(), context))))
                .collect(Collectors.joining("&"));
        return URI.create(trimTrailingSlash(baseUrl) + normalizedPath + (query.isBlank() ? "" : "?" + query));
    }

    private String resolveBaseUrl(OperationSpec operation, TestContext context) {
        if (!isBlank(operation.getApiSpecId())) {
            Object apiSpecBaseUrl = context.get(operation.getApiSpecId() + ".baseUrl").orElse(null);
            if (apiSpecBaseUrl != null) {
                return String.valueOf(apiSpecBaseUrl);
            }
        }
        if (!isBlank(operation.getService())) {
            Object serviceBaseUrl = context.get(operation.getService() + ".baseUrl").orElse(null);
            if (serviceBaseUrl != null) {
                return String.valueOf(serviceBaseUrl);
            }
        }
        return context.get("baseUrl")
                .map(String::valueOf)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No baseUrl is available for operation " + operation.getOperationId()));
    }

    private HttpResponse<String> send(
            PreparedRequest prepared,
            Object wireBody,
            ApiStepExecutionReport stepReport) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(prepared.uri());
        ReportRequest reportRequest = new ReportRequest();
        reportRequest.setMethod(prepared.method());
        reportRequest.setUrl(prepared.uri().toString());
        prepared.headers().forEach((name, value) -> {
            builder.header(name, value);
            reportRequest.getHeaders().put(name, value);
        });
        if (wireBody == null) {
            builder.method(prepared.method(), HttpRequest.BodyPublishers.noBody());
        } else {
            reportRequest.setBody(wireBody);
            builder.method(prepared.method(), HttpRequest.BodyPublishers.ofString(serializeWireBody(wireBody)));
        }
        stepReport.setRequest(reportRequest);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void executeActions(
            String stage,
            List<CaseActionSpec> actions,
            ActionExecutionState state,
            TestContext context,
            CryptoProfile profile,
            ApiStepExecutionReport stepReport) {
        for (int i = 0; i < actions.size(); i++) {
            CaseActionSpec action = actions.get(i);
            ActionExecutionReport actionReport = new ActionExecutionReport();
            actionReport.setStage(stage);
            actionReport.setIndex(i + 1);
            actionReport.setType(action.getType());
            actionReport.setName(isBlank(action.getName()) ? action.getType() : action.getName());
            stepReport.getActions().add(actionReport);

            if (!action.isEnabled()) {
                actionReport.setStatus("SKIPPED");
                continue;
            }

            long startedAt = System.nanoTime();
            try {
                Object input = actionExecutor.inspectInput(action, state, context);
                actionReport.setInput(input);
                actionReport.setOutput(actionExecutor.execute(action, state, profile, input));
                actionReport.setStatus("PASSED");
            } catch (RuntimeException error) {
                actionReport.setStatus("FAILED");
                actionReport.setErrorMessage(error.getMessage());
                throw error;
            } finally {
                actionReport.setDurationMs(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            }
        }
    }

    private void assertResponse(
            ApiTestCase testCase,
            ApiStep step,
            HttpResponse<String> response,
            TestContext context,
            ApiStepExecutionReport stepReport,
            Map<String, Object> decryptedPayloads) {
        boolean hasFailure = false;
        int expectedStatus = step.getResponse().getStatus();
        int actualStatus = response.statusCode();
        if (actualStatus == expectedStatus) {
            stepReport.getValidations().add(
                    ValidationReport.passed("status", "response.status", expectedStatus, actualStatus));
        } else {
            hasFailure = true;
            stepReport.getValidations().add(ValidationReport.failed(
                    "status",
                    "response.status",
                    expectedStatus,
                    actualStatus,
                    "Expected HTTP status " + expectedStatus + " but got " + actualStatus));
        }

        for (ValidationSpec assertion : step.getResponse().getAssertions()) {
            Object expected = variableResolver.resolveObject(assertion.getExpected(), context);
            try {
                Object actual = readFromSource(
                        assertion.getSource(), assertion.getPath(), response.body(), decryptedPayloads);
                if (matches(assertion.getOperator(), actual, expected)) {
                    stepReport.getValidations().add(ValidationReport.passed(
                            assertion.getOperator(), validationTarget(assertion), expected, actual));
                } else {
                    hasFailure = true;
                    stepReport.getValidations().add(ValidationReport.failed(
                            assertion.getOperator(),
                            validationTarget(assertion),
                            expected,
                            actual,
                            "Expected assertion did not match actual value"));
                }
            } catch (RuntimeException e) {
                hasFailure = true;
                stepReport.getValidations().add(ValidationReport.failed(
                        assertion.getOperator(), validationTarget(assertion), expected, null, e.getMessage()));
            }
        }

        if (hasFailure) {
            throw new AssertionError("API case validation failed: " + testCase.getName() + " / " + step.getName());
        }
    }

    private void extractValues(
            ApiStep step,
            String responseBody,
            TestContext context,
            ApiStepExecutionReport stepReport,
            Map<String, Object> decryptedPayloads) {
        step.getResponse().getExtract().forEach((name, rawSpec) -> {
            ExtractValueSpec spec = toExtractSpec(rawSpec);
            Object value = readFromSource(spec.getSource(), spec.getPath(), responseBody, decryptedPayloads);
            context.put(name, value);
            stepReport.getExtractedValues().put(name, value);
        });
    }

    private Object readFromSource(
            String source,
            String path,
            String responseBody,
            Map<String, Object> decryptedPayloads) {
        Object value;
        if ("response.body".equals(source)) {
            value = responseBody;
        } else if (source != null && source.startsWith("decrypted.")) {
            String payloadName = source.substring("decrypted.".length());
            if (!decryptedPayloads.containsKey(payloadName)) {
                throw new IllegalArgumentException("Unknown decrypted payload source: " + source);
            }
            value = decryptedPayloads.get(payloadName);
        } else {
            throw new IllegalArgumentException("Unsupported assertion/extract source: " + source);
        }
        if (isBlank(path)) {
            return value;
        }
        return value instanceof String text
                ? JsonPath.read(text, path)
                : JsonPath.read(value, path);
    }

    private ExtractValueSpec toExtractSpec(Object rawSpec) {
        return jsonMapper.convertValue(rawSpec, ExtractValueSpec.class);
    }

    private CryptoProfile resolveProfile(ApiTestCase testCase, ApiStep step, TestContext context) {
        String profileName = resolveProfileName(testCase, step);
        if (isBlank(profileName)) {
            return null;
        }
        CryptoProfile source = testCase.getProfiles().get(profileName);
        if (source == null) {
            throw new IllegalArgumentException("Unknown profile: " + profileName);
        }
        CryptoProfile resolved = new CryptoProfile();
        resolved.setSenderPublicKey(resolveProfileValue(source.getSenderPublicKey(), context));
        resolved.setSenderPrivateKey(resolveProfileValue(source.getSenderPrivateKey(), context));
        resolved.setSenderPrivateKeyPassphrase(resolveProfileValue(source.getSenderPrivateKeyPassphrase(), context));
        resolved.setRecipientPublicKey(resolveProfileValue(source.getRecipientPublicKey(), context));
        resolved.setRecipientPrivateKey(resolveProfileValue(source.getRecipientPrivateKey(), context));
        resolved.setRecipientPrivateKeyPassphrase(resolveProfileValue(source.getRecipientPrivateKeyPassphrase(), context));
        return resolved;
    }

    private String resolveProfileName(ApiTestCase testCase, ApiStep step) {
        if (step.getRuntime() != null && !isBlank(step.getRuntime().getProfile())) {
            return step.getRuntime().getProfile();
        }
        return testCase.getRuntime() == null ? null : testCase.getRuntime().getProfile();
    }

    private boolean hasCryptoAction(ApiStep step) {
        return step.getActions().getBeforeSend().stream().anyMatch(this::isCryptoAction)
                || step.getActions().getAfterReceive().stream().anyMatch(this::isCryptoAction);
    }

    private boolean isCryptoAction(CaseActionSpec action) {
        return CaseActionExecutor.SIGN_AND_ENCRYPT.equals(action.getType())
                || CaseActionExecutor.DECRYPT_AND_VERIFY.equals(action.getType());
    }

    private boolean matches(String operator, Object actual, Object expected) {
        String resolvedOperator = isBlank(operator) ? "equals" : operator;
        if ("equals".equalsIgnoreCase(resolvedOperator)) {
            return Objects.equals(normalizeNumber(actual), normalizeNumber(expected));
        }
        if ("notEquals".equalsIgnoreCase(resolvedOperator)) {
            return !Objects.equals(normalizeNumber(actual), normalizeNumber(expected));
        }
        if ("exists".equalsIgnoreCase(resolvedOperator)) {
            return actual != null;
        }
        if ("notExists".equalsIgnoreCase(resolvedOperator)) {
            return actual == null;
        }
        if ("contains".equalsIgnoreCase(resolvedOperator)) {
            if (actual instanceof String text) {
                return text.contains(String.valueOf(expected));
            }
            if (actual instanceof List<?> list) {
                return list.contains(expected);
            }
            return false;
        }
        if ("greaterThan".equalsIgnoreCase(resolvedOperator)) {
            return toDouble(actual) > toDouble(expected);
        }
        if ("lessThan".equalsIgnoreCase(resolvedOperator)) {
            return toDouble(actual) < toDouble(expected);
        }
        throw new IllegalArgumentException("Unsupported assertion operator: " + operator);
    }

    private Object normalizeNumber(Object value) {
        return value instanceof Number number ? number.doubleValue() : value;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private ReportResponse toReportResponse(HttpResponse<String> response) {
        ReportResponse reportResponse = new ReportResponse();
        reportResponse.setStatusCode(response.statusCode());
        reportResponse.setHeaders(response.headers().map());
        reportResponse.setBody(response.body());
        return reportResponse;
    }

    private String serializeWireBody(Object wireBody) {
        try {
            return wireBody instanceof String text ? text : jsonMapper.writeValueAsString(wireBody);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize wire request body", e);
        }
    }

    private String resolveProfileValue(String value, TestContext context) {
        return value == null ? null : variableResolver.resolveString(value, context);
    }

    private String validationTarget(ValidationSpec validation) {
        return validation.getSource() + " " + validation.getPath();
    }

    private String stepName(ApiStep step, int index) {
        return isBlank(step.getName()) ? "Step " + (index + 1) : step.getName();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void rethrow(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        if (throwable instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("API step execution was interrupted", throwable);
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException("API step execution failed", throwable);
    }

    private record PreparedRequest(
            String method,
            URI uri,
            Map<String, String> headers,
            Object body) {
    }
}
