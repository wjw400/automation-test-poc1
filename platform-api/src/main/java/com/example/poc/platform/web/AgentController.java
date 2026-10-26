package com.example.poc.platform.web;

import com.example.poc.platform.agent.AgentEventStreamService;
import com.example.poc.platform.agent.AgentAuditService;
import com.example.poc.platform.agent.AgentPermissionMode;
import com.example.poc.platform.agent.AgentAuditService.AgentAuditRecord;
import com.example.poc.platform.agent.AgentAuditService.AgentAuditSummary;
import com.example.poc.platform.agent.AgentRequestIdentity;
import com.example.poc.platform.agent.AgentTaskService;
import com.example.poc.platform.agent.AgentTaskService.AgentArtifactRecord;
import com.example.poc.platform.agent.AgentTaskService.AgentEventRecord;
import com.example.poc.platform.agent.AgentTaskService.AgentMessageRecord;
import com.example.poc.platform.agent.AgentTaskService.AgentTaskRecord;
import com.example.poc.platform.agent.AgentTaskService.CreateAgentTaskRequest;
import com.example.poc.platform.agent.AgentTurnCoordinator;
import com.example.poc.platform.agent.runtime.AgentRuntimeRegistry;
import com.example.poc.platform.agent.capability.AgentToolDefinition;
import com.example.poc.platform.agent.capability.AgentToolRegistry;
import com.example.poc.platform.agent.skill.AgentSkill;
import com.example.poc.platform.agent.skill.AgentSkillRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class AgentController {
    private final AgentTaskService taskService;
    private final AgentTurnCoordinator turnCoordinator;
    private final AgentEventStreamService eventStreamService;
    private final AgentRuntimeRegistry runtimeRegistry;
    private final AgentSkillRegistry skillRegistry;
    private final AgentToolRegistry toolRegistry;
    private final AgentRequestIdentity requestIdentity;
    private final AgentAuditService auditService;

    public AgentController(
            AgentTaskService taskService,
            AgentTurnCoordinator turnCoordinator,
            AgentEventStreamService eventStreamService,
            AgentRuntimeRegistry runtimeRegistry,
            AgentSkillRegistry skillRegistry,
            AgentToolRegistry toolRegistry,
            AgentRequestIdentity requestIdentity,
            AgentAuditService auditService) {
        this.taskService = taskService;
        this.turnCoordinator = turnCoordinator;
        this.eventStreamService = eventStreamService;
        this.runtimeRegistry = runtimeRegistry;
        this.skillRegistry = skillRegistry;
        this.toolRegistry = toolRegistry;
        this.requestIdentity = requestIdentity;
        this.auditService = auditService;
    }

    @GetMapping("/agent/runtimes")
    public List<AgentRuntimeRegistry.RuntimeDescriptor> runtimes() { return runtimeRegistry.list(); }

    @GetMapping("/agent/skills")
    public List<AgentSkillDescriptor> skills() {
        return skillRegistry.list().stream().map(AgentSkillDescriptor::from).toList();
    }

    @GetMapping("/agent/tools")
    public List<AgentToolDefinition> tools() { return toolRegistry.definitions(); }

    @PostMapping("/projects/{projectId}/agent/tasks")
    public AgentTaskRecord create(
            @PathVariable("projectId") String projectId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @Valid @RequestBody CreateAgentTaskRequest request) {
        return taskService.create(projectId, requestIdentity.resolve(actorHeader), request);
    }

    @GetMapping("/projects/{projectId}/agent/tasks")
    public List<AgentTaskRecord> list(
            @PathVariable("projectId") String projectId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return taskService.list(projectId, requestIdentity.resolve(actorHeader));
    }

    @GetMapping("/agent/tasks/{taskId}")
    public AgentTaskRecord get(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return taskService.get(taskId, requestIdentity.resolve(actorHeader));
    }

    @PatchMapping("/agent/tasks/{taskId}")
    public AgentTaskRecord rename(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @Valid @RequestBody RenameAgentTaskRequest request) {
        return taskService.rename(taskId, requestIdentity.resolve(actorHeader), request.title());
    }

    @PatchMapping("/agent/tasks/{taskId}/permission-mode")
    public AgentTaskRecord changePermissionMode(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @Valid @RequestBody ChangeAgentPermissionModeRequest request) {
        return taskService.changePermissionMode(
                taskId,
                requestIdentity.resolve(actorHeader),
                request.permissionMode());
    }

    @PatchMapping("/agent/tasks/{taskId}/model")
    public AgentTaskRecord changeModel(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @Valid @RequestBody ChangeAgentModelRequest request) {
        return taskService.changeModel(taskId, requestIdentity.resolve(actorHeader), request.modelId());
    }

    @DeleteMapping("/agent/tasks/{taskId}")
    public ResponseEntity<Void> delete(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        taskService.delete(taskId, requestIdentity.resolve(actorHeader));
        eventStreamService.close(taskId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/agent/tasks/{taskId}/messages")
    public List<AgentMessageRecord> messages(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return taskService.messages(taskId, requestIdentity.resolve(actorHeader));
    }

    @PostMapping("/agent/tasks/{taskId}/messages")
    public ResponseEntity<AgentTaskRecord> send(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @Valid @RequestBody SendAgentMessageRequest request) {
        return ResponseEntity.accepted().body(turnCoordinator.submit(
                taskId, requestIdentity.resolve(actorHeader), request.content().trim()));
    }

    @GetMapping(value = "/agent/tasks/{taskId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AgentEventRecord> events(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @RequestParam(name = "afterSequence", defaultValue = "0") long afterSequence) {
        return taskService.events(taskId, requestIdentity.resolve(actorHeader), Math.max(0, afterSequence));
    }

    @GetMapping(value = "/agent/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId,
            @RequestParam(name = "afterSequence", defaultValue = "0") long afterSequence) {
        return eventStreamService.subscribe(
                taskId,
                requestIdentity.resolve(actorHeader),
                Math.max(Math.max(0, afterSequence), parseSequence(lastEventId)));
    }

    @PostMapping("/agent/tasks/{taskId}/cancel")
    public AgentTaskRecord cancel(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return turnCoordinator.cancel(taskId, requestIdentity.resolve(actorHeader));
    }

    @PostMapping("/agent/tasks/{taskId}/continuation-decision")
    public ResponseEntity<AgentTaskRecord> continuationDecision(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @Valid @RequestBody ContinuationDecisionRequest request) {
        String actorId = requestIdentity.resolve(actorHeader);
        if ("CONTINUE".equalsIgnoreCase(request.decision())) {
            return ResponseEntity.accepted().body(turnCoordinator.continueTask(taskId, actorId));
        }
        if ("STOP".equalsIgnoreCase(request.decision())) {
            return ResponseEntity.ok(taskService.declineContinuation(taskId, actorId));
        }
        throw new IllegalArgumentException("Unsupported continuation decision: " + request.decision());
    }

    @GetMapping("/agent/tasks/{taskId}/artifacts")
    public List<AgentArtifactRecord> artifacts(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return taskService.artifacts(taskId, requestIdentity.resolve(actorHeader));
    }

    @PostMapping("/agent/tasks/{taskId}/decisions")
    public ResponseEntity<Void> decision(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @Valid @RequestBody AgentDecisionRequest request) {
        taskService.recordDecision(
                taskId, requestIdentity.resolve(actorHeader), request.decision(), request.details());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/agent/tasks/{taskId}/audit")
    public List<AgentAuditRecord> audit(
            @PathVariable("taskId") String taskId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return auditService.list(taskId, requestIdentity.resolve(actorHeader));
    }

    @GetMapping("/projects/{projectId}/agent/audit/summary")
    public AgentAuditSummary auditSummary(
            @PathVariable("projectId") String projectId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return auditService.summary(projectId, requestIdentity.resolve(actorHeader));
    }

    private long parseSequence(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Last-Event-ID must be a non-negative integer");
        }
    }

    public record SendAgentMessageRequest(@NotBlank String content) {}

    public record RenameAgentTaskRequest(@NotBlank String title) {}

    public record ChangeAgentPermissionModeRequest(@NotNull AgentPermissionMode permissionMode) {}

    public record ChangeAgentModelRequest(@NotBlank String modelId) {}

    public record AgentDecisionRequest(
            @NotBlank String decision,
            Map<String, Object> details) {}

    public record ContinuationDecisionRequest(@NotBlank String decision) {}

    public record AgentSkillDescriptor(
            String name, String description, String version, String riskLevel,
            List<String> requiredTools, boolean enabled, String source) {
        private static AgentSkillDescriptor from(AgentSkill skill) {
            return new AgentSkillDescriptor(
                    skill.name(), skill.description(), skill.version(), skill.riskLevel(),
                    skill.requiredTools(), skill.enabled(), skill.source());
        }
    }
}
