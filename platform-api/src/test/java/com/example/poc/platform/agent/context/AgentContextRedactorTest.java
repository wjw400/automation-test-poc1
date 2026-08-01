package com.example.poc.platform.agent.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentContextRedactorTest {
    private final AgentContextRedactor redactor = new AgentContextRedactor();

    @Test
    void redactsStructuredAndEmbeddedSecrets() {
        Object result = redactor.redact(Map.of(
                "Authorization", "Bearer abc.def",
                "body", "password=hunter2&name=demo\n<token>xml-secret</token>",
                "privateKey", "never-visible"));

        assertThat(result.toString())
                .contains("[REDACTED]")
                .doesNotContain("abc.def")
                .doesNotContain("hunter2")
                .doesNotContain("xml-secret")
                .doesNotContain("never-visible");
    }

    @Test
    void removesPgpPrivateKeyBlocksFromFreeText() {
        String result = redactor.redactText("""
                public note
                -----BEGIN PGP PRIVATE KEY BLOCK-----
                secret-material
                -----END PGP PRIVATE KEY BLOCK-----
                """);

        assertThat(result).contains("public note").contains("[REDACTED PRIVATE KEY]")
                .doesNotContain("secret-material");
    }

    @Test
    void keepsNonSecretTokenUsageMetrics() {
        Object result = redactor.redact(Map.of(
                "promptTokens", 120,
                "completionTokens", 45,
                "accessToken", "never-visible"));

        assertThat(result.toString())
                .contains("promptTokens=120")
                .contains("completionTokens=45")
                .doesNotContain("never-visible");
    }
}
