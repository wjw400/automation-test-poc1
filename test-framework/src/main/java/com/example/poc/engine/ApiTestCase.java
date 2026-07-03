package com.example.poc.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApiTestCase {
    private String name;
    private String description;
    private List<String> tags = List.of();
    private Map<String, Object> variables = new LinkedHashMap<>();
    private Map<String, CryptoProfile> profiles = new LinkedHashMap<>();
    private List<ApiStep> steps = List.of();
    private RequestSpec request;
    private ExpectSpec expect;
    private Map<String, Object> extract = new LinkedHashMap<>();

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

    public RequestSpec getRequest() {
        return request;
    }

    public void setRequest(RequestSpec request) {
        this.request = request;
    }

    public ExpectSpec getExpect() {
        return expect;
    }

    public void setExpect(ExpectSpec expect) {
        this.expect = expect;
    }

    public Map<String, Object> getExtract() {
        return extract;
    }

    public void setExtract(Map<String, Object> extract) {
        this.extract = extract == null ? new LinkedHashMap<>() : extract;
    }
}
