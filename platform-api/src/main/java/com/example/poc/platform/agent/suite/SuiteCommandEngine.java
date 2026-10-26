package com.example.poc.platform.agent.suite;

import com.example.poc.platform.agent.scenario.ScenarioCommandException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SuiteCommandEngine {
    public SuiteDraftDocument apply(SuiteDraftDocument source, List<SuiteCommand> commands) {
        String name = source.name(); String description = source.description(); String status = source.status();
        List<SuiteDraftDocument.Entry> entries = new ArrayList<>(source.entries());
        for (int index = 0; index < commands.size(); index++) {
            try {
                SuiteCommand command = commands.get(index);
                if (command instanceof SuiteCommand.UpdateSuiteMetadata value) { name = value.name() == null ? name : value.name(); description = value.description() == null ? description : value.description(); status = value.status() == null ? status : value.status(); }
                else if (command instanceof SuiteCommand.AddSuiteEntry value) { int position = value.position() == null ? entries.size() : value.position(); if (position < 0 || position > entries.size()) throw error("INVALID_COMMAND_FIELD", "position is outside the Suite insertion range", "position"); entries.add(position, new SuiteDraftDocument.Entry(UUID.randomUUID().toString(), value.scenarioId(), value.displayName(), value.enabled() == null || value.enabled(), value.variableOverrides(), value.environmentId(), value.profileId())); }
                else if (command instanceof SuiteCommand.RemoveSuiteEntry value) entries.remove(indexOf(entries, value.entryId()));
                else if (command instanceof SuiteCommand.MoveSuiteEntry value) { int from = indexOf(entries, value.entryId()); if (value.targetIndex() < 0 || value.targetIndex() >= entries.size()) throw error("INVALID_COMMAND_FIELD", "targetIndex is outside the Suite entry range", value.entryId()); entries.add(value.targetIndex(), entries.remove(from)); }
                else if (command instanceof SuiteCommand.SetSuiteEntryEnabled value) replace(entries, value.entryId(), entry(entries, value.entryId(), entry -> new SuiteDraftDocument.Entry(entry.id(), entry.scenarioId(), entry.displayName(), value.enabled(), entry.variableOverrides(), entry.environmentId(), entry.profileId())));
                else if (command instanceof SuiteCommand.SetSuiteEntryDisplayName value) replace(entries, value.entryId(), entry(entries, value.entryId(), entry -> new SuiteDraftDocument.Entry(entry.id(), entry.scenarioId(), value.displayName(), entry.enabled(), entry.variableOverrides(), entry.environmentId(), entry.profileId())));
                else if (command instanceof SuiteCommand.SetSuiteEntryRunContext value) replace(entries, value.entryId(), entry(entries, value.entryId(), entry -> new SuiteDraftDocument.Entry(entry.id(), entry.scenarioId(), entry.displayName(), entry.enabled(), entry.variableOverrides(), value.environmentId(), value.profileId())));
                else if (command instanceof SuiteCommand.SetSuiteEntryVariableOverrides value) replace(entries, value.entryId(), entry(entries, value.entryId(), entry -> new SuiteDraftDocument.Entry(entry.id(), entry.scenarioId(), entry.displayName(), entry.enabled(), value.variableOverrides(), entry.environmentId(), entry.profileId())));
                else if (command instanceof SuiteCommand.ResetSuiteEntryVariableOverrides value) replace(entries, value.entryId(), entry(entries, value.entryId(), entry -> new SuiteDraftDocument.Entry(entry.id(), entry.scenarioId(), entry.displayName(), entry.enabled(), java.util.Map.of(), entry.environmentId(), entry.profileId())));
            } catch (ScenarioCommandException exception) { throw exception.atCommand(index); }
        }
        return new SuiteDraftDocument(name, description, status, entries);
    }
    private SuiteDraftDocument.Entry entry(List<SuiteDraftDocument.Entry> entries, String id, java.util.function.Function<SuiteDraftDocument.Entry, SuiteDraftDocument.Entry> map) { int index = indexOf(entries, id); return map.apply(entries.get(index)); }
    private void replace(List<SuiteDraftDocument.Entry> entries, String id, SuiteDraftDocument.Entry value) { entries.set(indexOf(entries, id), value); }
    private int indexOf(List<SuiteDraftDocument.Entry> entries, String id) { for (int i = 0; i < entries.size(); i++) if (id.equals(entries.get(i).id())) return i; throw error("COMMAND_TARGET_NOT_FOUND", "Suite entry does not exist: " + id, id); }
    private ScenarioCommandException error(String code, String message, String path) { return new ScenarioCommandException(code, message, path); }
}
