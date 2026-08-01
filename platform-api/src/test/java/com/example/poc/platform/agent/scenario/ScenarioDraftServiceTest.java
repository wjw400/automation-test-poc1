package com.example.poc.platform.agent.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.poc.platform.persistence.JsonCodec;
import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.ScenarioDraftEntity;
import com.example.poc.platform.persistence.repo.ApiOperationRepository;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.ScenarioDraftRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScenarioDraftServiceTest {
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final CaseRepository caseRepository = mock(CaseRepository.class);
    private final ScenarioDraftRepository draftRepository = mock(ScenarioDraftRepository.class);
    private final ApiOperationRepository operationRepository = mock(ApiOperationRepository.class);
    private final ScenarioDslCodec codec = new ScenarioDslCodec();
    private final JsonCodec jsonCodec = new JsonCodec(JsonMapper.builder().build());
    private ScenarioDraftService service;

    @BeforeEach
    void setUp() {
        ScenarioCommandDecoder decoder = new ScenarioCommandDecoder(codec);
        ScenarioCommandEngine engine = new ScenarioCommandEngine(codec);
        ScenarioDraftValidator validator = new ScenarioDraftValidator(operationRepository, codec);
        ScenarioDiffService diffService = new ScenarioDiffService(codec);
        service = new ScenarioDraftService(
                projectRepository,
                caseRepository,
                draftRepository,
                operationRepository,
                jsonCodec,
                codec,
                decoder,
                engine,
                validator,
                diffService);
    }

    @Test
    void rejectsStaleDraftRevisionBeforeApplyingCommands() {
        ScenarioDraftEntity draft = draft(1, 0);
        when(draftRepository.findById("draft-1")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.applyCommands("draft-1", plan(0)))
                .isInstanceOf(ScenarioDraftConflictException.class)
                .satisfies(error -> {
                    ScenarioDraftConflictException conflict = (ScenarioDraftConflictException) error;
                    assertThat(conflict.getReason()).isEqualTo("DRAFT_REVISION_CHANGED");
                    assertThat(conflict.getExpectedRevision()).isZero();
                    assertThat(conflict.getActualRevision()).isEqualTo(1);
                });
    }

    @Test
    void rejectsDraftWhenSourceScenarioRevisionChanged() {
        ScenarioDraftEntity draft = draft(0, 2);
        CaseEntity source = new CaseEntity();
        source.setId("scenario-1");
        source.setProjectId("project-1");
        source.setRevision(3L);
        when(draftRepository.findById("draft-1")).thenReturn(Optional.of(draft));
        when(caseRepository.findById("scenario-1")).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.applyCommands("draft-1", plan(0)))
                .isInstanceOf(ScenarioDraftConflictException.class)
                .satisfies(error -> {
                    ScenarioDraftConflictException conflict = (ScenarioDraftConflictException) error;
                    assertThat(conflict.getReason()).isEqualTo("SOURCE_SCENARIO_CHANGED");
                    assertThat(conflict.getExpectedRevision()).isEqualTo(2);
                    assertThat(conflict.getActualRevision()).isEqualTo(3);
                });
    }

    private ScenarioDraftEntity draft(long revision, long sourceRevision) {
        ScenarioDraftEntity draft = new ScenarioDraftEntity();
        draft.setId("draft-1");
        draft.setProjectId("project-1");
        draft.setScenarioId("scenario-1");
        draft.setRevision(revision);
        draft.setSourceRevision(sourceRevision);
        draft.setStatus("ACTIVE");
        return draft;
    }

    private Map<String, Object> plan(long revision) {
        return Map.of(
                "targetType", "SCENARIO_DRAFT",
                "targetId", "draft-1",
                "baseRevision", revision,
                "commands", List.of(Map.of(
                        "type", "SetRequestHeader",
                        "stepId", "step-1",
                        "name", "X-Test",
                        "value", "true")));
    }
}
