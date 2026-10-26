package com.example.poc.engine;

public class ApiStep {
    private String name;
    private String type = "http";
    private OperationSpec operation = new OperationSpec();
    private RuntimeSpec runtime = new RuntimeSpec();
    private RequestSpec request;
    private ActionPipelineSpec actions = new ActionPipelineSpec();
    private ResponseSpec response;
    private String processingMode = "automatic";
    private OperationProcessingSpec processing;
    private java.util.Map<String, Object> processingSnapshot = new java.util.LinkedHashMap<>();

    public String getProcessingMode() { return processingMode; }
    public void setProcessingMode(String mode) { processingMode = mode; }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public OperationProcessingSpec getProcessing() { return processing; }
    public void setProcessing(OperationProcessingSpec processing) { this.processing = processing; }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    public java.util.Map<String, Object> getProcessingSnapshot() { return processingSnapshot; }
    public void setProcessingSnapshot(java.util.Map<String, Object> snapshot) { processingSnapshot = snapshot; }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public OperationSpec getOperation() {
        return operation;
    }

    public void setOperation(OperationSpec operation) {
        this.operation = operation == null ? new OperationSpec() : operation;
    }

    public RuntimeSpec getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeSpec runtime) {
        this.runtime = runtime == null ? new RuntimeSpec() : runtime;
    }

    public RequestSpec getRequest() {
        return request;
    }

    public void setRequest(RequestSpec request) {
        this.request = request;
    }

    public ActionPipelineSpec getActions() {
        return actions;
    }

    public void setActions(ActionPipelineSpec actions) {
        this.actions = actions == null ? new ActionPipelineSpec() : actions;
    }

    public ResponseSpec getResponse() {
        return response;
    }

    public void setResponse(ResponseSpec response) {
        this.response = response;
    }
}
