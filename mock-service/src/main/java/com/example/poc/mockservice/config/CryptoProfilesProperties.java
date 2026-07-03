package com.example.poc.mockservice.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock-service.crypto")
public class CryptoProfilesProperties {
    private String defaultProfile = "clientToServer";
    private Map<String, CryptoProfileProperties> profiles = new LinkedHashMap<>();

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public Map<String, CryptoProfileProperties> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, CryptoProfileProperties> profiles) {
        this.profiles = profiles;
    }
}
