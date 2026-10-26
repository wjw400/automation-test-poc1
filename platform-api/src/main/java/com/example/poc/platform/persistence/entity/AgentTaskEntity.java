package com.example.poc.platform.persistence.entity;

import com.example.poc.platform.agent.AgentPermissionMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_agent_task")
public class AgentTaskEntity {
    @Id private String id;
    @Column(nullable = false) private String projectId;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 32) private String status;
    @Column(length = 32) private String targetType;
    @Column(length = 64) private String targetId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private AgentPermissionMode permissionMode;
    @Column(nullable = false) private int permissionModeRevision;
    @Column(nullable = false, length = 32) private String runtimeType;
    @Column(length = 200) private String modelId;
    @Column(nullable = false, length = 100) private String createdBy;
    @Column(nullable = false) private int iterationCount;
    @Column(nullable = false) private long tokenBudget;
    @Column(nullable = false, columnDefinition = "text") private String contextJson;
    @Column(length = 64) private String pauseReason;
    @Column(columnDefinition = "text") private String pauseContextJson;
    @Column(nullable = false) private int continuationCount;
    @Version @Column(nullable = false) private Long revision;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Column private Instant completedAt;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public AgentPermissionMode getPermissionMode() { return permissionMode; }
    public void setPermissionMode(AgentPermissionMode permissionMode) { this.permissionMode = permissionMode; }
    public int getPermissionModeRevision() { return permissionModeRevision; }
    public void setPermissionModeRevision(int permissionModeRevision) { this.permissionModeRevision = permissionModeRevision; }
    public String getRuntimeType() { return runtimeType; }
    public void setRuntimeType(String runtimeType) { this.runtimeType = runtimeType; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public int getIterationCount() { return iterationCount; }
    public void setIterationCount(int iterationCount) { this.iterationCount = iterationCount; }
    public long getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(long tokenBudget) { this.tokenBudget = tokenBudget; }
    public String getContextJson() { return contextJson; }
    public void setContextJson(String contextJson) { this.contextJson = contextJson; }
    public String getPauseReason() { return pauseReason; }
    public void setPauseReason(String pauseReason) { this.pauseReason = pauseReason; }
    public String getPauseContextJson() { return pauseContextJson; }
    public void setPauseContextJson(String pauseContextJson) { this.pauseContextJson = pauseContextJson; }
    public int getContinuationCount() { return continuationCount; }
    public void setContinuationCount(int continuationCount) { this.continuationCount = continuationCount; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
