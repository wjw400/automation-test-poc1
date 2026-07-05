package com.example.poc.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "platform_suite_case")
public class SuiteCaseEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String suiteId;

    @Column(nullable = false)
    private String caseId;

    @Column(length = 200)
    private String alias;

    @Column(nullable = false)
    private int caseOrder;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, columnDefinition = "text")
    private String variableOverridesJson;

    private String environmentId;

    private String profileId;

    @PrePersist
    public void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSuiteId() { return suiteId; }
    public void setSuiteId(String suiteId) { this.suiteId = suiteId; }
    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public int getCaseOrder() { return caseOrder; }
    public void setCaseOrder(int caseOrder) { this.caseOrder = caseOrder; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getVariableOverridesJson() { return variableOverridesJson; }
    public void setVariableOverridesJson(String variableOverridesJson) { this.variableOverridesJson = variableOverridesJson; }
    public String getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }
}
