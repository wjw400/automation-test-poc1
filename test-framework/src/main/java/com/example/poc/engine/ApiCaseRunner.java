package com.example.poc.engine;

import com.example.poc.engine.report.ApiCaseExecutionReport;
import com.example.poc.engine.report.ApiCaseReportWriter;
import com.example.poc.engine.report.ApiStepExecutionReport;
import com.example.poc.engine.report.ReportRequest;
import com.example.poc.engine.report.ReportResponse;
import com.example.poc.engine.report.ValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.example.poc.pgp.PgpCryptoService;
import com.example.poc.pgp.PgpProfileMaterial;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ApiCaseRunner {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final VariableResolver variableResolver = new VariableResolver();
    private final ApiCaseReportWriter reportWriter = new ApiCaseReportWriter();
    private final FakeDecryptor fakeDecryptor = new FakeDecryptor();
    private final PgpCryptoService pgpCryptoService = new PgpCryptoService();

    public ApiCaseExecutionReport run(String caseFile) {
        return runInternal(caseFile, () -> loadCase(caseFile), true);
    }

    public ApiCaseExecutionReport executeInline(String caseRef, String caseContent) {
        return runInternal(caseRef, () -> loadInlineCase(caseRef, caseContent), false);
    }

    public ApiTestCase parseInlineCase(String caseRef, String caseContent) {
        return loadInlineCase(caseRef, caseContent);
    }

    private ApiCaseExecutionReport runInternal(String caseRef, Supplier<ApiTestCase> caseLoader, boolean propagateFailure) {
        ApiCaseExecutionReport report = new ApiCaseExecutionReport();
        report.setCaseFile(caseRef);
        report.setStartedAt(java.time.Instant.now());
        Throwable failure = null;
        TestContext context = new TestContext();

        try {
            ApiTestCase testCase = caseLoader.get();
            report.setCaseName(testCase.getName());
            testCase.getVariables().forEach((key, value) -> context.put(key, variableResolver.resolveObject(value, context)));

            List<ApiStep> steps = resolveSteps(testCase);
            boolean caseHasStepFailure = false;
            for (int i = 0; i < steps.size(); i++) {
                ApiStep step = steps.get(i);
                ApiStepExecutionReport stepReport = new ApiStepExecutionReport();
                stepReport.setIndex(i + 1);
                stepReport.setName(stepName(step, i));
                stepReport.setProfile(step.getProfile());
                report.getSteps().add(stepReport);

                try {
                    CryptoProfile profile = resolveProfile(testCase, step, context);
                    HttpExchange exchange = execute(step.getRequest(), context, stepReport, profile);
                    stepReport.setResponse(toReportResponse(exchange.response()));
                    Map<String, Object> decryptedPayloads = decryptPayloads(step, exchange.response().body(), stepReport, profile);
                    assertResponse(testCase, step, exchange.response(), context, stepReport, decryptedPayloads);
                    extractValues(step, exchange.response().body(), context, stepReport, decryptedPayloads);
                } catch (Throwable stepFailure) {
                    caseHasStepFailure = true;
                    stepReport.setStatus("FAILED");
                    stepReport.setErrorMessage(stepFailure.getMessage());
                    if (!step.isContinueOnFailure()) {
                        rethrow(stepFailure);
                    }
                }
            }
            if (caseHasStepFailure) {
                throw new AssertionError("API case has failed step(s): " + testCase.getName());
            }
        } catch (Throwable e) {
            failure = e;
        } finally {
            report.setFinishedAt(java.time.Instant.now());
            report.setDurationMs(java.time.Duration.between(report.getStartedAt(), report.getFinishedAt()).toMillis());
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
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure != null) {
                throw new RuntimeException(failure);
            }
        }
        return report;
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
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse API case: " + caseRef, e);
        }
    }

    private void validate(ApiTestCase testCase, String caseFile) {
        List<ApiStep> steps = resolveSteps(testCase);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Missing steps or request section in " + caseFile);
        }
        for (int i = 0; i < steps.size(); i++) {
            ApiStep step = steps.get(i);
            String prefix = "Step " + (i + 1) + " in " + caseFile;
            if (isBlank(step.getProfile())) {
                throw new IllegalArgumentException(prefix + " is missing profile");
            }
            if (!testCase.getProfiles().containsKey(step.getProfile())) {
                throw new IllegalArgumentException(prefix + " references unknown profile " + step.getProfile());
            }
            if (step.getRequest() == null) {
                throw new IllegalArgumentException(prefix + " is missing request section");
            }
            if (step.getExpect() == null) {
                throw new IllegalArgumentException(prefix + " is missing expect section");
            }
            if (isBlank(step.getRequest().getMethod())) {
                throw new IllegalArgumentException(prefix + " is missing request.method");
            }
            if (isBlank(step.getRequest().getBaseUrl())) {
                throw new IllegalArgumentException(prefix + " is missing request.baseUrl");
            }
            validateTransform(step.getRequest().getTransform(), prefix);
            validateExpect(step.getExpect(), prefix);
            validateExtract(step.getExtract(), prefix);
        }
    }

    private void validateTransform(RequestTransformSpec transform, String prefix) {
        if (transform == null) {
            return;
        }
        if (isBlank(transform.getType())) {
            throw new IllegalArgumentException(prefix + " has a request transform without type");
        }
        if (!"pgp".equalsIgnoreCase(transform.getType())) {
            throw new IllegalArgumentException(prefix + " has unsupported request transform type " + transform.getType());
        }
        if ("jsonField".equalsIgnoreCase(transform.getOutput().getMode()) && isBlank(transform.getOutput().getField())) {
            throw new IllegalArgumentException(prefix + " request transform jsonField mode is missing output.field");
        }
    }

    private void validateExpect(ExpectSpec expect, String prefix) {
        for (DecryptSpec decryptSpec : expect.getDecrypt()) {
            if (isBlank(decryptSpec.getName())) {
                throw new IllegalArgumentException(prefix + " has a decrypt entry without name");
            }
            if (isBlank(resolveDecryptPath(decryptSpec.getInput()))) {
                throw new IllegalArgumentException(prefix + " decrypt entry " + decryptSpec.getName() + " is missing input field/path");
            }
        }
        for (ValidationSpec validation : resolveValidations(expect)) {
            if (isBlank(validation.getSource())) {
                throw new IllegalArgumentException(prefix + " has a validation without source");
            }
            if (isBlank(validation.getPath())) {
                throw new IllegalArgumentException(prefix + " has a validation without path");
            }
        }
    }

    private void validateExtract(Map<String, Object> extract, String prefix) {
        for (Map.Entry<String, Object> entry : extract.entrySet()) {
            ExtractValueSpec spec = toExtractSpec(entry.getValue());
            if (isBlank(spec.getPath())) {
                throw new IllegalArgumentException(prefix + " extract " + entry.getKey() + " is missing path");
            }
        }
    }

    private List<ApiStep> resolveSteps(ApiTestCase testCase) {
        if (!testCase.getSteps().isEmpty()) {
            return testCase.getSteps();
        }
        if (testCase.getRequest() == null && testCase.getExpect() == null) {
            return List.of();
        }
        ApiStep step = new ApiStep();
        step.setName(testCase.getName());
        step.setProfile("default");
        step.setRequest(testCase.getRequest());
        step.setExpect(testCase.getExpect());
        step.setExtract(testCase.getExtract());
        return List.of(step);
    }

    private HttpExchange execute(
            RequestSpec requestSpec,
            TestContext context,
            ApiStepExecutionReport stepReport,
            CryptoProfile profile) throws IOException, InterruptedException {
        String method = variableResolver.resolveString(requestSpec.getMethod(), context).toUpperCase();
        URI uri = buildUri(requestSpec, context);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);

        ReportRequest reportRequest = new ReportRequest();
        reportRequest.setMethod(method);
        reportRequest.setUrl(uri.toString());

        requestSpec.getHeaders().forEach((name, value) -> {
            String resolvedValue = variableResolver.resolveString(value, context);
            builder.header(name, resolvedValue);
            reportRequest.getHeaders().put(name, resolvedValue);
        });

        if (requestSpec.getBody() == null || "GET".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            Object resolvedBody = variableResolver.resolveObject(requestSpec.getBody(), context);
            Object wireBody = transformRequestBody(requestSpec, resolvedBody, profile);
            reportRequest.setBody(wireBody);
            builder.method(method, HttpRequest.BodyPublishers.ofString(serializeWireBody(wireBody)));
        }

        stepReport.setRequest(reportRequest);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new HttpExchange(reportRequest, response);
    }

    private URI buildUri(RequestSpec requestSpec, TestContext context) {
        String baseUrl = trimTrailingSlash(variableResolver.resolveString(requestSpec.getBaseUrl(), context));
        String path = Objects.toString(variableResolver.resolveString(nullToEmpty(requestSpec.getPath()), context), "");
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        String query = requestSpec.getQuery().entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(String.valueOf(variableResolver.resolveObject(entry.getValue(), context))))
                .collect(Collectors.joining("&"));
        String uri = baseUrl + normalizedPath + (query.isBlank() ? "" : "?" + query);
        return URI.create(uri);
    }

    private void assertResponse(
            ApiTestCase testCase,
            ApiStep step,
            HttpResponse<String> response,
            TestContext context,
            ApiStepExecutionReport stepReport,
            Map<String, Object> decryptedPayloads) {
        boolean hasFailure = false;
        int expectedStatus = step.getExpect().getStatus();
        int actualStatus = response.statusCode();
        if (actualStatus == expectedStatus) {
            stepReport.getValidations().add(ValidationReport.passed("status", "response.status", expectedStatus, actualStatus));
        } else {
            hasFailure = true;
            stepReport.getValidations().add(ValidationReport.failed(
                    "status",
                    "response.status",
                    expectedStatus,
                    actualStatus,
                    "Expected HTTP status " + expectedStatus + " but got " + actualStatus));
        }

        for (ValidationSpec validation : resolveValidations(step.getExpect())) {
            Object expected = variableResolver.resolveObject(validation.getExpected(), context);
            try {
                Object actual = readFromSource(validation.getSource(), validation.getPath(), response.body(), decryptedPayloads);
                if (matches(validation.getOperator(), actual, expected)) {
                    stepReport.getValidations().add(ValidationReport.passed(
                            validation.getOperator(),
                            validationTarget(validation),
                            expected,
                            actual));
                } else {
                    hasFailure = true;
                    stepReport.getValidations().add(ValidationReport.failed(
                            validation.getOperator(),
                            validationTarget(validation),
                            expected,
                            actual,
                            "Expected validation did not match actual value"));
                }
            } catch (RuntimeException e) {
                hasFailure = true;
                stepReport.getValidations().add(ValidationReport.failed(
                        validation.getOperator(),
                        validationTarget(validation),
                        expected,
                        null,
                        e.getMessage()));
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
        step.getExtract().forEach((name, rawSpec) -> {
            ExtractValueSpec spec = toExtractSpec(rawSpec);
            Object value = readFromSource(spec.getSource(), spec.getPath(), responseBody, decryptedPayloads);
            context.put(name, value);
            stepReport.getExtractedValues().put(name, value);
        });
    }

    private Map<String, Object> decryptPayloads(
            ApiStep step,
            String responseBody,
            ApiStepExecutionReport stepReport,
            CryptoProfile profile) {
        Map<String, Object> decryptedPayloads = new LinkedHashMap<>();
        for (DecryptSpec decryptSpec : step.getExpect().getDecrypt()) {
            Object encryptedValue = JsonPath.read(responseBody, resolveDecryptPath(decryptSpec.getInput()));
            String cipherText = Objects.toString(encryptedValue, null);
            Object decrypted = decrypt(cipherText, decryptSpec, profile);
            decryptedPayloads.put(decryptSpec.getName(), decrypted);
            stepReport.getDecryptedPayloads().put(decryptSpec.getName(), decrypted);
        }
        return decryptedPayloads;
    }

    private Object decrypt(String cipherText, DecryptSpec decryptSpec, CryptoProfile profile) {
        if ("fake".equalsIgnoreCase(decryptSpec.getAlgorithm())) {
            return fakeDecryptor.decrypt(cipherText, decryptSpec.getPlaintextFormat(), profile);
        }
        if ("pgp".equalsIgnoreCase(decryptSpec.getAlgorithm())) {
            String plaintext = pgpCryptoService.decryptAndVerify(cipherText, toPgpProfile(profile));
            return deserializePlaintext(plaintext, decryptSpec.getPlaintextFormat());
        }
        throw new IllegalArgumentException("Unsupported decrypt algorithm: " + decryptSpec.getAlgorithm());
    }

    private List<ValidationSpec> resolveValidations(ExpectSpec expect) {
        List<ValidationSpec> validations = new ArrayList<>();
        expect.getJsonPath().forEach((path, expected) -> {
            ValidationSpec validation = new ValidationSpec();
            validation.setSource("raw");
            validation.setPath(path);
            validation.setOperator("equals");
            validation.setExpected(expected);
            validations.add(validation);
        });
        validations.addAll(expect.getValidations());
        return validations;
    }

    private ExtractValueSpec toExtractSpec(Object rawSpec) {
        if (rawSpec instanceof String path) {
            ExtractValueSpec spec = new ExtractValueSpec();
            spec.setSource("raw");
            spec.setPath(path);
            return spec;
        }
        return jsonMapper.convertValue(rawSpec, ExtractValueSpec.class);
    }

    private Object readFromSource(
            String source,
            String path,
            String rawResponseBody,
            Map<String, Object> decryptedPayloads) {
        if ("raw".equals(source)) {
            return JsonPath.read(rawResponseBody, path);
        }
        String prefix = "decrypted.";
        if (source != null && source.startsWith(prefix)) {
            String payloadName = source.substring(prefix.length());
            Object payload = decryptedPayloads.get(payloadName);
            if (payload == null) {
                throw new IllegalArgumentException("Unknown decrypted payload source: " + source);
            }
            return JsonPath.read(payload, path);
        }
        throw new IllegalArgumentException("Unsupported validation/extract source: " + source);
    }

    private String resolveDecryptPath(DecryptInputSpec input) {
        if (input == null) {
            return null;
        }
        if (!isBlank(input.getPath())) {
            return input.getPath();
        }
        if ("jsonField".equalsIgnoreCase(input.getMode()) && !isBlank(input.getField())) {
            return "$." + input.getField();
        }
        return null;
    }

    private String validationTarget(ValidationSpec validation) {
        return validation.getSource() + " " + validation.getPath();
    }

    private CryptoProfile resolveProfile(ApiTestCase testCase, ApiStep step, TestContext context) {
        CryptoProfile originalProfile = testCase.getProfiles().get(step.getProfile());
        if (originalProfile == null) {
            throw new IllegalArgumentException("Unknown profile: " + step.getProfile());
        }
        CryptoProfile profile = new CryptoProfile();
        profile.setSenderPublicKey(resolveProfileValue(originalProfile.getSenderPublicKey(), context));
        profile.setSenderPrivateKey(resolveProfileValue(originalProfile.getSenderPrivateKey(), context));
        profile.setSenderPrivateKeyPassphrase(resolveProfileValue(originalProfile.getSenderPrivateKeyPassphrase(), context));
        profile.setRecipientPublicKey(resolveProfileValue(originalProfile.getRecipientPublicKey(), context));
        profile.setRecipientPrivateKey(resolveProfileValue(originalProfile.getRecipientPrivateKey(), context));
        profile.setRecipientPrivateKeyPassphrase(resolveProfileValue(originalProfile.getRecipientPrivateKeyPassphrase(), context));
        return profile;
    }

    private boolean matches(String operator, Object actual, Object expected) {
        String resolvedOperator = operator == null ? "equals" : operator;
        if ("equals".equalsIgnoreCase(resolvedOperator)) {
            return String.valueOf(actual).equals(String.valueOf(expected));
        }
        if ("exists".equalsIgnoreCase(resolvedOperator)) {
            return actual != null;
        }
        throw new IllegalArgumentException("Unsupported validation operator: " + operator);
    }

    private String stepName(ApiStep step, int index) {
        if (step.getName() == null || step.getName().isBlank()) {
            return "Step " + (index + 1);
        }
        return step.getName();
    }

    private void rethrow(Throwable throwable) {
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

    private ReportResponse toReportResponse(HttpResponse<String> response) {
        ReportResponse reportResponse = new ReportResponse();
        reportResponse.setStatusCode(response.statusCode());
        reportResponse.setHeaders(response.headers().map());
        reportResponse.setBody(response.body());
        return reportResponse;
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveProfileValue(String value, TestContext context) {
        if (value == null) {
            return null;
        }
        return variableResolver.resolveString(value, context);
    }

    private Object transformRequestBody(RequestSpec requestSpec, Object resolvedBody, CryptoProfile profile) {
        RequestTransformSpec transform = requestSpec.getTransform();
        if (transform == null) {
            return resolvedBody;
        }
        if (!"pgp".equalsIgnoreCase(transform.getType())) {
            throw new IllegalArgumentException("Unsupported request transform type: " + transform.getType());
        }
        String plaintext = serializePlaintext(resolvedBody, transform.getPlaintextFormat());
        String cipherText;
        if ("pgp".equalsIgnoreCase(transform.getAlgorithm())) {
            cipherText = pgpCryptoService.signAndEncrypt(plaintext, toPgpProfile(profile));
        } else if ("fake".equalsIgnoreCase(transform.getAlgorithm())) {
            cipherText = "ENCRYPTED(" + plaintext + ")";
        } else {
            throw new IllegalArgumentException("Unsupported request transform algorithm: " + transform.getAlgorithm());
        }
        if ("raw".equalsIgnoreCase(transform.getOutput().getMode())) {
            return cipherText;
        }
        if ("jsonField".equalsIgnoreCase(transform.getOutput().getMode())) {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put(transform.getOutput().getField(), cipherText);
            return wrapper;
        }
        throw new IllegalArgumentException("Unsupported request transform output mode: " + transform.getOutput().getMode());
    }

    private String serializePlaintext(Object resolvedBody, String plaintextFormat) {
        try {
            if ("text".equalsIgnoreCase(plaintextFormat)) {
                return String.valueOf(resolvedBody);
            }
            return jsonMapper.writeValueAsString(resolvedBody);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to serialize plaintext body", e);
        }
    }

    private Object deserializePlaintext(String plaintext, String plaintextFormat) {
        try {
            if ("text".equalsIgnoreCase(plaintextFormat)) {
                return plaintext;
            }
            return jsonMapper.readValue(plaintext, Object.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse decrypted plaintext payload", e);
        }
    }

    private String serializeWireBody(Object wireBody) {
        try {
            if (wireBody instanceof String textBody) {
                return textBody;
            }
            return jsonMapper.writeValueAsString(wireBody);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to serialize wire body", e);
        }
    }

    private PgpProfileMaterial toPgpProfile(CryptoProfile profile) {
        PgpProfileMaterial material = new PgpProfileMaterial();
        material.setSenderPublicKey(profile.getSenderPublicKey());
        material.setSenderPrivateKey(profile.getSenderPrivateKey());
        material.setSenderPrivateKeyPassphrase(profile.getSenderPrivateKeyPassphrase());
        material.setRecipientPublicKey(profile.getRecipientPublicKey());
        material.setRecipientPrivateKey(profile.getRecipientPrivateKey());
        material.setRecipientPrivateKeyPassphrase(profile.getRecipientPrivateKeyPassphrase());
        return material;
    }

    private record HttpExchange(ReportRequest reportRequest, HttpResponse<String> response) {
    }
}
