package com.example.poc.engine;

public class OperationSpec {
    private String id;
    private String apiSpecId;
    private String apiSpecName;
    private String operationId;
    private String summary;
    private String service;
    private String method;
    private String path;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApiSpecId() { return apiSpecId; }
    public void setApiSpecId(String apiSpecId) { this.apiSpecId = apiSpecId; }
    public String getApiSpecName() { return apiSpecName; }
    public void setApiSpecName(String apiSpecName) { this.apiSpecName = apiSpecName; }
    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}
