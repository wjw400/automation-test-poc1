package com.example.poc.engine.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ApiCaseExecutionReport {
    private String caseName;
    private String caseFile;
    private Instant startedAt;
    private Instant finishedAt;
    private long durationMs;
    private String status = "PASSED";
    private String errorMessage;
    private List<ApiStepExecutionReport> steps = new ArrayList<>();

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public String getCaseFile() {
        return caseFile;
    }

    public void setCaseFile(String caseFile) {
        this.caseFile = caseFile;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ApiStepExecutionReport> getSteps() {
        return steps;
    }

    public void setSteps(List<ApiStepExecutionReport> steps) {
        this.steps = steps;
    }
}
