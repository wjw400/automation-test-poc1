package com.example.poc.platform.agent;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private String defaultRuntime = "FAKE";
    private int maxTurns = 20;
    private int maxToolCalls = 20;
    private int maxRepeatedToolCalls = 3;
    private int maxNoProgressToolCalls = 3;
    private Duration turnTimeout = Duration.ofSeconds(120);
    private final Fake fake = new Fake();
    private final DeepSeek deepseek = new DeepSeek();

    public String getDefaultRuntime() { return defaultRuntime; }
    public void setDefaultRuntime(String defaultRuntime) { this.defaultRuntime = defaultRuntime; }
    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int maxTurns) { this.maxTurns = maxTurns; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }
    public int getMaxRepeatedToolCalls() { return maxRepeatedToolCalls; }
    public void setMaxRepeatedToolCalls(int maxRepeatedToolCalls) { this.maxRepeatedToolCalls = maxRepeatedToolCalls; }
    public int getMaxNoProgressToolCalls() { return maxNoProgressToolCalls; }
    public void setMaxNoProgressToolCalls(int maxNoProgressToolCalls) { this.maxNoProgressToolCalls = maxNoProgressToolCalls; }
    public Duration getTurnTimeout() { return turnTimeout; }
    public void setTurnTimeout(Duration turnTimeout) { this.turnTimeout = turnTimeout; }
    public Fake getFake() { return fake; }
    public DeepSeek getDeepseek() { return deepseek; }

    public static class Fake {
        private Duration chunkDelay = Duration.ofMillis(10);

        public Duration getChunkDelay() { return chunkDelay; }
        public void setChunkDelay(Duration chunkDelay) { this.chunkDelay = chunkDelay; }
    }

    public static class DeepSeek {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-v4-flash";
        private double temperature = 0.2;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
    }
}
