package com.example.poc.platform.agent.capability;

import java.util.Map;

public interface AgentTool {
    AgentToolDefinition definition();

    AgentToolResult execute(Map<String, Object> input);
}
