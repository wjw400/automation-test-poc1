package com.example.poc.platform.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.poc.platform.persistence.entity.ProjectEntity;
import com.example.poc.platform.persistence.repo.AgentArtifactRepository;
import com.example.poc.platform.persistence.repo.AgentEventRepository;
import com.example.poc.platform.persistence.repo.AgentMessageRepository;
import com.example.poc.platform.persistence.repo.AgentTaskRepository;
import com.example.poc.platform.persistence.repo.ProjectRepository;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
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
        "spring.datasource.url=jdbc:h2:mem:agent-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "agent.fake.chunk-delay=5ms",
        "agent.run-timeout=5s",
        "agent.max-turns=5",
        "agent.deepseek.api-key=",
        "agent.copilot.enabled=false"
})
@AutoConfigureMockMvc
class AgentApiIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AgentTaskRepository taskRepository;
    @Autowired private AgentMessageRepository messageRepository;
    @Autowired private AgentEventRepository eventRepository;
    @Autowired private AgentArtifactRepository artifactRepository;
    @Autowired private AgentProperties agentProperties;

    private String projectId;

    @BeforeEach
    void reset() {
        artifactRepository.deleteAll();
        eventRepository.deleteAll();
        messageRepository.deleteAll();
        taskRepository.deleteAll();
        projectRepository.deleteAll();

        ProjectEntity project = new ProjectEntity();
        project.setCode("agent-" + UUID.randomUUID());
        project.setName("Agent API Test");
        project.setStatus("ACTIVE");
        projectId = projectRepository.saveAndFlush(project).getId();
    }

    @Test
    void completesMultipleAuditableFakeTurnsAndExposesResumableEvents() throws Exception {
        String taskId = createTask(4096);

        send(taskId, "add a correlation header").andExpect(status().isAccepted());
        awaitTask(taskId, task -> "COMPLETED".equals(task.path("status").asText()));
        send(taskId, "now add an extraction").andExpect(status().isAccepted());
        awaitTask(taskId, task -> "COMPLETED".equals(task.path("status").asText())
                && task.path("iterationCount").asInt() == 2);

        JsonNode messages = json(get("/api/agent/tasks/{taskId}/messages", taskId));
        assertThat(messages.size()).isEqualTo(4);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("USER");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("ASSISTANT");

        JsonNode events = json(get("/api/agent/tasks/{taskId}/events", taskId)
                .accept(MediaType.APPLICATION_JSON));
        assertThat(events).isNotEmpty();
        long previous = 0;
        for (JsonNode event : events) {
            long sequence = event.path("sequence").asLong();
            assertThat(sequence).isGreaterThan(previous);
            previous = sequence;
        }
        assertThat(eventTypes(events)).contains("task.started", "message.delta", "message.completed",
                "artifact.created", "task.completed");

        JsonNode artifacts = json(get("/api/agent/tasks/{taskId}/artifacts", taskId));
        assertThat(artifacts.size()).isEqualTo(4);
        assertThat(artifacts.get(0).path("artifactType").asText()).isEqualTo("CONTEXT_SNAPSHOT");
        assertThat(artifacts.get(1).path("artifactType").asText()).isEqualTo("RUNTIME_TRACE");

        mockMvc.perform(get("/api/agent/tasks/{taskId}/events", taskId)
                        .header("Last-Event-ID", previous - 1)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        mockMvc.perform(delete("/api/agent/tasks/{taskId}", taskId))
                .andExpect(status().isNoContent());
        assertThat(taskRepository.existsById(taskId)).isFalse();
        assertThat(messageRepository.countByTaskId(taskId)).isZero();
        assertThat(eventRepository.countByTaskId(taskId)).isZero();
    }

    @Test
    void rejectsConcurrentTurnsAndCanCancelTheRunningTurn() throws Exception {
        String taskId = createTask(4096);
        String longMessage = "slow ".repeat(400);
        send(taskId, longMessage).andExpect(status().isAccepted());

        send(taskId, "second turn").andExpect(status().isConflict());
        mockMvc.perform(post("/api/agent/tasks/{taskId}/cancel", taskId))
                .andExpect(status().isOk());

        JsonNode cancelled = awaitTask(taskId, task -> "CANCELLED".equals(task.path("status").asText()));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        JsonNode events = json(get("/api/agent/tasks/{taskId}/events", taskId)
                .accept(MediaType.APPLICATION_JSON));
        assertThat(eventTypes(events)).contains("task.cancelled");
    }

    @Test
    void pausesTheTurnWhenRuntimeOutputExceedsItsTokenBudget() throws Exception {
        String taskId = createTask(5);
        send(taskId, "hello").andExpect(status().isAccepted());

        JsonNode paused = awaitTask(taskId, task -> "WAITING_FOR_USER".equals(task.path("status").asText()));
        assertThat(paused.path("status").asText()).isEqualTo("WAITING_FOR_USER");
        JsonNode events = json(get("/api/agent/tasks/{taskId}/events", taskId)
                .accept(MediaType.APPLICATION_JSON));
        assertThat(eventTypes(events)).contains("task.paused");
        assertThat(json(get("/api/agent/tasks/{taskId}/messages", taskId)).size()).isEqualTo(1);
    }

    @Test
    void letsTheUserStopAPausedTaskWithoutResendingTheOriginalMessage() throws Exception {
        String taskId = createTask(5);
        send(taskId, "hello").andExpect(status().isAccepted());
        awaitTask(taskId, task -> "WAITING_FOR_USER".equals(task.path("status").asText()));

        mockMvc.perform(post("/api/agent/tasks/{taskId}/continuation-decision", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"STOP\"}"))
                .andExpect(status().isOk());

        JsonNode cancelled = awaitTask(taskId, task -> "CANCELLED".equals(task.path("status").asText()));
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(json(get("/api/agent/tasks/{taskId}/messages", taskId)).size()).isEqualTo(1);
        JsonNode events = json(get("/api/agent/tasks/{taskId}/events", taskId)
                .accept(MediaType.APPLICATION_JSON));
        assertThat(eventTypes(events)).contains("continuation.declined", "task.cancelled");
    }

    @Test
    void continuesFromTheExistingConversationWithoutAddingAnotherUserMessage() throws Exception {
        String taskId = createTask(5);
        send(taskId, "hello").andExpect(status().isAccepted());
        awaitTask(taskId, task -> "WAITING_FOR_USER".equals(task.path("status").asText()));

        mockMvc.perform(post("/api/agent/tasks/{taskId}/continuation-decision", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONTINUE\"}"))
                .andExpect(status().isAccepted());

        JsonNode pausedAgain = awaitTask(taskId, task -> "WAITING_FOR_USER".equals(task.path("status").asText())
                && task.path("iterationCount").asInt() == 2);
        assertThat(pausedAgain.path("status").asText()).isEqualTo("WAITING_FOR_USER");
        assertThat(json(get("/api/agent/tasks/{taskId}/messages", taskId)).size()).isEqualTo(1);
        JsonNode events = json(get("/api/agent/tasks/{taskId}/events", taskId)
                .accept(MediaType.APPLICATION_JSON));
        assertThat(eventTypes(events)).contains("continuation.started");
    }

    @Test
    void letsTheUserContinueBeyondThePreviousHardLimit() throws Exception {
        String taskId = createTask(5);
        send(taskId, "request requiring several continuations").andExpect(status().isAccepted());
        awaitTask(taskId, task -> "WAITING_FOR_USER".equals(task.path("status").asText()));

        for (int iteration = 2; iteration <= 6; iteration++) {
            mockMvc.perform(post("/api/agent/tasks/{taskId}/continuation-decision", taskId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"decision\":\"CONTINUE\"}"))
                    .andExpect(status().isAccepted());
            int expectedIteration = iteration;
            awaitTask(taskId, task -> "WAITING_FOR_USER".equals(task.path("status").asText())
                    && task.path("iterationCount").asInt() == expectedIteration);
        }

        JsonNode events = json(get("/api/agent/tasks/{taskId}/events", taskId)
                .accept(MediaType.APPLICATION_JSON));
        long continuations = java.util.stream.StreamSupport.stream(events.spliterator(), false)
                .filter(event -> "continuation.started".equals(event.path("eventType").asText()))
                .count();
        assertThat(continuations).isEqualTo(5);
    }

    @Test
    void createsCopilotTasksWithConfiguredModelWithoutCallingTheProvider() throws Exception {
        var config = agentProperties.getCopilot();
        String originalModel = config.getModel();
        String originalToken = config.getApiToken();
        List<String> originalModels = config.getModels();
        try {
            config.setEnabled(true);
            config.setModel("copilot-test-model");
            config.setModels(List.of("copilot-second-model"));
            config.setApiToken("copilot-test-secret");
            JsonNode task = json(post("/api/projects/{projectId}/agent/tasks", projectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Copilot task\",\"runtimeType\":\"COPILOT\"}"));
            assertThat(task.path("runtimeType").asText()).isEqualTo("COPILOT");
            assertThat(task.path("modelId").asText()).isEqualTo("copilot-test-model");
            assertThat(task.path("status").asText()).isEqualTo("READY");
            assertThat(task.toString()).doesNotContain("copilot-test-secret");
            assertThat(json(get("/api/agent/runtimes")).toString()).doesNotContain("copilot-test-secret");
            assertThat(taskRepository.findById(task.path("id").asText()).orElseThrow().getContextJson())
                    .doesNotContain("copilot-test-secret");
            mockMvc.perform(post("/api/projects/{projectId}/agent/tasks", projectId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"runtimeType\":\"COPILOT\",\"modelId\":\"not-allowed\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            config.setEnabled(false);
            config.setModel(originalModel);
            config.setModels(originalModels);
            config.setApiToken(originalToken);
        }
    }

    @Test
    void reportsCopilotAsUnavailableUntilEnabled() throws Exception {
        JsonNode runtimes = json(get("/api/agent/runtimes"));
        JsonNode copilot = null;
        for (JsonNode runtime : runtimes) {
            if ("COPILOT".equals(runtime.path("type").asText())) copilot = runtime;
        }
        assertThat(copilot).isNotNull();
        assertThat(copilot.path("capabilities").path("available").asBoolean()).isFalse();
        assertThat(copilot.path("capabilities").path("toolCalling").asBoolean()).isTrue();
        assertThat(copilot.toString()).doesNotContain("apiToken", "api-token");
        mockMvc.perform(post("/api/projects/{projectId}/agent/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Copilot task\",\"runtimeType\":\"COPILOT\"}"))
                .andExpect(status().isServiceUnavailable());
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void reportsDeepSeekAsUnavailableWithoutPersistingItsApiKey() throws Exception {
        JsonNode runtimes = json(get("/api/agent/runtimes"));
        JsonNode deepseek = null;
        for (JsonNode runtime : runtimes) {
            if ("DEEPSEEK".equals(runtime.path("type").asText())) deepseek = runtime;
        }
        assertThat(deepseek).isNotNull();
        assertThat(deepseek.path("capabilities").path("available").asBoolean()).isFalse();

        mockMvc.perform(post("/api/projects/{projectId}/agent/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"DeepSeek task","runtimeType":"DEEPSEEK"}
                                """))
                .andExpect(status().isServiceUnavailable());
        assertThat(taskRepository.count()).isZero();
    }

    @Test
    void renamesATaskAndRecordsTheChange() throws Exception {
        String taskId = createTask(4096);

        String response = mockMvc.perform(patch("/api/agent/tasks/{taskId}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Header authoring\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(response).path("title").asText()).isEqualTo("Header authoring");
        JsonNode events = json(get("/api/agent/tasks/{taskId}/events", taskId)
                .accept(MediaType.APPLICATION_JSON));
        assertThat(eventTypes(events)).contains("task.updated");
    }

    private String createTask(long tokenBudget) throws Exception {
        String response = mockMvc.perform(post("/api/projects/{projectId}/agent/tasks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Agent task","runtimeType":"FAKE","tokenBudget":%d}
                                """.formatted(tokenBudget)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions send(String taskId, String content) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("content", content));
        return mockMvc.perform(post("/api/agent/tasks/{taskId}/messages", taskId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode awaitTask(String taskId, Predicate<JsonNode> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        JsonNode task;
        do {
            task = json(get("/api/agent/tasks/{taskId}", taskId));
            if (condition.test(task)) return task;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Agent Task did not reach the expected state: " + task);
    }

    private JsonNode json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        String response = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private List<String> eventTypes(JsonNode events) {
        java.util.ArrayList<String> types = new java.util.ArrayList<>();
        for (JsonNode event : events) types.add(event.path("eventType").asText());
        return types;
    }
}
