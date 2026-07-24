package com.example.poc.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_suite_run")
public class SuiteRunEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String suiteId;

    @Column(nullable = false, length = 200)
    private String suiteName;

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
    public String getSuiteId() { return suiteId; }
    public void setSuiteId(String suiteId) { this.suiteId = suiteId; }
    public String getSuiteName() { return suiteName; }
    public void setSuiteName(String suiteName) { this.suiteName = suiteName; }
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
