package com.example.poc.platform.agent.runtime;

import com.example.poc.platform.agent.capability.AgentTool;
import java.util.List;

public record AgentTurnRequest(
        String taskId,
        String turnId,
        String modelId,
        long tokenBudget,
        List<AgentConversationMessage> messages,
        String systemContext,
        List<String> skillNames,
        List<AgentTool> tools) {
    public AgentTurnRequest {
        messages = List.copyOf(messages);
        systemContext = systemContext == null ? "" : systemContext;
        skillNames = skillNames == null ? List.of() : List.copyOf(skillNames);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public AgentTurnRequest(
            String taskId,
            String turnId,
            String modelId,
            long tokenBudget,
            List<AgentConversationMessage> messages) {
        this(taskId, turnId, modelId, tokenBudget, messages, "", List.of(), List.of());
    }

    public AgentTurnRequest withCapabilities(
            String context,
            List<String> selectedSkills,
            List<AgentTool> availableTools) {
        return new AgentTurnRequest(
                taskId, turnId, modelId, tokenBudget, messages,
                context, selectedSkills, availableTools);
    }

    public String latestUserText() {
        for (int index = messages.size() - 1; index >= 0; index--) {
            AgentConversationMessage message = messages.get(index);
            if ("USER".equals(message.role())) return message.text();
        }
        return "";
    }
}
