package com.example.poc.platform.agent.scenario;

import com.example.poc.engine.ApiStep;
import com.example.poc.engine.CaseActionSpec;
import com.example.poc.engine.ExtractValueSpec;
import com.example.poc.engine.HttpStatusSpec;
import com.example.poc.engine.ValidationSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ScenarioCommandDecoder {
    private static final Set<String> PLAN_FIELDS = Set.of(
            "targetType", "targetId", "baseRevision", "commands", "assumptions", "warnings", "citations");

    private final ScenarioDslCodec codec;

    public ScenarioCommandDecoder(ScenarioDslCodec codec) {
        this.codec = codec;
    }

    public ScenarioCommandPlan decode(Map<String, Object> input) {
        rejectUnknownFields(input, PLAN_FIELDS, "plan");
        String targetType = requiredText(input, "targetType", "plan.targetType");
        if (!"SCENARIO_DRAFT".equals(targetType)) {
            throw error("INVALID_COMMAND_TARGET", "targetType must be SCENARIO_DRAFT", "plan.targetType");
        }
        String targetId = requiredText(input, "targetId", "plan.targetId");
        long baseRevision = requiredLong(input, "baseRevision", "plan.baseRevision");
        List<?> commandValues = requiredList(input, "commands", "plan.commands");
        if (commandValues.isEmpty()) {
            throw error("EMPTY_COMMAND_PLAN", "Command plan must contain at least one command", "plan.commands");
        }

        List<ScenarioCommand> commands = new ArrayList<>();
        for (int index = 0; index < commandValues.size(); index++) {
            String path = "plan.commands[" + index + "]";
            try {
                commands.add(decodeCommand(asMap(commandValues.get(index), path), path));
            } catch (ScenarioCommandException exception) {
                throw exception.atCommand(index);
            }
        }

        return new ScenarioCommandPlan(
                targetType,
                targetId,
                baseRevision,
                commands,
                optionalStringList(input.get("assumptions"), "plan.assumptions"),
                optionalStringList(input.get("warnings"), "plan.warnings"),
                optionalCitations(input.get("citations"), "plan.citations"));
    }

    private List<ScenarioCommandPlan.Citation> optionalCitations(Object value, String path) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) {
            throw error("INVALID_COMMAND_FIELD", "Expected an array of citations", path);
        }
        List<ScenarioCommandPlan.Citation> citations = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            String itemPath = path + "[" + index + "]";
            Map<String, Object> citation = asMap(list.get(index), itemPath);
            rejectUnknownFields(citation, Set.of("knowledgeId", "scope", "targetId"), itemPath);
            citations.add(new ScenarioCommandPlan.Citation(
                    requiredText(citation, "knowledgeId", itemPath + ".knowledgeId"),
                    requiredText(citation, "scope", itemPath + ".scope"),
                    optionalText(citation.get("targetId"), itemPath + ".targetId")));
        }
        return List.copyOf(citations);
    }

    private ScenarioCommand decodeCommand(Map<String, Object> command, String path) {
        String type = requiredText(command, "type", path + ".type");
        return switch (type) {
            case "SetRequestHeader" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "name", "value"), path);
                yield new ScenarioCommand.SetRequestHeader(
                        stepId(command, path),
                        requiredText(command, "name", path + ".name"),
                        requiredText(command, "value", path + ".value"));
            }
            case "RemoveRequestHeader" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "name"), path);
                yield new ScenarioCommand.RemoveRequestHeader(
                        stepId(command, path), requiredText(command, "name", path + ".name"));
            }
            case "SetQueryParameter" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "name", "value"), path);
                yield new ScenarioCommand.SetQueryParameter(
                        stepId(command, path),
                        requiredText(command, "name", path + ".name"),
                        requiredValue(command, "value", path + ".value"));
            }
            case "SetPathParameter" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "name", "value"), path);
                yield new ScenarioCommand.SetPathParameter(
                        stepId(command, path),
                        requiredText(command, "name", path + ".name"),
                        requiredValue(command, "value", path + ".value"));
            }
            case "SetRequestBody" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "body"), path);
                yield new ScenarioCommand.SetRequestBody(
                        stepId(command, path), requiredValue(command, "body", path + ".body"));
            }
            case "SetRequestBodyValue" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "path", "value"), path);
                yield new ScenarioCommand.SetRequestBodyValue(
                        stepId(command, path),
                        requiredText(command, "path", path + ".path"),
                        requiredValue(command, "value", path + ".value"));
            }
            case "SetResponseStatus" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "status"), path);
                yield new ScenarioCommand.SetResponseStatus(
                        stepId(command, path),
                        codec.convert(requiredValue(command, "status", path + ".status"), HttpStatusSpec.class, path + ".status"));
            }
            case "AddBeforeAction" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "action"), path);
                yield new ScenarioCommand.AddBeforeAction(
                        stepId(command, path),
                        codec.convert(requiredValue(command, "action", path + ".action"), CaseActionSpec.class, path + ".action"));
            }
            case "AddAfterAction" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "action"), path);
                yield new ScenarioCommand.AddAfterAction(
                        stepId(command, path),
                        codec.convert(requiredValue(command, "action", path + ".action"), CaseActionSpec.class, path + ".action"));
            }
            case "AddAssertion" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "assertion"), path);
                yield new ScenarioCommand.AddAssertion(
                        stepId(command, path),
                        codec.convert(requiredValue(command, "assertion", path + ".assertion"), ValidationSpec.class, path + ".assertion"));
            }
            case "RemoveAssertion" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "index"), path);
                yield new ScenarioCommand.RemoveAssertion(
                        stepId(command, path), requiredInteger(command, "index", path + ".index"));
            }
            case "AddExtraction" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "name", "extraction"), path);
                yield new ScenarioCommand.AddExtraction(
                        stepId(command, path),
                        requiredText(command, "name", path + ".name"),
                        codec.convert(requiredValue(command, "extraction", path + ".extraction"), ExtractValueSpec.class, path + ".extraction"));
            }
            case "RemoveExtraction" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "name"), path);
                yield new ScenarioCommand.RemoveExtraction(
                        stepId(command, path), requiredText(command, "name", path + ".name"));
            }
            case "AddStep" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "afterStepId", "targetIndex", "step"), path);
                String afterStepId = optionalText(command.get("afterStepId"), path + ".afterStepId");
                Integer targetIndex = command.get("targetIndex") == null
                        ? null
                        : requiredInteger(command, "targetIndex", path + ".targetIndex");
                if (afterStepId != null && targetIndex != null) {
                    throw error("INVALID_COMMAND_FIELD",
                            "AddStep accepts either afterStepId or targetIndex, not both",
                            path);
                }
                yield new ScenarioCommand.AddStep(
                        optionalText(command.get("stepId"), path + ".stepId"),
                        afterStepId,
                        targetIndex,
                        codec.convert(requiredValue(command, "step", path + ".step"), ApiStep.class, path + ".step"));
            }
            case "RemoveStep" -> {
                rejectUnknownFields(command, Set.of("type", "stepId"), path);
                yield new ScenarioCommand.RemoveStep(stepId(command, path));
            }
            case "MoveStep" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "targetIndex"), path);
                yield new ScenarioCommand.MoveStep(
                        stepId(command, path), requiredInteger(command, "targetIndex", path + ".targetIndex"));
            }
            case "RenameStep" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "name"), path);
                yield new ScenarioCommand.RenameStep(
                        stepId(command, path), requiredText(command, "name", path + ".name"));
            }
            case "SelectOperation" -> {
                rejectUnknownFields(command, Set.of("type", "stepId", "operationRefId", "operationId"), path);
                String operationRefId = optionalText(command.get("operationRefId"), path + ".operationRefId");
                String legacyOperationId = optionalText(command.get("operationId"), path + ".operationId");
                if (operationRefId != null && legacyOperationId != null) {
                    throw error("INVALID_COMMAND_FIELD",
                            "SelectOperation accepts operationRefId only",
                            path);
                }
                yield new ScenarioCommand.SelectOperation(
                        stepId(command, path),
                        operationRefId == null
                                ? requiredText(command, "operationId", path + ".operationId")
                                : operationRefId);
            }
            default -> throw error("UNKNOWN_COMMAND_TYPE", "Unsupported Scenario command: " + type, path + ".type");
        };
    }

    private String stepId(Map<String, Object> command, String path) {
        return requiredText(command, "stepId", path + ".stepId");
    }

    private void rejectUnknownFields(Map<String, Object> input, Set<String> allowed, String path) {
        input.keySet().stream()
                .filter(field -> !allowed.contains(field))
                .findFirst()
                .ifPresent(field -> {
                    throw error("UNKNOWN_COMMAND_FIELD", "Unknown field: " + field, path + "." + field);
                });
    }

    private Map<String, Object> asMap(Object value, String path) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw error("INVALID_COMMAND_FIELD", "Expected an object", path);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) raw;
        return result;
    }

    private List<?> requiredList(Map<String, Object> input, String field, String path) {
        Object value = input.get(field);
        if (!(value instanceof List<?> list)) {
            throw error("INVALID_COMMAND_FIELD", "Expected an array", path);
        }
        return list;
    }

    private Object requiredValue(Map<String, Object> input, String field, String path) {
        if (!input.containsKey(field)) {
            throw error("MISSING_COMMAND_FIELD", "Missing required field: " + field, path);
        }
        return input.get(field);
    }

    private String requiredText(Map<String, Object> input, String field, String path) {
        return requiredTextValue(input.get(field), path);
    }

    private String requiredTextValue(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw error("INVALID_COMMAND_FIELD", "Expected a non-blank string", path);
        }
        return text;
    }

    private String optionalText(Object value, String path) {
        if (value == null) {
            return null;
        }
        return requiredTextValue(value, path);
    }

    private long requiredLong(Map<String, Object> input, String field, String path) {
        Object value = input.get(field);
        if (!(value instanceof Number number)) {
            throw error("INVALID_COMMAND_FIELD", "Expected an integer", path);
        }
        return number.longValue();
    }

    private int requiredInteger(Map<String, Object> input, String field, String path) {
        long value = requiredLong(input, field, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw error("INVALID_COMMAND_FIELD", "Integer is out of range", path);
        }
        return (int) value;
    }

    private List<String> optionalStringList(Object value, String path) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw error("INVALID_COMMAND_FIELD", "Expected an array of strings", path);
        }
        List<String> result = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            result.add(requiredTextValue(list.get(index), path + "[" + index + "]"));
        }
        return result;
    }

    private ScenarioCommandException error(String code, String message, String path) {
        return new ScenarioCommandException(code, message, path);
    }
}
