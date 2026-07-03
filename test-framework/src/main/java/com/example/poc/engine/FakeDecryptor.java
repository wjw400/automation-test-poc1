package com.example.poc.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class FakeDecryptor {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Object decrypt(String cipherText, String plaintextFormat, CryptoProfile profile) {
        if (cipherText == null) {
            throw new IllegalArgumentException("Ciphertext is null");
        }
        if (profile == null) {
            throw new IllegalArgumentException("Crypto profile is null");
        }
        String trimmed = cipherText.trim();
        String prefix = "ENCRYPTED(";
        if (!trimmed.startsWith(prefix) || !trimmed.endsWith(")")) {
            throw new IllegalArgumentException("Unsupported fake encrypted payload format");
        }
        String plaintext = trimmed.substring(prefix.length(), trimmed.length() - 1);
        if ("json".equalsIgnoreCase(plaintextFormat)) {
            try {
                return objectMapper.readValue(plaintext, Object.class);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to parse decrypted JSON payload", e);
            }
        }
        return plaintext;
    }
}
