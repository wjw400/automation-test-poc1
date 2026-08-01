package com.example.poc.platform.agent.runtime;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.agent.AgentRuntimeResources;
import com.example.poc.platform.agent.capability.AgentTool;
import com.example.poc.platform.agent.capability.AgentToolResult;
import com.example.poc.platform.agent.scenario.ScenarioDraftService.ScenarioStepReference;
import com.example.poc.platform.persistence.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class FakeAgentRuntime implements AgentRuntime {
    private final AgentProperties properties;
    private final AgentRuntimeResources resources;
    private final JsonCodec jsonCodec;
    private final Map<String, Future<?>> active = new ConcurrentHashMap<>();

    public FakeAgentRuntime(
            AgentProperties properties,
            AgentRuntimeResources resources,
            JsonCodec jsonCodec) {
        this.properties = properties;
        this.resources = resources;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public String type() { return "FAKE"; }

    @Override
    public AgentRuntimeCapabilities capabilities() {
        return new AgentRuntimeCapabilities(true, true, true, false, "fake-agent-v1");
    }

    @Override
    public CompletableFuture<AgentTurnResult> startTurn(
            AgentTurnRequest request,
            Consumer<AgentRuntimeEvent> eventSink) {
        CompletableFuture<AgentTurnResult> result = new CompletableFuture<>();
        Future<?> worker = resources.executor().submit(() -> execute(request, eventSink, result));
        active.put(request.taskId(), worker);
        result.whenComplete((ignored, error) -> active.remove(request.taskId(), worker));
        return result;
    }

    private void execute(
            AgentTurnRequest request,
            Consumer<AgentRuntimeEvent> eventSink,
            CompletableFuture<AgentTurnResult> result) {
        try {
            String input = request.latestUserText();
            AgentToolCallBudget toolBudget = new AgentToolCallBudget(properties, jsonCodec);
            if (input.contains("[fail]")) {
                throw new AgentRuntimeException("FAKE_FAILURE", "Fake runtime failure requested", false);
            }
            List<AgentArtifactOutput> artifacts = new ArrayList<>();
            String response;
            if (input.contains("[secure-demo]")) {
                response = executeSecureDraftDemo(request, eventSink, toolBudget);
            } else if (input.contains("[draft-demo]")) {
                response = executeDraftDemo(request, eventSink, toolBudget);
            } else {
                response = "Fake agent response: " + input;
            }
            for (String chunk : chunks(response, 12)) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException("Agent turn cancelled");
                eventSink.accept(new AgentRuntimeEvent("message.delta", Map.of("delta", chunk)));
                Thread.sleep(properties.getFake().getChunkDelay().toMillis());
            }
            artifacts.add(new AgentArtifactOutput(
                    "RUNTIME_TRACE",
                    null,
                    null,
                    null,
                    "READY",
                    Map.of("runtime", type(), "input", input)));
            result.complete(new AgentTurnResult(response, input.length(), response.length(), artifacts));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            result.completeExceptionally(new CancellationException("Agent turn cancelled"));
        } catch (Throwable exception) {
            result.completeExceptionally(exception);
        }
    }

    private String executeDraftDemo(
            AgentTurnRequest request,
            Consumer<AgentRuntimeEvent> eventSink,
            AgentToolCallBudget budget) {
        if (request.tools().isEmpty()) {
            throw new AgentRuntimeException("FAKE_TOOLS_UNAVAILABLE", "Draft demo requires Scenario Tools", false);
        }
        AgentToolResult scenarioResult = invoke(request, "getScenario", Map.of(), eventSink, budget);
        String operationId = firstOperationId(scenarioResult);
        invoke(request, "getOperationSchema", Map.of("operationId", operationId), eventSink, budget);
        AgentToolResult knowledgeResult = invoke(
                request, "searchPublishedKnowledge", Map.of(), eventSink, budget);
        AgentToolResult created = invoke(request, "createScenarioDraft", Map.of(), eventSink, budget);
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) created.output().get("draft");
        @SuppressWarnings("unchecked")
        List<ScenarioStepReference> steps = (List<ScenarioStepReference>) draft.get("steps");
        if (steps.isEmpty()) throw new IllegalArgumentException("Draft demo requires at least one Scenario Step");

        String draftId = String.valueOf(draft.get("id"));
        long revision = ((Number) draft.get("revision")).longValue();
        String stepId = steps.get(0).id();
        List<Map<String, Object>> commands = List.of(
                Map.of(
                        "type", "SetRequestHeader",
                        "stepId", stepId,
                        "name", "X-Correlation-Id",
                        "value", "${correlationId}"),
                Map.of(
                        "type", "AddExtraction",
                        "stepId", stepId,
                        "name", "orderId",
                        "extraction", Map.of("source", "raw", "path", "$.orderId")));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("targetType", "SCENARIO_DRAFT");
        plan.put("targetId", draftId);
        plan.put("baseRevision", revision);
        plan.put("commands", commands);
        plan.put("assumptions", List.of("The first Scenario Step is the requested target"));
        plan.put("warnings", List.of());
        plan.put("citations", citations(knowledgeResult));
        invoke(request, "applyScenarioCommands", Map.of("draftId", draftId, "commandPlan", plan), eventSink, budget);
        invoke(request, "validateScenarioDraft", Map.of("draftId", draftId), eventSink, budget);
        invoke(request, "getScenarioDraftDiff", Map.of("draftId", draftId), eventSink, budget);
        return "Created and validated a Scenario Draft with X-Correlation-Id and orderId extraction.";
    }

    private String executeSecureDraftDemo(
            AgentTurnRequest request,
            Consumer<AgentRuntimeEvent> eventSink,
            AgentToolCallBudget budget) {
        if (request.tools().isEmpty()) {
            throw new AgentRuntimeException("FAKE_TOOLS_UNAVAILABLE", "Secure demo requires Scenario Tools", false);
        }
        AgentToolResult scenarioResult = invoke(request, "getScenario", Map.of(), eventSink, budget);
        String operationId = firstOperationId(scenarioResult);
        invoke(request, "getOperationSchema", Map.of("operationId", operationId), eventSink, budget);
        invoke(request, "getSupportedActions", Map.of(), eventSink, budget);
        AgentToolResult knowledgeResult = invoke(
                request, "searchPublishedKnowledge", Map.of(), eventSink, budget);
        AgentToolResult created = invoke(request, "createScenarioDraft", Map.of(), eventSink, budget);
        @SuppressWarnings("unchecked")
        Map<String, Object> draft = (Map<String, Object>) created.output().get("draft");
        @SuppressWarnings("unchecked")
        List<ScenarioStepReference> steps = (List<ScenarioStepReference>) draft.get("steps");
        if (steps.isEmpty()) throw new IllegalArgumentException("Secure demo requires at least one Scenario Step");

        String draftId = String.valueOf(draft.get("id"));
        long revision = ((Number) draft.get("revision")).longValue();
        String stepId = steps.get(0).id();
        List<Map<String, Object>> commands = List.of(
                actionCommand("AddBeforeAction", stepId, Map.of(
                        "type", "crypto.pgp.signAndEncrypt",
                        "name", "Protect request payload",
                        "input", Map.of("source", "request.body", "format", "json"),
                        "output", Map.of("target", "artifacts.encryptedPayload", "format", "text"))),
                actionCommand("AddBeforeAction", stepId, Map.of(
                        "type", "data.encodeBase64",
                        "name", "Encode protected payload",
                        "input", Map.of("source", "artifacts.encryptedPayload"),
                        "output", Map.of("target", "artifacts.encryptedPayloadBase64"))),
                actionCommand("AddBeforeAction", stepId, Map.of(
                        "type", "body.buildJson",
                        "name", "Build secure wire body",
                        "template", Map.of(
                                "meta", Map.of("channel", "platform-poc"),
                                "encryptedPayload", "${artifacts.encryptedPayloadBase64}"),
                        "output", Map.of("target", "request.body"))),
                actionCommand("AddAfterAction", stepId, Map.of(
                        "type", "crypto.pgp.decryptAndVerify",
                        "name", "Decrypt secure response payload",
                        "input", Map.of("source", "response.body", "path", "$.payload"),
                        "output", Map.of("target", "decrypted.securePayload", "format", "json"))),
                assertionCommand(stepId, "response.body", "$.code", "SUCCESS"),
                assertionCommand(stepId, "decrypted.securePayload", "$.status", "SHIPPED"));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("targetType", "SCENARIO_DRAFT");
        plan.put("targetId", draftId);
        plan.put("baseRevision", revision);
        plan.put("commands", commands);
        plan.put("assumptions", List.of("The published protocol stores the encrypted response at $.payload"));
        plan.put("warnings", List.of());
        plan.put("citations", citations(knowledgeResult));
        invoke(request, "applyScenarioCommands", Map.of("draftId", draftId, "commandPlan", plan), eventSink, budget);
        invoke(request, "validateScenarioDraft", Map.of("draftId", draftId), eventSink, budget);
        invoke(request, "getScenarioDraftDiff", Map.of("draftId", draftId), eventSink, budget);
        return "Created and validated a secure Scenario Draft without reading Profile secret material.";
    }

    private Map<String, Object> actionCommand(String type, String stepId, Map<String, Object> action) {
        return Map.of("type", type, "stepId", stepId, "action", action);
    }

    private Map<String, Object> assertionCommand(
            String stepId,
            String source,
            String path,
            Object expected) {
        return Map.of(
                "type", "AddAssertion",
                "stepId", stepId,
                "assertion", Map.of(
                        "source", source,
                        "path", path,
                        "operator", "equals",
                        "expected", expected));
    }

    @SuppressWarnings("unchecked")
    private String firstOperationId(AgentToolResult scenarioResult) {
        Map<String, Object> scenario = (Map<String, Object>) scenarioResult.output().get("scenario");
        Map<String, Object> dsl = (Map<String, Object>) scenario.get("dsl");
        List<Map<String, Object>> steps = (List<Map<String, Object>>) dsl.get("steps");
        Map<String, Object> operation = (Map<String, Object>) steps.get(0).get("operation");
        return String.valueOf(operation.get("id"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> citations(AgentToolResult knowledgeResult) {
        List<Map<String, Object>> matches = (List<Map<String, Object>>) knowledgeResult.output().get("matches");
        if (matches == null || matches.isEmpty()) return List.of();
        Map<String, Object> source = matches.get(0);
        Map<String, Object> citation = new LinkedHashMap<>();
        citation.put("knowledgeId", source.get("knowledgeId"));
        citation.put("scope", source.get("scope"));
        citation.put("targetId", source.get("targetId"));
        return List.of(citation);
    }

    private AgentToolResult invoke(
            AgentTurnRequest request,
            String name,
            Map<String, Object> input,
            Consumer<AgentRuntimeEvent> eventSink,
            AgentToolCallBudget budget) {
        AgentTool tool = request.tools().stream()
                .filter(candidate -> name.equals(candidate.definition().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Fake runtime Tool not found: " + name));
        AgentToolCallBudget.Invocation permit = budget.begin(name, input);
        eventSink.accept(new AgentRuntimeEvent("tool.started", Map.of(
                "toolName", name,
                "category", tool.definition().category(),
                "sideEffect", tool.definition().sideEffect(),
                "callSequence", permit.sequence(),
                "inputFingerprint", permit.inputFingerprint())));
        try {
            AgentToolResult result = tool.execute(input);
            budget.succeeded(permit, result.output(), result.artifacts().size());
            result.artifacts().forEach(artifact -> eventSink.accept(AgentRuntimeEvent.artifact(artifact)));
            eventSink.accept(new AgentRuntimeEvent("tool.completed", Map.of(
                    "toolName", name,
                    "artifactCount", result.artifacts().size())));
            return result;
        } catch (RuntimeException exception) {
            if (!(exception instanceof AgentRuntimeException)) budget.failed();
            eventSink.accept(new AgentRuntimeEvent("tool.failed", Map.of(
                    "toolName", name,
                    "code", exception instanceof SecurityException ? "TOOL_PERMISSION_DENIED" : "TOOL_EXECUTION_FAILED")));
            throw exception;
        }
    }

    private List<String> chunks(String value, int size) {
        java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
        for (int start = 0; start < value.length(); start += size) {
            chunks.add(value.substring(start, Math.min(value.length(), start + size)));
        }
        return chunks;
    }

    @Override
    public boolean cancel(String taskId) {
        Future<?> worker = active.remove(taskId);
        return worker != null && worker.cancel(true);
    }
}
