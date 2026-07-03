package com.example.poc.engine;

import java.util.Map;

public class ApiStep {
    private String name;
    private String profile;
    private boolean continueOnFailure;
    private RequestSpec request;
    private ExpectSpec expect;
    private Map<String, Object> extract = new java.util.LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public boolean isContinueOnFailure() {
        return continueOnFailure;
    }

    public void setContinueOnFailure(boolean continueOnFailure) {
        this.continueOnFailure = continueOnFailure;
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
        this.extract = extract == null ? new java.util.LinkedHashMap<>() : extract;
    }
}
