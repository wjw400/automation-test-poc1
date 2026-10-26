package com.example.poc.platform.agent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    private String defaultRuntime = "FAKE";
    private int maxTurns = 20;
    private int maxToolCalls = 40;
    private int maxRepeatedToolCalls = 3;
    private int maxNoProgressToolCalls = 3;
    private Duration runTimeout = Duration.ofHours(2);
    private Duration inactivityTimeout = Duration.ofMinutes(5);
    private Duration modelRequestTimeout = Duration.ofMinutes(3);
    private Duration toolDefaultTimeout = Duration.ofSeconds(30);
    private final Fake fake = new Fake();
    private final DeepSeek deepseek = new DeepSeek();
    private final Copilot copilot = new Copilot();

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
    public Duration getRunTimeout() { return runTimeout; }
    public void setRunTimeout(Duration runTimeout) { this.runTimeout = runTimeout; }
    public Duration getInactivityTimeout() { return inactivityTimeout; }
    public void setInactivityTimeout(Duration inactivityTimeout) { this.inactivityTimeout = inactivityTimeout; }
    public Duration getModelRequestTimeout() { return modelRequestTimeout; }
    public void setModelRequestTimeout(Duration modelRequestTimeout) { this.modelRequestTimeout = modelRequestTimeout; }
    public Duration getToolDefaultTimeout() { return toolDefaultTimeout; }
    public void setToolDefaultTimeout(Duration toolDefaultTimeout) { this.toolDefaultTimeout = toolDefaultTimeout; }
    public Fake getFake() { return fake; }
    public DeepSeek getDeepseek() { return deepseek; }
    public Copilot getCopilot() { return copilot; }

    public static class Copilot {
        private boolean enabled;
        private String baseUrl = "https://api.githubcopilot.com";
        private String apiToken = "";
        private String apiVersion = "";
        private String integrationId = "";
        private Map<String, String> headers = Map.of();
        private int maxOutputTokens = 8192;
        private String model = "";
        private List<String> models = List.of();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiToken() { return apiToken; }
        public void setApiToken(String apiToken) { this.apiToken = apiToken; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
        public String getIntegrationId() { return integrationId; }
        public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers == null ? Map.of() : Map.copyOf(headers); }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public List<String> getModels() { return models; }
        public void setModels(List<String> models) { this.models = models == null ? List.of() : List.copyOf(models); }

        public boolean isConfigured() {
            return enabled && apiToken != null && !apiToken.isBlank()
                    && baseUrl != null && !baseUrl.isBlank()
                    && model != null && !model.isBlank() && !"auto".equalsIgnoreCase(model.trim())
                    && maxOutputTokens > 0;
        }
    }

    public static class Fake {
        private Duration chunkDelay = Duration.ofMillis(10);

        public Duration getChunkDelay() { return chunkDelay; }
        public void setChunkDelay(Duration chunkDelay) { this.chunkDelay = chunkDelay; }
    }

    public static class DeepSeek {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String model = "deepseek-v4-flash";
        private List<String> models = List.of();
        private double temperature = 0.2;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public List<String> getModels() { return models; }
        public void setModels(List<String> models) { this.models = models == null ? List.of() : List.copyOf(models); }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
    }
}
