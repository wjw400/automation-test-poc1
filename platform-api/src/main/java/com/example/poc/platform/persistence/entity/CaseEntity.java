package com.example.poc.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_case")
public class CaseEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 500)
    private String folderPath;

    private String folderId;

    private String defaultEnvironmentId;

    private String defaultProfileId;

    @Column(nullable = false, columnDefinition = "text")
    private String tagsJson;

    @Column(nullable = false, length = 50)
    private String dslVersion;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 50)
    private String status;

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String folderPath) { this.folderPath = folderPath; }
    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public String getDefaultEnvironmentId() { return defaultEnvironmentId; }
    public void setDefaultEnvironmentId(String defaultEnvironmentId) { this.defaultEnvironmentId = defaultEnvironmentId; }
    public String getDefaultProfileId() { return defaultProfileId; }
    public void setDefaultProfileId(String defaultProfileId) { this.defaultProfileId = defaultProfileId; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getDslVersion() { return dslVersion; }
    public void setDslVersion(String dslVersion) { this.dslVersion = dslVersion; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
