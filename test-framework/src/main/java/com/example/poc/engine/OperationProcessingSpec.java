package com.example.poc.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Declarative, versioned operation configuration. Contains no keys or executable code. */
public class OperationProcessingSpec {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) { throw new IllegalArgumentException("Unsupported processing property: " + name); }
    private int version = 1;
    private MessageRule request = new MessageRule();
    private Map<String, MessageRule> responses = new LinkedHashMap<>();

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public MessageRule getRequest() { return request; }
    public void setRequest(MessageRule request) { this.request = request == null ? new MessageRule() : request; }
    public Map<String, MessageRule> getResponses() { return responses; }
    public void setResponses(Map<String, MessageRule> responses) { this.responses = responses == null ? new LinkedHashMap<>() : responses; }

    public boolean requiresProfile() {
        return !"none".equals(request.getProtection()) || responses.values().stream().anyMatch(rule -> !"none".equals(rule.getProtection()));
    }

    public MessageRule responseRule(int status) {
        MessageRule rule = responses.get(String.valueOf(status));
        if (rule == null) rule = responses.get((status / 100) + "XX");
        if (rule == null) rule = responses.get("default");
        if (rule == null) throw new IllegalArgumentException("No response processing rule configured for HTTP " + status);
        return rule;
    }

    public void validate() {
        if (version != 1) throw new IllegalArgumentException("Unsupported processing configuration version: " + version);
        validateRule(request, true);
        responses.forEach((status, rule) -> {
            if (!status.matches("[1-5][0-9]{2}|[1-5]XX|default")) throw new IllegalArgumentException("Invalid response processing status: " + status);
            validateRule(rule, false);
        });
        if (requiresProfile() && responses.isEmpty()) throw new IllegalArgumentException("Configure response handling explicitly, including plain JSON responses");
    }

    private void validateRule(MessageRule rule, boolean requestDirection) {
        if (rule == null) throw new IllegalArgumentException("Processing rule must be an object");
        Set<String> allowed = requestDirection ? Set.of("none", "pgp-encrypt", "pgp-sign-encrypt")
                : Set.of("none", "pgp-decrypt", "pgp-decrypt-verify");
        if (rule.getProtection() == null || !allowed.contains(rule.getProtection())) throw new IllegalArgumentException("Unsupported " + (requestDirection ? "request" : "response") + " protection: " + rule.getProtection());
        if ("none".equals(rule.getProtection())) return;
        if (rule.getCiphertext().getLocation() == null || !Set.of("body", "json-pointer").contains(rule.getCiphertext().getLocation())) throw new IllegalArgumentException("Ciphertext location must be body or json-pointer");
        if ("json-pointer".equals(rule.getCiphertext().getLocation())) {
            JsonBodyPointer.tokens(rule.getCiphertext().getPointer());
            if (requestDirection) {
                Object seed = copy(rule.getEnvelope());
                JsonBodyPointer.write(seed, rule.getCiphertext().getPointer(), "<ciphertext>");
            }
        }
    }

    public static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> result.put(String.valueOf(key), copy(child)));
            return result;
        }
        if (value instanceof java.util.List<?> list) {
            return list.stream().map(OperationProcessingSpec::copy).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        }
        return value;
    }

    public static class MessageRule {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) { throw new IllegalArgumentException("Unsupported processing rule property: " + name); }
        private String protection = "none";
        private CiphertextLocation ciphertext = new CiphertextLocation();
        private Object envelope = new LinkedHashMap<String, Object>();
        private Object businessSchema;
        public String getProtection() { return protection; }
        public void setProtection(String protection) { this.protection = protection; }
        public CiphertextLocation getCiphertext() { return ciphertext; }
        public void setCiphertext(CiphertextLocation ciphertext) { this.ciphertext = ciphertext == null ? new CiphertextLocation() : ciphertext; }
        public Object getEnvelope() { return envelope; }
        public void setEnvelope(Object envelope) { this.envelope = envelope; }
        public Object getBusinessSchema() { return businessSchema; }
        public void setBusinessSchema(Object businessSchema) { this.businessSchema = businessSchema; }
    }

    public static class CiphertextLocation {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknownProperty(String name, Object value) { throw new IllegalArgumentException("Unsupported ciphertext property: " + name); }
        private String location = "json-pointer";
        private String pointer;
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getPointer() { return pointer; }
        public void setPointer(String pointer) { this.pointer = pointer; }
    }
}
