package com.example.poc.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExpectSpec {
    private int status;
    private Map<String, Object> jsonPath = new LinkedHashMap<>();
    private List<DecryptSpec> decrypt = new ArrayList<>();
    private List<ValidationSpec> validations = new ArrayList<>();

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Map<String, Object> getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(Map<String, Object> jsonPath) {
        this.jsonPath = jsonPath == null ? new LinkedHashMap<>() : jsonPath;
    }

    public List<DecryptSpec> getDecrypt() {
        return decrypt;
    }

    public void setDecrypt(List<DecryptSpec> decrypt) {
        this.decrypt = decrypt == null ? new ArrayList<>() : decrypt;
    }

    public List<ValidationSpec> getValidations() {
        return validations;
    }

    public void setValidations(List<ValidationSpec> validations) {
        this.validations = validations == null ? new ArrayList<>() : validations;
    }
}
