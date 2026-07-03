package com.example.poc.engine.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiStepExecutionReport {
    private int index;
    private String name;
    private String profile;
    private String status = "PASSED";
    private String errorMessage;
    private ReportRequest request;
    private ReportResponse response;
    private Map<String, Object> decryptedPayloads = new LinkedHashMap<>();
    private List<ValidationReport> validations = new ArrayList<>();
    private Map<String, Object> extractedValues = new LinkedHashMap<>();

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
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

    public ReportRequest getRequest() {
        return request;
    }

    public void setRequest(ReportRequest request) {
        this.request = request;
    }

    public ReportResponse getResponse() {
        return response;
    }

    public void setResponse(ReportResponse response) {
        this.response = response;
    }

    public Map<String, Object> getDecryptedPayloads() {
        return decryptedPayloads;
    }

    public void setDecryptedPayloads(Map<String, Object> decryptedPayloads) {
        this.decryptedPayloads = decryptedPayloads;
    }

    public List<ValidationReport> getValidations() {
        return validations;
    }

    public void setValidations(List<ValidationReport> validations) {
        this.validations = validations;
    }

    public Map<String, Object> getExtractedValues() {
        return extractedValues;
    }

    public void setExtractedValues(Map<String, Object> extractedValues) {
        this.extractedValues = extractedValues;
    }
}
