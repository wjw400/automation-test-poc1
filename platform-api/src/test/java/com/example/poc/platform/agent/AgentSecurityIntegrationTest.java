package com.example.poc.platform.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.poc.platform.persistence.entity.ProjectEntity;
import com.example.poc.platform.persistence.repo.AgentArtifactRepository;
import com.example.poc.platform.persistence.repo.AgentAuditEventRepository;
import com.example.poc.platform.persistence.repo.AgentEventRepository;
import com.example.poc.platform.persistence.repo.AgentMessageRepository;
import com.example.poc.platform.persistence.repo.AgentTaskRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agent-security;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "agent.fake.chunk-delay=1ms",
        "agent.run-timeout=5s",
        "agent.deepseek.api-key="
})
@AutoConfigureMockMvc
class AgentSecurityIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AgentTaskRepository taskRepository;
    @Autowired private AgentMessageRepository messageRepository;
    @Autowired private AgentEventRepository eventRepository;
    @Autowired private AgentArtifactRepository artifactRepository;
    @Autowired private AgentAuditEventRepository auditRepository;

    private String projectId;

    @BeforeEach
    void reset() {
        artifactRepository.deleteAll();
        eventRepository.deleteAll();
        messageRepository.deleteAll();
        taskRepository.deleteAll();
        auditRepository.deleteAll();
        projectRepository.deleteAll();

        ProjectEntity project = new ProjectEntity();
        project.setCode("security-" + UUID.randomUUID());
        project.setName("Agent Security Test");
        project.setStatus("ACTIVE");
        projectId = projectRepository.saveAndFlush(project).getId();
    }

    @Test
    void bindsTasksToTheServerResolvedUserAndRejectsCrossUserAccess() throws Exception {
        String response = mockMvc.perform(post("/api/projects/{projectId}/agent/tasks", projectId)
                        .header(AgentRequestIdentity.HEADER_NAME, "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Owned task","runtimeType":"FAKE","createdBy":"mallory"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(response).path("id").asText();
        assertThat(objectMapper.readTree(response).path("createdBy").asText()).isEqualTo("alice");

        mockMvc.perform(get("/api/agent/tasks/{taskId}", taskId)
                        .header(AgentRequestIdentity.HEADER_NAME, "bob"))
                .andExpect(status().isForbidden());
        JsonNode bobTasks = json(get("/api/projects/{projectId}/agent/tasks", projectId)
                .header(AgentRequestIdentity.HEADER_NAME, "bob"));
        assertThat(bobTasks).isEmpty();
        mockMvc.perform(delete("/api/agent/tasks/{taskId}", taskId)
                        .header(AgentRequestIdentity.HEADER_NAME, "bob"))
                .andExpect(status().isForbidden());
    }

    @Test
    void redactsSecretsAndRetainsAnAppendOnlyAuditAfterTaskDeletion() throws Exception {
        String taskId = createTask("alice");
        String secretMessage = "Authorization: Bearer raw-token password=hunter2";
        mockMvc.perform(post("/api/agent/tasks/{taskId}/messages", taskId)
                        .header(AgentRequestIdentity.HEADER_NAME, "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", secretMessage))))
                .andExpect(status().isAccepted());
        awaitCompleted(taskId, "alice");

        JsonNode messages = json(get("/api/agent/tasks/{taskId}/messages", taskId)
                .header(AgentRequestIdentity.HEADER_NAME, "alice"));
        assertSafe(messages.toString());
        JsonNode audit = json(get("/api/agent/tasks/{taskId}/audit", taskId)
                .header(AgentRequestIdentity.HEADER_NAME, "alice"));
        assertThat(audit.toString()).contains("task.created").contains("message.user").contains("task.completed");
        assertSafe(audit.toString());

        mockMvc.perform(post("/api/agent/tasks/{taskId}/decisions", taskId)
                        .header(AgentRequestIdentity.HEADER_NAME, "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"FEEDBACK_POSITIVE\",\"details\":{}}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/agent/tasks/{taskId}/decisions", taskId)
                        .header(AgentRequestIdentity.HEADER_NAME, "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DRAFT_APPLIED_TO_EDITOR\",\"details\":{}}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/agent/tasks/{taskId}/decisions", taskId)
                        .header(AgentRequestIdentity.HEADER_NAME, "alice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"SCENARIO_SAVED\",\"details\":{\"manuallyModified\":true}}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/agent/tasks/{taskId}", taskId)
                        .header(AgentRequestIdentity.HEADER_NAME, "alice"))
                .andExpect(status().isNoContent());

        assertThat(taskRepository.existsById(taskId)).isFalse();
        JsonNode retained = json(get("/api/agent/tasks/{taskId}/audit", taskId)
                .header(AgentRequestIdentity.HEADER_NAME, "alice"));
        assertThat(retained.toString()).contains("task.deleted").contains("feedback.positive");
        JsonNode summary = json(get("/api/projects/{projectId}/agent/audit/summary", projectId)
                .header(AgentRequestIdentity.HEADER_NAME, "alice"));
        assertThat(summary.path("taskCount").asInt()).isEqualTo(1);
        assertThat(summary.path("completedCount").asInt()).isEqualTo(1);
        assertThat(summary.path("savedCount").asInt()).isEqualTo(1);
        assertThat(summary.path("manuallyModifiedCount").asInt()).isEqualTo(1);
        assertThat(summary.path("positiveFeedbackCount").asInt()).isEqualTo(1);
        assertThat(summary.path("successRate").asDouble()).isEqualTo(1.0);
        assertThat(summary.path("adoptionRate").asDouble()).isEqualTo(1.0);
    }

    private String createTask(String actorId) throws Exception {
        String response = mockMvc.perform(post("/api/projects/{projectId}/agent/tasks", projectId)
                        .header(AgentRequestIdentity.HEADER_NAME, actorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Secure task\",\"runtimeType\":\"FAKE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asText();
    }

    private void awaitCompleted(String taskId, String actorId) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            JsonNode task = json(get("/api/agent/tasks/{taskId}", taskId)
                    .header(AgentRequestIdentity.HEADER_NAME, actorId));
            if ("COMPLETED".equals(task.path("status").asText())) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Agent Task did not complete");
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String response = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private void assertSafe(String value) {
        assertThat(value)
                .contains("[REDACTED")
                .doesNotContain("raw-token")
                .doesNotContain("hunter2");
    }
}
