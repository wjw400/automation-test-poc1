package com.example.poc.platform.agent.runtime;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.persistence.JsonCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

final class AgentToolCallBudget {
    private final int maxCalls;
    private final int maxRepeatedCalls;
    private final int maxNoProgressCalls;
    private final JsonCodec jsonCodec;
    private final Map<String, Integer> signatureCounts = new HashMap<>();
    private int calls;
    private int noProgressCalls;
    private String previousOutcomeFingerprint;

    AgentToolCallBudget(AgentProperties properties, JsonCodec jsonCodec) {
        this.maxCalls = properties.getMaxToolCalls();
        this.maxRepeatedCalls = properties.getMaxRepeatedToolCalls();
        this.maxNoProgressCalls = properties.getMaxNoProgressToolCalls();
        this.jsonCodec = jsonCodec;
    }

    synchronized Invocation begin(String toolName, Map<String, Object> input) {
        calls++;
        if (calls > maxCalls) {
            throw exceeded("TOOL_CALL_BUDGET_EXCEEDED", "Agent reached its Tool call limit");
        }
        String inputFingerprint = fingerprint(jsonCodec.write(input == null ? Map.of() : input));
        String signature = toolName + ":" + inputFingerprint;
        int repeated = signatureCounts.merge(signature, 1, Integer::sum);
        if (repeated > maxRepeatedCalls) {
            throw exceeded("REPEATED_TOOL_CALL", "Agent repeated the same Tool call without progress");
        }
        return new Invocation(calls, toolName, signature, inputFingerprint);
    }

    synchronized void succeeded(Invocation invocation, Map<String, Object> output, int artifactCount) {
        String outputFingerprint = fingerprint(jsonCodec.write(output == null ? Map.of() : output));
        String outcomeFingerprint = "SUCCESS:" + invocation.signature() + ":" + outputFingerprint;
        recordOutcome(outcomeFingerprint, artifactCount > 0, "Agent Tool calls are no longer making progress");
    }

    synchronized void failed(Invocation invocation, String errorCode) {
        recordOutcome(
                "FAILED:" + invocation.toolName() + ":" + errorCode,
                false,
                "Agent Tool calls repeatedly failed without progress");
    }

    private void recordOutcome(String outcomeFingerprint, boolean explicitProgress, String message) {
        boolean repeatedOutcome = outcomeFingerprint.equals(previousOutcomeFingerprint);
        noProgressCalls = explicitProgress || !repeatedOutcome ? 0 : noProgressCalls + 1;
        previousOutcomeFingerprint = outcomeFingerprint;
        if (noProgressCalls >= maxNoProgressCalls) {
            throw exceeded("NO_PROGRESS", message);
        }
    }

    private AgentRuntimeException exceeded(String code, String message) {
        return new AgentRuntimeException(code, message, false);
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record Invocation(int sequence, String toolName, String signature, String inputFingerprint) {}
}
