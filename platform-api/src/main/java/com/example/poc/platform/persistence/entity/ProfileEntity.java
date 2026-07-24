package com.example.poc.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "platform_profile")
public class ProfileEntity {
    @Id
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(columnDefinition = "text")
    private String senderPublicKey;

    @Column(columnDefinition = "text")
    private String senderPrivateKey;

    @Column(length = 500)
    private String senderPrivateKeyPassphrase;

    @Column(columnDefinition = "text")
    private String recipientPublicKey;

    @Column(columnDefinition = "text")
    private String recipientPrivateKey;

    @Column(length = 500)
    private String recipientPrivateKeyPassphrase;

    @Column(nullable = false)
    private boolean enabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "platform_profile_environment",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "environment_id", nullable = false)
    private Set<String> environmentIds = new LinkedHashSet<>();

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
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSenderPublicKey() { return senderPublicKey; }
    public void setSenderPublicKey(String senderPublicKey) { this.senderPublicKey = senderPublicKey; }
    public String getSenderPrivateKey() { return senderPrivateKey; }
    public void setSenderPrivateKey(String senderPrivateKey) { this.senderPrivateKey = senderPrivateKey; }
    public String getSenderPrivateKeyPassphrase() { return senderPrivateKeyPassphrase; }
    public void setSenderPrivateKeyPassphrase(String senderPrivateKeyPassphrase) { this.senderPrivateKeyPassphrase = senderPrivateKeyPassphrase; }
    public String getRecipientPublicKey() { return recipientPublicKey; }
    public void setRecipientPublicKey(String recipientPublicKey) { this.recipientPublicKey = recipientPublicKey; }
    public String getRecipientPrivateKey() { return recipientPrivateKey; }
    public void setRecipientPrivateKey(String recipientPrivateKey) { this.recipientPrivateKey = recipientPrivateKey; }
    public String getRecipientPrivateKeyPassphrase() { return recipientPrivateKeyPassphrase; }
    public void setRecipientPrivateKeyPassphrase(String recipientPrivateKeyPassphrase) { this.recipientPrivateKeyPassphrase = recipientPrivateKeyPassphrase; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<String> getEnvironmentIds() { return environmentIds; }
    public void setEnvironmentIds(Set<String> environmentIds) {
        this.environmentIds = environmentIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(environmentIds);
    }
}
