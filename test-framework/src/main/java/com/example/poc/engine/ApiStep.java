package com.example.poc.engine;

public class ApiStep {
    private String name;
    private String type = "http";
    private OperationSpec operation = new OperationSpec();
    private RuntimeSpec runtime = new RuntimeSpec();
    private RequestSpec request;
    private ActionPipelineSpec actions = new ActionPipelineSpec();
    private ResponseSpec response;

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
