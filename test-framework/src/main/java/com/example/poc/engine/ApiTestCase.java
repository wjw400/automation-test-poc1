package com.example.poc.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiTestCase {
    private String dslVersion;
    private String name;
    private String description;
    private List<String> tags = List.of();
    private RuntimeSpec runtime = new RuntimeSpec();
    private Map<String, Object> variables = new LinkedHashMap<>();
    private Map<String, CryptoProfile> profiles = new LinkedHashMap<>();
    private List<ApiStep> steps = List.of();

    public String getDslVersion() {
        return dslVersion;
    }

    public void setDslVersion(String dslVersion) {
        this.dslVersion = dslVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? List.of() : tags;
    }

    public RuntimeSpec getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeSpec runtime) {
        this.runtime = runtime == null ? new RuntimeSpec() : runtime;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables == null ? new LinkedHashMap<>() : variables;
    }

    public Map<String, CryptoProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, CryptoProfile> profiles) {
        this.profiles = profiles == null ? new LinkedHashMap<>() : profiles;
    }

    public List<ApiStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ApiStep> steps) {
        this.steps = steps == null ? List.of() : steps;
    }
}
