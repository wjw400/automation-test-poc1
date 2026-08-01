package com.example.poc.platform.agent.scenario;

public class ScenarioCommandException extends IllegalArgumentException {
    private final String code;
    private final int commandIndex;
    private final String path;

    public ScenarioCommandException(String code, String message, int commandIndex, String path) {
        super(message);
        this.code = code;
        this.commandIndex = commandIndex;
        this.path = path;
    }

    public ScenarioCommandException(String code, String message, String path) {
        this(code, message, -1, path);
    }

    public ScenarioCommandException atCommand(int index) {
        return commandIndex >= 0 ? this : new ScenarioCommandException(code, getMessage(), index, path);
    }

    public String getCode() { return code; }
    public int getCommandIndex() { return commandIndex; }
    public String getPath() { return path; }
}
