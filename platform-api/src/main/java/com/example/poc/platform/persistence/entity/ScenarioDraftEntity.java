package com.example.poc.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_scenario_draft")
public class ScenarioDraftEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column
    private String scenarioId;

    // Historical provenance; the source Suite may have been deleted.
    private String sourceSuiteId;

    @Column(nullable = false)
    private long sourceRevision;

    @Column(nullable = false, columnDefinition = "text")
    private String baseContent;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, columnDefinition = "text")
    private String baseStepIdsJson;

    @Column(nullable = false, columnDefinition = "text")
    private String stepIdsJson;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 64)
    private String agentTaskId;

    @Column(length = 64)
    private String lastCommandFingerprint;

    @Version
    @Column(nullable = false)
    private Long revision;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    public String getSourceSuiteId() { return sourceSuiteId; }
    public void setSourceSuiteId(String sourceSuiteId) { this.sourceSuiteId = sourceSuiteId; }
    public long getSourceRevision() { return sourceRevision; }
    public void setSourceRevision(long sourceRevision) { this.sourceRevision = sourceRevision; }
    public String getBaseContent() { return baseContent; }
    public void setBaseContent(String baseContent) { this.baseContent = baseContent; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getBaseStepIdsJson() { return baseStepIdsJson; }
    public void setBaseStepIdsJson(String baseStepIdsJson) { this.baseStepIdsJson = baseStepIdsJson; }
    public String getStepIdsJson() { return stepIdsJson; }
    public void setStepIdsJson(String stepIdsJson) { this.stepIdsJson = stepIdsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getAgentTaskId() { return agentTaskId; }
    public void setAgentTaskId(String agentTaskId) { this.agentTaskId = agentTaskId; }
    public String getLastCommandFingerprint() { return lastCommandFingerprint; }
    public void setLastCommandFingerprint(String lastCommandFingerprint) { this.lastCommandFingerprint = lastCommandFingerprint; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
