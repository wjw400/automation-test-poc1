package com.example.poc.platform.agent.suite;

import com.example.poc.platform.agent.scenario.ScenarioCommandException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SuiteCommandDecoder {
    private static final Set<String> PLAN_FIELDS = Set.of(
            "targetType", "targetId", "baseRevision", "commands", "assumptions", "warnings", "citations");

    public SuiteCommandPlan decode(Map<String, Object> input) {
        rejectUnknown(input, PLAN_FIELDS, "plan");
        String targetType = text(input, "targetType", "plan.targetType");
        if (!"SUITE_DRAFT".equals(targetType)) throw error("INVALID_COMMAND_TARGET", "targetType must be SUITE_DRAFT", "plan.targetType");
        List<?> values = list(input, "commands", "plan.commands");
        if (values.isEmpty()) throw error("EMPTY_COMMAND_PLAN", "Command plan must contain at least one command", "plan.commands");
        List<SuiteCommand> commands = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            try { commands.add(command(map(values.get(index), "plan.commands[" + index + "]"), "plan.commands[" + index + "]")); }
            catch (ScenarioCommandException exception) { throw exception.atCommand(index); }
        }
        return new SuiteCommandPlan(targetType, text(input, "targetId", "plan.targetId"), number(input, "baseRevision", "plan.baseRevision"),
                commands, strings(input.get("assumptions"), "plan.assumptions"), strings(input.get("warnings"), "plan.warnings"), citations(input.get("citations")));
    }

    private SuiteCommand command(Map<String, Object> value, String path) {
        String type = text(value, "type", path + ".type");
        return switch (type) {
            case "UpdateSuiteMetadata" -> { rejectUnknown(value, Set.of("type", "name", "description", "status"), path); yield new SuiteCommand.UpdateSuiteMetadata(optional(value, "name", path), optional(value, "description", path), optional(value, "status", path)); }
            case "AddSuiteEntry" -> { rejectUnknown(value, Set.of("type", "scenarioId", "position", "displayName", "enabled", "variableOverrides", "environmentId", "profileId"), path); yield new SuiteCommand.AddSuiteEntry(text(value, "scenarioId", path + ".scenarioId"), optionalInteger(value, "position", path), optional(value, "displayName", path), optionalBoolean(value, "enabled", path), object(value.get("variableOverrides"), path + ".variableOverrides"), optional(value, "environmentId", path), optional(value, "profileId", path)); }
            case "RemoveSuiteEntry" -> { rejectUnknown(value, Set.of("type", "entryId"), path); yield new SuiteCommand.RemoveSuiteEntry(text(value, "entryId", path + ".entryId")); }
            case "MoveSuiteEntry" -> { rejectUnknown(value, Set.of("type", "entryId", "targetIndex"), path); yield new SuiteCommand.MoveSuiteEntry(text(value, "entryId", path + ".entryId"), integer(value, "targetIndex", path)); }
            case "SetSuiteEntryEnabled" -> { rejectUnknown(value, Set.of("type", "entryId", "enabled"), path); yield new SuiteCommand.SetSuiteEntryEnabled(text(value, "entryId", path + ".entryId"), bool(value, "enabled", path)); }
            case "SetSuiteEntryDisplayName" -> { rejectUnknown(value, Set.of("type", "entryId", "displayName"), path); yield new SuiteCommand.SetSuiteEntryDisplayName(text(value, "entryId", path + ".entryId"), optional(value, "displayName", path)); }
            case "SetSuiteEntryRunContext" -> { rejectUnknown(value, Set.of("type", "entryId", "environmentId", "profileId"), path); yield new SuiteCommand.SetSuiteEntryRunContext(text(value, "entryId", path + ".entryId"), optional(value, "environmentId", path), optional(value, "profileId", path)); }
            case "SetSuiteEntryVariableOverrides" -> { rejectUnknown(value, Set.of("type", "entryId", "variableOverrides"), path); yield new SuiteCommand.SetSuiteEntryVariableOverrides(text(value, "entryId", path + ".entryId"), objectRequired(value, "variableOverrides", path)); }
            case "ResetSuiteEntryVariableOverrides" -> { rejectUnknown(value, Set.of("type", "entryId"), path); yield new SuiteCommand.ResetSuiteEntryVariableOverrides(text(value, "entryId", path + ".entryId")); }
            default -> throw error("UNKNOWN_COMMAND_TYPE", "Unsupported Suite command: " + type, path + ".type");
        };
    }

    private List<SuiteCommandPlan.Citation> citations(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw error("INVALID_COMMAND_FIELD", "Expected an array", "plan.citations");
        List<SuiteCommandPlan.Citation> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) { Map<String, Object> citation = map(list.get(i), "plan.citations[" + i + "]"); rejectUnknown(citation, Set.of("knowledgeId", "scope", "targetId"), "plan.citations[" + i + "]"); result.add(new SuiteCommandPlan.Citation(text(citation, "knowledgeId", "plan.citations[" + i + "].knowledgeId"), text(citation, "scope", "plan.citations[" + i + "].scope"), optional(citation, "targetId", "plan.citations[" + i + "]"))); }
        return List.copyOf(result);
    }
    private List<String> strings(Object value, String path) { if (value == null) return List.of(); if (!(value instanceof List<?> list)) throw error("INVALID_COMMAND_FIELD", "Expected an array", path); List<String> r = new ArrayList<>(); for (int i = 0; i < list.size(); i++) r.add(textValue(list.get(i), path + "[" + i + "]")); return List.copyOf(r); }
    private void rejectUnknown(Map<String, Object> value, Set<String> fields, String path) { value.keySet().stream().filter(key -> !fields.contains(key)).findFirst().ifPresent(key -> { throw error("UNKNOWN_COMMAND_FIELD", "Unknown field: " + key, path + "." + key); }); }
    @SuppressWarnings("unchecked") private Map<String, Object> map(Object value, String path) { if (!(value instanceof Map<?, ?> raw)) throw error("INVALID_COMMAND_FIELD", "Expected an object", path); return (Map<String, Object>) raw; }
    private Map<String, Object> object(Object value, String path) { return value == null ? Map.of() : map(value, path); }
    private Map<String, Object> objectRequired(Map<String, Object> source, String key, String path) { if (!source.containsKey(key)) throw error("MISSING_COMMAND_FIELD", "Missing required field: " + key, path + "." + key); return map(source.get(key), path + "." + key); }
    private List<?> list(Map<String, Object> value, String key, String path) { Object raw = value.get(key); if (!(raw instanceof List<?> result)) throw error("INVALID_COMMAND_FIELD", "Expected an array", path); return result; }
    private String text(Map<String, Object> value, String key, String path) { return textValue(value.get(key), path); }
    private String textValue(Object value, String path) { if (!(value instanceof String result) || result.isBlank()) throw error("INVALID_COMMAND_FIELD", "Expected a non-blank string", path); return result; }
    private String optional(Map<String, Object> value, String key, String path) { Object raw = value.get(key); return raw == null ? null : textValue(raw, path + "." + key); }
    private long number(Map<String, Object> value, String key, String path) { Object raw = value.get(key); if (!(raw instanceof Number number)) throw error("INVALID_COMMAND_FIELD", "Expected an integer", path); return number.longValue(); }
    private int integer(Map<String, Object> value, String key, String path) { long result = number(value, key, path + "." + key); if (result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) throw error("INVALID_COMMAND_FIELD", "Integer is out of range", path + "." + key); return (int) result; }
    private Integer optionalInteger(Map<String, Object> value, String key, String path) { return value.get(key) == null ? null : integer(value, key, path); }
    private boolean bool(Map<String, Object> value, String key, String path) { if (!(value.get(key) instanceof Boolean result)) throw error("INVALID_COMMAND_FIELD", "Expected a boolean", path + "." + key); return result; }
    private Boolean optionalBoolean(Map<String, Object> value, String key, String path) { return value.get(key) == null ? null : bool(value, key, path); }
    private ScenarioCommandException error(String code, String message, String path) { return new ScenarioCommandException(code, message, path); }
}
