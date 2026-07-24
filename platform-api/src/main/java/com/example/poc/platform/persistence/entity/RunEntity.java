package com.example.poc.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_run")
public class RunEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String caseId;

    @Column
    private String suiteRunId;

    @Column
    private String suiteCaseId;

    @Column(length = 200)
    private String suiteDisplayName;

    @Column
    private Integer suiteCaseOrder;

    @Column
    private String environmentId;

    @Column(length = 200)
    private String environmentName;

    @Column
    private String profileId;

    @Column(length = 200)
    private String profileName;

    @Column(nullable = false, length = 100)
    private String triggeredBy;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant finishedAt;

    @Column(nullable = false)
    private long durationMs;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @PrePersist
    public void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getSuiteRunId() { return suiteRunId; }
    public void setSuiteRunId(String suiteRunId) { this.suiteRunId = suiteRunId; }
    public String getSuiteCaseId() { return suiteCaseId; }
    public void setSuiteCaseId(String suiteCaseId) { this.suiteCaseId = suiteCaseId; }
    public String getSuiteDisplayName() { return suiteDisplayName; }
    public void setSuiteDisplayName(String suiteDisplayName) { this.suiteDisplayName = suiteDisplayName; }
    public Integer getSuiteCaseOrder() { return suiteCaseOrder; }
    public void setSuiteCaseOrder(Integer suiteCaseOrder) { this.suiteCaseOrder = suiteCaseOrder; }
    public String getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    public String getEnvironmentName() { return environmentName; }
    public void setEnvironmentName(String environmentName) { this.environmentName = environmentName; }
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(String triggeredBy) { this.triggeredBy = triggeredBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
