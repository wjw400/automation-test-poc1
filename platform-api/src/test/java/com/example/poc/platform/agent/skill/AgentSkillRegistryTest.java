package com.example.poc.platform.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.AgentPermissionMode;
import com.example.poc.platform.agent.context.AgentContextSelection;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AgentSkillRegistryTest {
    private final AgentSkillRegistry registry = new AgentSkillRegistry();

    @Test
    void selectsAuthoringForScenariosAndSecurePayloadOnlyWhenRelevant() {
        AgentTaskRecord task = task("SCENARIO", "scenario-1");

        assertThat(registry.select(task, "Add a response assertion", "plain Scenario").stream()
                .map(AgentSkill::name))
                .containsExactly("scenario-authoring");
        assertThat(registry.select(task, "Encrypt and sign the XML request", "plain Scenario").stream()
                .map(AgentSkill::name))
                .containsExactly("scenario-authoring", "secure-payload");
        assertThat(registry.select(task, "Check the OpenAPI schema and required fields", "plain Scenario").stream()
                .map(AgentSkill::name))
                .containsExactly("scenario-authoring", "openapi-analysis");
        assertThat(registry.select(task, "Update the body", "Scenario variable named schema").stream()
                .map(AgentSkill::name))
                .containsExactly("scenario-authoring");
    }

    @Test
    void refusesDisabledSkills() {
        assertThatThrownBy(() -> registry.require("failure-analysis"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void selectsAnalysisSkillForReadOnlyScenarioWithoutWriteSkills() {
        AgentTaskRecord task = task("SCENARIO", "scenario-1", AgentPermissionMode.READ_ONLY);

        assertThat(registry.select(task, "Explain the current assertions", "plain Scenario").stream()
                .map(AgentSkill::name))
                .containsExactly("scenario-analysis");
        assertThat(registry.select(task, "Encrypt the request", "plain Scenario").stream()
                .map(AgentSkill::name))
                .containsExactly("scenario-analysis");
    }

    private AgentTaskRecord task(String targetType, String targetId) {
        return task(targetType, targetId, AgentPermissionMode.REVIEW_REQUIRED);
    }

    private AgentTaskRecord task(String targetType, String targetId, AgentPermissionMode permissionMode) {
        Instant now = Instant.now();
        return new AgentTaskRecord(
                "task-1", "project-1", "Task", "READY", targetType, targetId,
                permissionMode, 1, "FAKE", "fake-agent-v1", "tester",
                0, 4096, AgentContextSelection.empty(), 0, now, now, null);
    }
}
