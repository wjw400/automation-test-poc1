package com.example.poc.engine;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableResolver {
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern EXACT_TOKEN = Pattern.compile("^\\$\\{([^}]+)}$");

    public Object resolveObject(Object value, TestContext context) {
        return resolveObject(value, context, Collections.emptyMap());
    }

    public Object resolveObject(Object value, TestContext context, Map<String, Object> artifacts) {
        if (value instanceof String text) {
            Matcher exactMatcher = EXACT_TOKEN.matcher(text);
            if (exactMatcher.matches()) {
                return resolveToken(exactMatcher.group(1), context, artifacts);
            }
            return resolveString(text, context, artifacts);
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            entry -> resolveObject(entry.getValue(), context, artifacts),
                            (left, right) -> right,
                            java.util.LinkedHashMap::new));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> resolveObject(item, context, artifacts)).toList();
        }
        return value;
    }

    public String resolveString(String value, TestContext context) {
        return resolveString(value, context, Collections.emptyMap());
    }

    public String resolveString(String value, TestContext context, Map<String, Object> artifacts) {
        Matcher matcher = TOKEN.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            Object replacement = resolveToken(matcher.group(1), context, artifacts);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(replacement)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private Object resolveToken(String token, TestContext context, Map<String, Object> artifacts) {
        if ("random.uuid".equals(token)) {
            return UUID.randomUUID();
        }
        if ("random.email".equals(token)) {
            return "test_" + UUID.randomUUID() + "@example.com";
        }
        if ("time.now".equals(token)) {
            return Instant.now().toString();
        }
        if (token.startsWith("env.")) {
            return System.getenv(token.substring("env.".length()));
        }
        if (token.startsWith("artifacts.")) {
            String artifactName = token.substring("artifacts.".length());
            if (!artifacts.containsKey(artifactName)) {
                throw new IllegalArgumentException("Unknown artifact: ${" + token + "}");
            }
            return artifacts.get(artifactName);
        }
        String variableName = token.startsWith("variables.")
                ? token.substring("variables.".length())
                : token;
        return context.get(variableName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown variable: ${" + token + "}"));
    }
}
