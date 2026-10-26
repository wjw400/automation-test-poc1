package com.example.poc.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResponseSpec {
    private HttpStatusSpec status;
    private List<ValidationSpec> assertions = new ArrayList<>();
    private Map<String, Object> extract = new LinkedHashMap<>();

    public HttpStatusSpec getStatus() { return status; }
    public void setStatus(HttpStatusSpec status) { this.status = status; }
    public List<ValidationSpec> getAssertions() { return assertions; }
    public void setAssertions(List<ValidationSpec> assertions) {
        this.assertions = assertions == null ? new ArrayList<>() : assertions;
    }
    public Map<String, Object> getExtract() { return extract; }
    public void setExtract(Map<String, Object> extract) {
        this.extract = extract == null ? new LinkedHashMap<>() : extract;
    }
}
