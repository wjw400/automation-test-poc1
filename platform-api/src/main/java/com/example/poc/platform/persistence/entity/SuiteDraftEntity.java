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
@Table(name = "platform_suite_draft")
public class SuiteDraftEntity {
    @Id
    private String id;
    @Column(nullable = false)
    private String projectId;
    @Column(nullable = false)
    private String suiteId;
    @Column(nullable = false)
    private long sourceRevision;
    @Column(nullable = false, columnDefinition = "text")
    private String baseContent;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(length = 64)
    private String lastCommandFingerprint;
    @Version
    @Column(nullable = false)
    private Long revision;
    @Column(length = 100)
    private String createdBy;
    @Column(length = 64)
    private String agentTaskId;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (status == null || status.isBlank()) status = "ACTIVE";
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getSuiteId() { return suiteId; }
    public void setSuiteId(String suiteId) { this.suiteId = suiteId; }
    public long getSourceRevision() { return sourceRevision; }
    public void setSourceRevision(long sourceRevision) { this.sourceRevision = sourceRevision; }
    public String getBaseContent() { return baseContent; }
    public void setBaseContent(String baseContent) { this.baseContent = baseContent; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastCommandFingerprint() { return lastCommandFingerprint; }
    public void setLastCommandFingerprint(String value) { this.lastCommandFingerprint = value; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getAgentTaskId() { return agentTaskId; }
    public void setAgentTaskId(String agentTaskId) { this.agentTaskId = agentTaskId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
