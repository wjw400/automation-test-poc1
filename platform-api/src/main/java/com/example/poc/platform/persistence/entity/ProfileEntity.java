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
    private String verificationPublicKey;

    @Column(columnDefinition = "text")
    private String signingPrivateKey;

    @Column(length = 500)
    private String signingPrivateKeyPassphrase;

    @Column(columnDefinition = "text")
    private String encryptionPublicKey;

    @Column(columnDefinition = "text")
    private String decryptionPrivateKey;

    @Column(length = 500)
    private String decryptionPrivateKeyPassphrase;

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
    public String getVerificationPublicKey() { return verificationPublicKey; }
    public void setVerificationPublicKey(String verificationPublicKey) { this.verificationPublicKey = verificationPublicKey; }
    public String getSigningPrivateKey() { return signingPrivateKey; }
    public void setSigningPrivateKey(String signingPrivateKey) { this.signingPrivateKey = signingPrivateKey; }
    public String getSigningPrivateKeyPassphrase() { return signingPrivateKeyPassphrase; }
    public void setSigningPrivateKeyPassphrase(String signingPrivateKeyPassphrase) { this.signingPrivateKeyPassphrase = signingPrivateKeyPassphrase; }
    public String getEncryptionPublicKey() { return encryptionPublicKey; }
    public void setEncryptionPublicKey(String encryptionPublicKey) { this.encryptionPublicKey = encryptionPublicKey; }
    public String getDecryptionPrivateKey() { return decryptionPrivateKey; }
    public void setDecryptionPrivateKey(String decryptionPrivateKey) { this.decryptionPrivateKey = decryptionPrivateKey; }
    public String getDecryptionPrivateKeyPassphrase() { return decryptionPrivateKeyPassphrase; }
    public void setDecryptionPrivateKeyPassphrase(String decryptionPrivateKeyPassphrase) { this.decryptionPrivateKeyPassphrase = decryptionPrivateKeyPassphrase; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<String> getEnvironmentIds() { return environmentIds; }
    public void setEnvironmentIds(Set<String> environmentIds) {
        this.environmentIds = environmentIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(environmentIds);
    }
}
