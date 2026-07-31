package com.example.poc.engine.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class ApiCaseReportWriter {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();
    private final Path reportDir;

    public ApiCaseReportWriter() {
        this(Path.of("target", "api-case-reports"));
    }

    public ApiCaseReportWriter(Path reportDir) {
        this.reportDir = reportDir;
    }

    public void write(ApiCaseExecutionReport report) {
        try {
            Files.createDirectories(reportDir);
            String fileName = toFileName(report);
            Files.writeString(reportDir.resolve(fileName + ".json"), objectMapper.writeValueAsString(report), StandardCharsets.UTF_8);
            Files.writeString(reportDir.resolve(fileName + ".html"), toHtml(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write API case report", e);
        }
    }

    private String toFileName(ApiCaseExecutionReport report) {
        String status = report.getStatus().toLowerCase(Locale.ROOT);
        String caseName = report.getCaseName() == null ? "api-case" : report.getCaseName();
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .withZone(java.time.ZoneOffset.UTC)
                .format(report.getStartedAt());
        return timestamp + "-" + safe(caseName) + "-" + status;
    }

    private String safe(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String toHtml(ApiCaseExecutionReport report) throws IOException {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>%s</title>
                  <style>
                    body { margin: 0; font-family: Arial, sans-serif; color: #172033; background: #f6f7f9; }
                    main { max-width: 1180px; margin: 0 auto; padding: 28px; }
                    h1 { margin: 0 0 8px; font-size: 26px; }
                    h2 { margin: 28px 0 12px; font-size: 18px; }
                    h3 { margin: 6px 0 14px; font-size: 17px; }
                    .meta { color: #5d667a; margin-bottom: 18px; }
                    .status { display: inline-block; padding: 4px 9px; border-radius: 4px; font-weight: 700; font-size: 12px; }
                    .PASSED { color: #075e36; background: #dff7e9; }
                    .FAILED { color: #8f1f1f; background: #ffe2e2; }
                    .step-index { color: #5d667a; font-weight: 700; margin-right: 8px; }
                    section { background: #ffffff; border: 1px solid #dde2ea; border-radius: 6px; padding: 16px; margin-bottom: 16px; }
                    table { width: 100%%; border-collapse: collapse; }
                    th, td { border-bottom: 1px solid #e8ebf0; padding: 9px 10px; text-align: left; vertical-align: top; }
                    th { background: #f1f3f6; font-size: 13px; }
                    pre { margin: 0; white-space: pre-wrap; word-break: break-word; background: #101828; color: #f2f4f7; padding: 14px; border-radius: 5px; overflow: auto; }
                    .kv { display: grid; grid-template-columns: 160px 1fr; gap: 8px 14px; }
                    .label { color: #5d667a; font-weight: 700; }
                    .error { color: #8f1f1f; font-weight: 700; }
                  </style>
                </head>
                <body>
                <main>
                  <h1>%s <span class="status %s">%s</span></h1>
                  <div class="meta">%s | %sms | %s</div>
                  %s
                  %s
                </main>
                </body>
                </html>
                """.formatted(
                escape(report.getCaseName()),
                escape(report.getCaseName()),
                escape(report.getStatus()),
                escape(report.getStatus()),
                escape(report.getCaseFile()),
                report.getDurationMs(),
                escape(DateTimeFormatter.ISO_INSTANT.format(report.getStartedAt())),
                errorBlock(report),
                stepSections(report));
    }

    private String stepSections(ApiCaseExecutionReport report) {
        if (report.getSteps().isEmpty()) {
            return "<section><h2>Steps</h2><p>No steps were recorded.</p></section>";
        }
        StringBuilder html = new StringBuilder();
        html.append("<h2>Steps</h2>");
        for (ApiStepExecutionReport step : report.getSteps()) {
            html.append("<section>")
                    .append("<h3><span class=\"step-index\">#").append(step.getIndex()).append("</span>")
                    .append(escape(step.getName())).append(" <span class=\"status ")
                    .append(escape(step.getStatus())).append("\">").append(escape(step.getStatus()))
                    .append("</span></h3>")
                    .append(stepErrorBlock(step))
                    .append("<h2>Request</h2>")
                    .append("<div class=\"kv\"><div class=\"label\">Profile</div><div>")
                    .append(escape(step.getProfile()))
                    .append("</div><div class=\"label\">Method</div><div>")
                    .append(escape(step.getRequest() == null ? "" : step.getRequest().getMethod()))
                    .append("</div><div class=\"label\">URL</div><div>")
                    .append(escape(step.getRequest() == null ? "" : step.getRequest().getUrl()))
                    .append("</div></div><h2>Actions</h2>")
                    .append(actionTable(step))
                    .append("<h2>Request Headers</h2><pre>")
                    .append(escape(toJson(step.getRequest() == null ? null : step.getRequest().getHeaders())))
                    .append("</pre><h2>Request Body</h2><pre>")
                    .append(escape(toJson(step.getRequest() == null ? null : step.getRequest().getBody())))
                    .append("</pre><h2>Response</h2><div class=\"kv\"><div class=\"label\">Status Code</div><div>")
                    .append(step.getResponse() == null ? "" : step.getResponse().getStatusCode())
                    .append("</div></div><h2>Response Headers</h2><pre>")
                    .append(escape(toJson(step.getResponse() == null ? null : step.getResponse().getHeaders())))
                    .append("</pre><h2>Response Body</h2><pre>")
                    .append(escape(step.getResponse() == null ? "" : step.getResponse().getBody()))
                    .append("</pre><h2>Decrypted Payloads</h2><pre>")
                    .append(escape(toJson(step.getDecryptedPayloads())))
                    .append("</pre><h2>Validations</h2>")
                    .append(validationTable(step))
                    .append("<h2>Extracted Values</h2><pre>")
                    .append(escape(toJson(step.getExtractedValues())))
                    .append("</pre></section>");
        }
        return html.toString();
    }

    private String actionTable(ApiStepExecutionReport step) {
        if (step.getActions().isEmpty()) {
            return "<p>No actions were executed.</p>";
        }
        StringBuilder html = new StringBuilder();
        html.append("<table><thead><tr><th>Stage</th><th>#</th><th>Status</th><th>Action</th><th>Duration</th><th>Input</th><th>Output</th><th>Error</th></tr></thead><tbody>");
        for (ActionExecutionReport action : step.getActions()) {
            html.append("<tr>")
                    .append("<td>").append(escape(action.getStage())).append("</td>")
                    .append("<td>").append(action.getIndex()).append("</td>")
                    .append("<td><span class=\"status ").append(escape(action.getStatus())).append("\">")
                    .append(escape(action.getStatus())).append("</span></td>")
                    .append("<td>").append(escape(action.getName())).append("<br><small>")
                    .append(escape(action.getType())).append("</small></td>")
                    .append("<td>").append(action.getDurationMs()).append(" ms</td>")
                    .append("<td><pre>").append(escape(toJson(action.getInput()))).append("</pre></td>")
                    .append("<td><pre>").append(escape(toJson(action.getOutput()))).append("</pre></td>")
                    .append("<td>").append(escape(action.getErrorMessage())).append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String validationTable(ApiStepExecutionReport step) {
        if (step.getValidations().isEmpty()) {
            return "<p>No validations were recorded.</p>";
        }
        StringBuilder html = new StringBuilder();
        html.append("<table><thead><tr><th>Status</th><th>Type</th><th>Target</th><th>Expected</th><th>Actual</th><th>Message</th></tr></thead><tbody>");
        for (ValidationReport validation : step.getValidations()) {
            html.append("<tr>")
                    .append("<td><span class=\"status ").append(escape(validation.getStatus())).append("\">")
                    .append(escape(validation.getStatus())).append("</span></td>")
                    .append("<td>").append(escape(validation.getType())).append("</td>")
                    .append("<td>").append(escape(validation.getTarget())).append("</td>")
                    .append("<td><pre>").append(escape(toJson(validation.getExpected()))).append("</pre></td>")
                    .append("<td><pre>").append(escape(toJson(validation.getActual()))).append("</pre></td>")
                    .append("<td>").append(escape(validation.getMessage())).append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String stepErrorBlock(ApiStepExecutionReport step) {
        if (step.getErrorMessage() == null || step.getErrorMessage().isBlank()) {
            return "";
        }
        return "<p class=\"error\">" + escape(step.getErrorMessage()) + "</p>";
    }

    private String errorBlock(ApiCaseExecutionReport report) {
        if (report.getErrorMessage() == null || report.getErrorMessage().isBlank()) {
            return "";
        }
        return "<section><h2>Error</h2><p class=\"error\">" + escape(report.getErrorMessage()) + "</p></section>";
    }

    private String toJson(Object value) {
        try {
            if (value == null) {
                return "";
            }
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return String.valueOf(value);
        }
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
