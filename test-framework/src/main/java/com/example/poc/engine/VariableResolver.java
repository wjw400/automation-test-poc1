package com.example.poc.engine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableResolver {
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^}]+)}");

    public Object resolveObject(Object value, TestContext context) {
        if (value instanceof String text) {
            return resolveString(text, context);
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            entry -> resolveObject(entry.getValue(), context),
                            (left, right) -> right,
                            java.util.LinkedHashMap::new));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> resolveObject(item, context)).toList();
        }
        return value;
    }

    public String resolveString(String value, TestContext context) {
        Matcher matcher = TOKEN.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            Object replacement = resolveToken(matcher.group(1), context);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(replacement)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private Object resolveToken(String token, TestContext context) {
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
        return context.get(token).orElseThrow(() -> new IllegalArgumentException("Unknown variable: ${" + token + "}"));
    }
}
