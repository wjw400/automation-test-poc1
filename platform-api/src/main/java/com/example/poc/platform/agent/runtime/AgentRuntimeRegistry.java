package com.example.poc.platform.agent.runtime;

import com.example.poc.platform.agent.AgentProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentRuntimeRegistry {
    private final Map<String, AgentRuntime> runtimes;
    private final AgentProperties properties;

    public AgentRuntimeRegistry(List<AgentRuntime> runtimes, AgentProperties properties) {
        Map<String, AgentRuntime> indexed = new LinkedHashMap<>();
        for (AgentRuntime runtime : runtimes) indexed.put(normalize(runtime.type()), runtime);
        this.runtimes = Map.copyOf(indexed);
        this.properties = properties;
    }

    public AgentRuntime require(String type) {
        String requested = type == null || type.isBlank() ? properties.getDefaultRuntime() : type;
        AgentRuntime runtime = runtimes.get(normalize(requested));
        if (runtime == null) throw new IllegalArgumentException("Unknown Agent runtime: " + requested);
        if (!runtime.capabilities().available()) {
            throw new AgentRuntimeException(
                    "RUNTIME_NOT_CONFIGURED",
                    "Agent runtime is not configured: " + runtime.type(),
                    false);
        }
        return runtime;
    }

    public List<RuntimeDescriptor> list() {
        return runtimes.values().stream()
                .map(runtime -> new RuntimeDescriptor(runtime.type(), runtime.capabilities()))
                .toList();
    }

    public String resolveModel(AgentRuntime runtime, String requestedModel) {
        String model = requestedModel == null || requestedModel.isBlank()
                ? runtime.capabilities().defaultModel()
                : requestedModel.trim();
        if (!runtime.capabilities().models().contains(model)) {
            throw new IllegalArgumentException("Model is not enabled for " + runtime.type() + ": " + model);
        }
        return model;
    }

    private String normalize(String value) { return value.trim().toUpperCase(Locale.ROOT); }

    public record RuntimeDescriptor(String type, AgentRuntimeCapabilities capabilities) {}
}
