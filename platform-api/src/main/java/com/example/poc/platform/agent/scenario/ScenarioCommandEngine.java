package com.example.poc.platform.agent.scenario;

import com.example.poc.engine.ActionPipelineSpec;
import com.example.poc.engine.ApiStep;
import com.example.poc.engine.ApiTestCase;
import com.example.poc.engine.OperationSpec;
import com.example.poc.engine.RequestSpec;
import com.example.poc.engine.ResponseSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ScenarioCommandEngine {
    private final ScenarioDslCodec codec;

    public ScenarioCommandEngine(ScenarioDslCodec codec) {
        this.codec = codec;
    }

    public ScenarioDocument apply(
            ScenarioDocument source,
            List<ScenarioCommand> commands,
            OperationResolver operationResolver) {
        ScenarioDocument working = new ScenarioDocument(
                codec.copy(source.scenario()),
                new ArrayList<>(source.stepIds()));
        List<String> stepIds = new ArrayList<>(working.stepIds());

        for (int index = 0; index < commands.size(); index++) {
            try {
                applyOne(working.scenario(), stepIds, commands.get(index), operationResolver);
            } catch (ScenarioCommandException exception) {
                throw exception.atCommand(index);
            } catch (RuntimeException exception) {
                throw new ScenarioCommandException(
                        "COMMAND_EXECUTION_FAILED",
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                        index,
                        "commands[" + index + "]");
            }
        }

        return new ScenarioDocument(working.scenario(), stepIds);
    }

    private void applyOne(
            ApiTestCase scenario,
            List<String> stepIds,
            ScenarioCommand command,
            OperationResolver operationResolver) {
        if (command instanceof ScenarioCommand.SetRequestHeader value) {
            RequestSpec request = request(step(scenario, stepIds, value.stepId()));
            request.getHeaders().keySet().removeIf(name -> name.equalsIgnoreCase(value.name()));
            request.getHeaders().put(value.name(), value.value());
            return;
        }
        if (command instanceof ScenarioCommand.RemoveRequestHeader value) {
            RequestSpec request = request(step(scenario, stepIds, value.stepId()));
            String existing = request.getHeaders().keySet().stream()
                    .filter(name -> name.equalsIgnoreCase(value.name()))
                    .findFirst()
                    .orElseThrow(() -> error("COMMAND_TARGET_NOT_FOUND", "Header does not exist: " + value.name(), value.stepId()));
            request.getHeaders().remove(existing);
            return;
        }
        if (command instanceof ScenarioCommand.SetQueryParameter value) {
            request(step(scenario, stepIds, value.stepId())).getQuery().put(value.name(), value.value());
            return;
        }
        if (command instanceof ScenarioCommand.SetPathParameter value) {
            request(step(scenario, stepIds, value.stepId())).getPath().put(value.name(), value.value());
            return;
        }
        if (command instanceof ScenarioCommand.SetRequestBody value) {
            request(step(scenario, stepIds, value.stepId())).setBody(codec.toGeneric(value.body()));
            return;
        }
        if (command instanceof ScenarioCommand.SetRequestBodyValue value) {
            RequestSpec request = request(step(scenario, stepIds, value.stepId()));
            request.setBody(setJsonPointer(request.getBody(), value.path(), value.value()));
            return;
        }
        if (command instanceof ScenarioCommand.AddBeforeAction value) {
            actions(step(scenario, stepIds, value.stepId())).getBeforeSend().add(value.action());
            return;
        }
        if (command instanceof ScenarioCommand.AddAfterAction value) {
            actions(step(scenario, stepIds, value.stepId())).getAfterReceive().add(value.action());
            return;
        }
        if (command instanceof ScenarioCommand.AddAssertion value) {
            response(step(scenario, stepIds, value.stepId())).getAssertions().add(value.assertion());
            return;
        }
        if (command instanceof ScenarioCommand.RemoveAssertion value) {
            List<com.example.poc.engine.ValidationSpec> assertions = response(
                    step(scenario, stepIds, value.stepId())).getAssertions();
            if (value.index() < 0 || value.index() >= assertions.size()) {
                throw error("COMMAND_TARGET_NOT_FOUND", "Assertion index does not exist: " + value.index(), value.stepId());
            }
            assertions.remove(value.index());
            return;
        }
        if (command instanceof ScenarioCommand.AddExtraction value) {
            Map<String, Object> extract = response(step(scenario, stepIds, value.stepId())).getExtract();
            if (extract.containsKey(value.name())) {
                throw error("COMMAND_TARGET_ALREADY_EXISTS", "Extraction already exists: " + value.name(), value.stepId());
            }
            extract.put(value.name(), value.extraction());
            return;
        }
        if (command instanceof ScenarioCommand.RemoveExtraction value) {
            Map<String, Object> extract = response(step(scenario, stepIds, value.stepId())).getExtract();
            if (!extract.containsKey(value.name())) {
                throw error("COMMAND_TARGET_NOT_FOUND", "Extraction does not exist: " + value.name(), value.stepId());
            }
            extract.remove(value.name());
            return;
        }
        if (command instanceof ScenarioCommand.AddStep value) {
            String newStepId = value.stepId() == null ? UUID.randomUUID().toString() : value.stepId();
            if (stepIds.contains(newStepId)) {
                throw error("COMMAND_TARGET_ALREADY_EXISTS", "Step reference already exists: " + newStepId, newStepId);
            }
            int insertIndex = value.afterStepId() == null
                    ? scenario.getSteps().size()
                    : indexOf(stepIds, value.afterStepId()) + 1;
            scenario.getSteps().add(insertIndex, value.step());
            stepIds.add(insertIndex, newStepId);
            return;
        }
        if (command instanceof ScenarioCommand.RemoveStep value) {
            int index = indexOf(stepIds, value.stepId());
            scenario.getSteps().remove(index);
            stepIds.remove(index);
            return;
        }
        if (command instanceof ScenarioCommand.MoveStep value) {
            int sourceIndex = indexOf(stepIds, value.stepId());
            if (value.targetIndex() < 0 || value.targetIndex() >= stepIds.size()) {
                throw error("INVALID_COMMAND_FIELD", "targetIndex is outside the Scenario step range", value.stepId());
            }
            ApiStep movedStep = scenario.getSteps().remove(sourceIndex);
            String movedId = stepIds.remove(sourceIndex);
            scenario.getSteps().add(value.targetIndex(), movedStep);
            stepIds.add(value.targetIndex(), movedId);
            return;
        }
        if (command instanceof ScenarioCommand.RenameStep value) {
            step(scenario, stepIds, value.stepId()).setName(value.name());
            return;
        }
        if (command instanceof ScenarioCommand.SelectOperation value) {
            OperationSpec operation = operationResolver.resolve(value.operationId());
            if (operation == null) {
                throw error("COMMAND_TARGET_NOT_FOUND", "Operation does not exist: " + value.operationId(), value.stepId());
            }
            step(scenario, stepIds, value.stepId()).setOperation(operation);
            return;
        }
        throw error("UNKNOWN_COMMAND_TYPE", "Unsupported Scenario command: " + command.getClass().getSimpleName(), "command");
    }

    private ApiStep step(ApiTestCase scenario, List<String> stepIds, String stepId) {
        return scenario.getSteps().get(indexOf(stepIds, stepId));
    }

    private int indexOf(List<String> stepIds, String stepId) {
        int index = stepIds.indexOf(stepId);
        if (index < 0) {
            throw error("COMMAND_TARGET_NOT_FOUND", "Step reference does not exist: " + stepId, stepId);
        }
        return index;
    }

    private RequestSpec request(ApiStep step) {
        if (step.getRequest() == null) {
            step.setRequest(new RequestSpec());
        }
        return step.getRequest();
    }

    private ResponseSpec response(ApiStep step) {
        if (step.getResponse() == null) {
            step.setResponse(new ResponseSpec());
        }
        return step.getResponse();
    }

    private ActionPipelineSpec actions(ApiStep step) {
        if (step.getActions() == null) {
            step.setActions(new ActionPipelineSpec());
        }
        return step.getActions();
    }

    private Object setJsonPointer(Object body, String pointer, Object value) {
        if (pointer == null || !pointer.startsWith("/") || pointer.length() == 1) {
            throw error("INVALID_COMMAND_FIELD", "Body path must be a non-root JSON Pointer", "path");
        }
        String[] tokens = pointer.substring(1).split("/", -1);
        for (int index = 0; index < tokens.length; index++) {
            tokens[index] = tokens[index].replace("~1", "/").replace("~0", "~");
        }

        Object root = body;
        if (root == null) {
            root = isArrayToken(tokens[0]) ? new ArrayList<>() : new LinkedHashMap<String, Object>();
        }
        Object cursor = root;
        for (int index = 0; index < tokens.length - 1; index++) {
            String token = tokens[index];
            String nextToken = tokens[index + 1];
            if (cursor instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = mutableMap(rawMap, pointer);
                cursor = map.computeIfAbsent(
                        token,
                        ignored -> isArrayToken(nextToken) ? new ArrayList<>() : new LinkedHashMap<String, Object>());
                continue;
            }
            if (cursor instanceof List<?> rawList) {
                List<Object> list = mutableList(rawList);
                int itemIndex = arrayIndex(token, list.size(), true, pointer);
                if (itemIndex == list.size()) {
                    list.add(isArrayToken(nextToken) ? new ArrayList<>() : new LinkedHashMap<String, Object>());
                }
                cursor = list.get(itemIndex);
                continue;
            }
            throw error("INVALID_COMMAND_FIELD", "Body path traverses a scalar value", pointer);
        }

        String finalToken = tokens[tokens.length - 1];
        if (cursor instanceof Map<?, ?> rawMap) {
            mutableMap(rawMap, pointer).put(finalToken, value);
            return root;
        }
        if (cursor instanceof List<?> rawList) {
            List<Object> list = mutableList(rawList);
            int itemIndex = arrayIndex(finalToken, list.size(), true, pointer);
            if (itemIndex == list.size()) {
                list.add(value);
            } else {
                list.set(itemIndex, value);
            }
            return root;
        }
        throw error("INVALID_COMMAND_FIELD", "Body path parent is not an object or array", pointer);
    }

    private boolean isArrayToken(String token) {
        return "-".equals(token) || (!token.isEmpty() && token.chars().allMatch(Character::isDigit));
    }

    private int arrayIndex(String token, int size, boolean allowAppend, String path) {
        if ("-".equals(token)) {
            if (allowAppend) {
                return size;
            }
            throw error("INVALID_COMMAND_FIELD", "Array append token is not allowed here", path);
        }
        try {
            int index = Integer.parseInt(token);
            int max = allowAppend ? size : size - 1;
            if (index < 0 || index > max) {
                throw error("INVALID_COMMAND_FIELD", "Array index is outside the body range", path);
            }
            return index;
        } catch (NumberFormatException exception) {
            throw error("INVALID_COMMAND_FIELD", "Expected an array index in body path", path);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableMap(Map<?, ?> map, String path) {
        if (map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw error("INVALID_COMMAND_FIELD", "Body object contains a non-string key", path);
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private List<Object> mutableList(List<?> list) {
        return (List<Object>) list;
    }

    private ScenarioCommandException error(String code, String message, String path) {
        return new ScenarioCommandException(code, message, path);
    }

    @FunctionalInterface
    public interface OperationResolver {
        OperationSpec resolve(String operationId);
    }
}
