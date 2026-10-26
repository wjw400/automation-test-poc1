package com.example.poc.platform.agent.suite;

import java.util.Map;

public sealed interface SuiteCommand permits
        SuiteCommand.UpdateSuiteMetadata,
        SuiteCommand.AddSuiteEntry,
        SuiteCommand.RemoveSuiteEntry,
        SuiteCommand.MoveSuiteEntry,
        SuiteCommand.SetSuiteEntryEnabled,
        SuiteCommand.SetSuiteEntryDisplayName,
        SuiteCommand.SetSuiteEntryRunContext,
        SuiteCommand.SetSuiteEntryVariableOverrides,
        SuiteCommand.ResetSuiteEntryVariableOverrides {
    record UpdateSuiteMetadata(String name, String description, String status) implements SuiteCommand {}
    record AddSuiteEntry(String scenarioId, Integer position, String displayName, Boolean enabled,
                         Map<String, Object> variableOverrides, String environmentId, String profileId) implements SuiteCommand {}
    record RemoveSuiteEntry(String entryId) implements SuiteCommand {}
    record MoveSuiteEntry(String entryId, int targetIndex) implements SuiteCommand {}
    record SetSuiteEntryEnabled(String entryId, boolean enabled) implements SuiteCommand {}
    record SetSuiteEntryDisplayName(String entryId, String displayName) implements SuiteCommand {}
    record SetSuiteEntryRunContext(String entryId, String environmentId, String profileId) implements SuiteCommand {}
    record SetSuiteEntryVariableOverrides(String entryId, Map<String, Object> variableOverrides) implements SuiteCommand {}
    record ResetSuiteEntryVariableOverrides(String entryId) implements SuiteCommand {}
}
