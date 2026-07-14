package com.example.poc.engine;

public class CaseActionSpec {
    private String type;
    private String name;
    private boolean enabled = true;
    private ActionInputSpec input = new ActionInputSpec();
    private ActionOutputSpec output = new ActionOutputSpec();
    private Object template;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public ActionInputSpec getInput() { return input; }
    public void setInput(ActionInputSpec input) { this.input = input == null ? new ActionInputSpec() : input; }
    public ActionOutputSpec getOutput() { return output; }
    public void setOutput(ActionOutputSpec output) { this.output = output == null ? new ActionOutputSpec() : output; }
    public Object getTemplate() { return template; }
    public void setTemplate(Object template) { this.template = template; }
}
