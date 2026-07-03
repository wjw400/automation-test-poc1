package com.example.poc.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_api_operation")
public class ApiOperationEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String apiSpecId;

    @Column(length = 100)
    private String tagName;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(length = 255)
    private String operationId;

    @Column(length = 255)
    private String summary;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String requestBodyJson;

    @Column(columnDefinition = "text")
    private String responsesJson;

    @Column(columnDefinition = "text")
    private String rawOperationJson;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getApiSpecId() { return apiSpecId; }
    public void setApiSpecId(String apiSpecId) { this.apiSpecId = apiSpecId; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRequestBodyJson() { return requestBodyJson; }
    public void setRequestBodyJson(String requestBodyJson) { this.requestBodyJson = requestBodyJson; }
    public String getResponsesJson() { return responsesJson; }
    public void setResponsesJson(String responsesJson) { this.responsesJson = responsesJson; }
    public String getRawOperationJson() { return rawOperationJson; }
    public void setRawOperationJson(String rawOperationJson) { this.rawOperationJson = rawOperationJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
