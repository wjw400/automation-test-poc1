package com.example.poc.platform.agent.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AgentContextRedactor {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "secret", "token", "authorization", "apikey", "api_key",
            "privatekey", "private_key", "passphrase", "credential", "credentials",
            "cookie", "set_cookie", "clientsecret", "client_secret", "datasource_password");
    private static final Pattern PRIVATE_KEY_BLOCK = Pattern.compile(
            "(?is)-----BEGIN (?:PGP )?PRIVATE KEY BLOCK-----.*?-----END (?:PGP )?PRIVATE KEY BLOCK-----");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*)(?:bearer|basic)\\s+[^\\s,;]+" );
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passphrase|api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret)"
                    + "(\\s*[=:]\\s*|\\\"\\s*:\\s*\\\")([^\\s,;&\\\"}]+)");
    private static final Pattern XML_SECRET = Pattern.compile(
            "(?is)<(password|passphrase|token|secret|privateKey)\\b[^>]*>.*?</\\1>");
    private static final Pattern COOKIE = Pattern.compile("(?i)((?:set-)?cookie\\s*[:=]\\s*)[^\\r\\n]+");

    public Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String name = String.valueOf(key);
                result.put(name, isSensitive(name) ? "[REDACTED]" : redact(item));
            });
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(item -> result.add(redact(item)));
            return result;
        }
        if (value instanceof String text) return redactText(text);
        return value;
    }

    public String redactText(String value) {
        if (value == null || value.isEmpty()) return value;
        String redacted = PRIVATE_KEY_BLOCK.matcher(value).replaceAll("[REDACTED PRIVATE KEY]");
        redacted = AUTHORIZATION.matcher(redacted).replaceAll("$1[REDACTED]");
        redacted = SECRET_ASSIGNMENT.matcher(redacted).replaceAll("$1$2[REDACTED]");
        redacted = XML_SECRET.matcher(redacted).replaceAll("<$1>[REDACTED]</$1>");
        return COOKIE.matcher(redacted).replaceAll("$1[REDACTED]");
    }

    private boolean isSensitive(String key) {
        String normalized = key.replace("-", "_").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.endsWith("password")
                || normalized.endsWith("passphrase")
                || normalized.endsWith("privatekey")
                || normalized.endsWith("private_key")
                || normalized.endsWith("apikey")
                || normalized.endsWith("api_key")
                || normalized.endsWith("accesstoken")
                || normalized.endsWith("access_token")
                || normalized.endsWith("refreshtoken")
                || normalized.endsWith("refresh_token")
                || normalized.endsWith("clientsecret")
                || normalized.endsWith("client_secret");
    }
}
