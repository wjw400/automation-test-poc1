package com.example.poc.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public class ActionExecutionState {
    private Object requestBody;
    private String responseBody;
    private final Map<String, Object> artifacts = new LinkedHashMap<>();
    private final Map<String, Object> decryptedPayloads = new LinkedHashMap<>();

    public Object getRequestBody() { return requestBody; }
    public void setRequestBody(Object requestBody) { this.requestBody = requestBody; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public Map<String, Object> getArtifacts() { return artifacts; }
    public Map<String, Object> getDecryptedPayloads() { return decryptedPayloads; }
}
