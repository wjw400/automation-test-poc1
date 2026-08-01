package com.example.poc.platform.agent;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AgentRequestIdentity {
    public static final String HEADER_NAME = "X-Platform-User";
    public static final String LOCAL_USER = "local-user";
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9._@-]{1,100}");

    public String resolve(String value) {
        String actorId = value == null || value.isBlank() ? LOCAL_USER : value.trim();
        if (!VALID_ID.matcher(actorId).matches()) {
            throw new IllegalArgumentException("X-Platform-User contains unsupported characters");
        }
        return actorId;
    }
}
