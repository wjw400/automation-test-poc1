package com.example.poc.mockservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock-service.crypto")
public class CryptoConfigurationProperties {
    private String defaultProfile = "secureEcho";
    private ServiceIdentityProperties service = new ServiceIdentityProperties();
    private SeedPartnerProperties seededPartner = new SeedPartnerProperties();

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public ServiceIdentityProperties getService() {
        return service;
    }

    public void setService(ServiceIdentityProperties service) {
        this.service = service;
    }

    public SeedPartnerProperties getSeededPartner() {
        return seededPartner;
    }

    public void setSeededPartner(SeedPartnerProperties seededPartner) {
        this.seededPartner = seededPartner;
    }
}
