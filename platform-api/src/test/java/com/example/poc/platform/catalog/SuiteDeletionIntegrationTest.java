package com.example.poc.platform.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.poc.platform.persistence.entity.CaseEntity;
import com.example.poc.platform.persistence.entity.ProjectEntity;
import com.example.poc.platform.persistence.entity.ScenarioDraftEntity;
import com.example.poc.platform.persistence.entity.SuiteEntity;
import com.example.poc.platform.persistence.entity.SuiteRunEntity;
import com.example.poc.platform.persistence.repo.CaseRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import com.example.poc.platform.persistence.repo.ScenarioDraftRepository;
import com.example.poc.platform.persistence.repo.SuiteRepository;
import com.example.poc.platform.persistence.repo.SuiteRunRepository;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:suite-deletion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "agent.deepseek.api-key="
})
@AutoConfigureMockMvc
class SuiteDeletionIntegrationTest {
    @Autowired ProjectRepository projects;
    @Autowired SuiteRepository suites;
    @Autowired CaseRepository cases;
    @Autowired ScenarioDraftRepository drafts;
    @Autowired SuiteRunRepository runs;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mvc;

    @Test
    void migrationAllowsDeletingSuiteWhilePreservingDraftsScenariosAndRunHistory() throws Exception {
        ProjectEntity project = new ProjectEntity();
        project.setCode("suite-delete-" + UUID.randomUUID());
        project.setName("Suite deletion");
        project.setStatus("ACTIVE");
        projects.saveAndFlush(project);
        SuiteEntity suite = new SuiteEntity();
        suite.setProjectId(project.getId());
        suite.setName("Source Suite");
        suite.setStatus("ACTIVE");
        suites.saveAndFlush(suite);
        CaseEntity scenario = new CaseEntity();
        scenario.setProjectId(project.getId());
        scenario.setName("Published Scenario");
        scenario.setCode("published");
        scenario.setContent("name: Published Scenario\nsteps: []\n");
        scenario.setTagsJson("[]");
        scenario.setDslVersion("v1");
        scenario.setStatus("ACTIVE");
        cases.saveAndFlush(scenario);
        ScenarioDraftEntity active = draft(project.getId(), suite.getId(), null, "ACTIVE");
        ScenarioDraftEntity published = draft(project.getId(), suite.getId(), scenario.getId(), "PUBLISHED");
        SuiteRunEntity run = new SuiteRunEntity();
        run.setProjectId(project.getId());
        run.setSuiteId(suite.getId());
        run.setSuiteName(suite.getName());
        run.setTriggeredBy("tester");
        run.setStatus("PASSED");
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        runs.saveAndFlush(run);

        // Hibernate-created test schemas omit scalar-id foreign keys. Recreate
        // the V24 constraint to reproduce the production failure before V27.
        jdbc.execute("alter table platform_scenario_draft add constraint "
                + "platform_scenario_draft_source_suite_id_fkey foreign key (source_suite_id) references platform_suite(id)");
        assertThatThrownBy(() -> jdbc.update("delete from platform_suite where id = ?", suite.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V27__preserve_scenario_drafts_after_suite_deletion.sql")).execute(dataSource);

        mvc.perform(delete("/api/suites/{suiteId}", suite.getId())).andExpect(status().isOk());

        assertThat(suites.findById(suite.getId())).isEmpty();
        assertThat(drafts.findById(active.getId())).hasValueSatisfying(saved -> {
            assertThat(saved.getSourceSuiteId()).isEqualTo(suite.getId());
            assertThat(saved.getContent()).isEqualTo(active.getContent());
            assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        });
        assertThat(drafts.findById(published.getId())).hasValueSatisfying(saved -> {
            assertThat(saved.getScenarioId()).isEqualTo(scenario.getId());
            assertThat(saved.getSourceSuiteId()).isEqualTo(suite.getId());
            assertThat(saved.getStatus()).isEqualTo("PUBLISHED");
        });
        assertThat(cases.findById(scenario.getId())).isPresent();
        assertThat(runs.findById(run.getId())).hasValueSatisfying(saved -> {
            assertThat(saved.getSuiteId()).isEqualTo(suite.getId());
            assertThat(saved.getSuiteName()).isEqualTo("Source Suite");
        });
    }

    private ScenarioDraftEntity draft(String projectId, String suiteId, String scenarioId, String status) {
        ScenarioDraftEntity draft = new ScenarioDraftEntity();
        draft.setProjectId(projectId);
        draft.setSourceSuiteId(suiteId);
        draft.setScenarioId(scenarioId);
        draft.setBaseContent("name: Draft\nsteps: []\n");
        draft.setContent("name: Draft\nsteps: []\n");
        draft.setBaseStepIdsJson("[]");
        draft.setStepIdsJson("[]");
        draft.setStatus(status);
        return drafts.saveAndFlush(draft);
    }
}
