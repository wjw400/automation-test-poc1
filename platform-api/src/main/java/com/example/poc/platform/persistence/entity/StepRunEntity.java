package com.example.poc.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "platform_step_run")
public class StepRunEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String runId;

    @Column(nullable = false)
    private int stepIndex;

    @Column(nullable = false, length = 200)
    private String stepName;

    @Column(length = 200)
    private String profileName;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(columnDefinition = "text")
    private String requestJson;

    @Column(columnDefinition = "text")
    private String responseJson;

    @Column(columnDefinition = "text")
    private String decryptedPayloadsJson;

    @Column(columnDefinition = "text")
    private String validationsJson;

    @Column(columnDefinition = "text")
    private String extractedValuesJson;

    @PrePersist
    public void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public int getStepIndex() { return stepIndex; }
    public void setStepIndex(int stepIndex) { this.stepIndex = stepIndex; }
    public String getStepName() { return stepName; }
    public void setStepName(String stepName) { this.stepName = stepName; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRequestJson() { return requestJson; }
    public void setRequestJson(String requestJson) { this.requestJson = requestJson; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public String getDecryptedPayloadsJson() { return decryptedPayloadsJson; }
    public void setDecryptedPayloadsJson(String decryptedPayloadsJson) { this.decryptedPayloadsJson = decryptedPayloadsJson; }
    public String getValidationsJson() { return validationsJson; }
    public void setValidationsJson(String validationsJson) { this.validationsJson = validationsJson; }
    public String getExtractedValuesJson() { return extractedValuesJson; }
    public void setExtractedValuesJson(String extractedValuesJson) { this.extractedValuesJson = extractedValuesJson; }
}
