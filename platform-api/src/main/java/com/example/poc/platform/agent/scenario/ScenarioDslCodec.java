package com.example.poc.platform.agent.scenario;

import com.example.poc.engine.ApiTestCase;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Component
public class ScenarioDslCodec {
    private final ObjectMapper yamlMapper = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private final ObjectMapper jsonMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ApiTestCase parse(String content) {
        try {
            return yamlMapper.readValue(content, ApiTestCase.class);
        } catch (JacksonException exception) {
            throw new ScenarioCommandException(
                    "INVALID_SCENARIO_YAML",
                    "Unable to parse Case DSL v1: " + exception.getOriginalMessage(),
                    "scenario");
        }
    }

    public String write(ApiTestCase scenario) {
        try {
            return yamlMapper.writeValueAsString(scenario);
        } catch (JacksonException exception) {
            throw new ScenarioCommandException(
                    "SCENARIO_SERIALIZATION_FAILED",
                    "Unable to serialize Case DSL v1: " + exception.getOriginalMessage(),
                    "scenario");
        }
    }

    public ApiTestCase copy(ApiTestCase scenario) {
        return jsonMapper.convertValue(scenario, ApiTestCase.class);
    }

    public <T> T convert(Object value, Class<T> targetType, String path) {
        try {
            return jsonMapper.convertValue(value, targetType);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new ScenarioCommandException(
                    "INVALID_COMMAND_FIELD",
                    "Invalid value at " + path + ": " + exception.getMessage(),
                    path);
        }
    }

    public Object toGeneric(Object value) {
        return jsonMapper.convertValue(value, Object.class);
    }
}
