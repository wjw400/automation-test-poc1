package com.example.poc.engine.report;

public class ActionExecutionReport {
    private String stage;
    private int index;
    private String type;
    private String name;
    private String status;
    private long durationMs;
    private Object input;
    private Object output;
    private String errorMessage;
    private String origin;
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public Object getInput() { return input; }
    public void setInput(Object input) { this.input = input; }
    public Object getOutput() { return output; }
    public void setOutput(Object output) { this.output = output; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
