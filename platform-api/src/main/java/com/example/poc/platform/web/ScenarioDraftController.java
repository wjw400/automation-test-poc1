package com.example.poc.platform.web;

import com.example.poc.platform.agent.AgentRequestIdentity;
import com.example.poc.platform.agent.scenario.ScenarioDraftDiff;
import com.example.poc.platform.agent.scenario.ScenarioDraftService;
import com.example.poc.platform.agent.scenario.ScenarioDraftService.CommandExecutionResult;
import com.example.poc.platform.agent.scenario.ScenarioDraftService.CreateScenarioDraftRequest;
import com.example.poc.platform.agent.scenario.ScenarioDraftService.ScenarioDraftRecord;
import com.example.poc.platform.agent.scenario.ScenarioValidationResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ScenarioDraftController {
    private final ScenarioDraftService draftService;
    private final AgentRequestIdentity requestIdentity;

    public ScenarioDraftController(ScenarioDraftService draftService, AgentRequestIdentity requestIdentity) {
        this.draftService = draftService;
        this.requestIdentity = requestIdentity;
    }

    @PostMapping("/projects/{projectId}/scenario-drafts")
    public ScenarioDraftRecord create(
            @PathVariable("projectId") String projectId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @Valid @RequestBody CreateScenarioDraftRequest request) {
        return draftService.create(projectId, request, requestIdentity.resolve(actorHeader), null);
    }

    @GetMapping("/projects/{projectId}/scenario-drafts")
    public List<ScenarioDraftRecord> list(
            @PathVariable("projectId") String projectId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return draftService.list(projectId, requestIdentity.resolve(actorHeader));
    }

    @GetMapping("/scenario-drafts/{draftId}")
    public ScenarioDraftRecord get(
            @PathVariable("draftId") String draftId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return draftService.get(draftId, requestIdentity.resolve(actorHeader));
    }

    @PostMapping("/scenario-drafts/{draftId}/commands")
    public CommandExecutionResult applyCommands(
            @PathVariable("draftId") String draftId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader,
            @RequestBody Map<String, Object> commandPlan) {
        return draftService.applyCommands(draftId, requestIdentity.resolve(actorHeader), commandPlan);
    }

    @PostMapping("/scenario-drafts/{draftId}/validate")
    public ScenarioValidationResult validate(
            @PathVariable("draftId") String draftId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return draftService.validate(draftId, requestIdentity.resolve(actorHeader));
    }

    @GetMapping("/scenario-drafts/{draftId}/diff")
    public ScenarioDraftDiff diff(
            @PathVariable("draftId") String draftId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        return draftService.diff(draftId, requestIdentity.resolve(actorHeader));
    }

    @DeleteMapping("/scenario-drafts/{draftId}")
    public ResponseEntity<Void> discard(
            @PathVariable("draftId") String draftId,
            @RequestHeader(name = AgentRequestIdentity.HEADER_NAME, required = false) String actorHeader) {
        draftService.discard(draftId, requestIdentity.resolve(actorHeader));
        return ResponseEntity.noContent().build();
    }
}
