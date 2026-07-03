package com.example.poc.mockservice.service;

import com.example.poc.mockservice.config.CryptoProfileProperties;
import com.example.poc.mockservice.config.CryptoProfilesProperties;
import com.example.poc.pgp.PgpCryptoService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class SecureMockService {
    private final CryptoProfilesProperties cryptoProfilesProperties;
    private final PgpCryptoService pgpCryptoService = new PgpCryptoService();
    private final AtomicInteger sequence = new AtomicInteger(1000);
    private final Map<String, Map<String, Object>> entities = new ConcurrentHashMap<>();

    public SecureMockService(CryptoProfilesProperties cryptoProfilesProperties) {
        this.cryptoProfilesProperties = cryptoProfilesProperties;
    }

    public Map<String, Object> handleRaw(String profileName, String cipherText) {
        CryptoProfileProperties profile = resolveProfile(profileName);
        Object plaintext = decryptToObject(cipherText, profile);
        Map<String, Object> payload = asMap(plaintext);

        Map<String, Object> encryptedPayload = Map.of(
                "orderId", "O" + sequence.incrementAndGet(),
                "amount", payload.getOrDefault("amount", 0),
                "status", "CREATED");

        return baseResponse(profileName, Map.of(
                "payload", encryptObject(encryptedPayload, profile),
                "receivedRequest", payload));
    }

    public Map<String, Object> handleJsonField(String profileName, String field, Map<String, Object> request) {
        CryptoProfileProperties profile = resolveProfile(profileName);
        Object encryptedField = request.get(field);
        if (!(encryptedField instanceof String cipherText)) {
            throw new IllegalArgumentException("Field " + field + " must contain an armored PGP string");
        }

        Map<String, Object> decryptedRequest = asMap(decryptToObject(cipherText, profile));
        Map<String, Object> securePayload = Map.of(
                "result", "ACCEPTED",
                "customerId", decryptedRequest.get("customerId"),
                "fieldEcho", field);

        Map<String, Object> response = baseResponse(profileName, Map.of(
                "fieldUsed", field,
                "payload", encryptObject(securePayload, profile)));
        response.put("traceId", request.getOrDefault("traceId", response.get("traceId")));
        return response;
    }

    public Map<String, Object> createEntity(String profileName, Map<String, Object> request) {
        CryptoProfileProperties profile = resolveProfile(profileName);
        Map<String, Object> decryptedRequest = decryptPayloadField(request, profile);
        String entityId = "E" + sequence.incrementAndGet();

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityId", entityId);
        entity.put("name", decryptedRequest.getOrDefault("name", "unknown"));
        entity.put("status", "CREATED");
        entities.put(entityId, entity);

        return baseResponse(profileName, Map.of(
                "payload", encryptObject(entity, profile)));
    }

    public Map<String, Object> queryEntity(String profileName, Map<String, Object> request) {
        CryptoProfileProperties profile = resolveProfile(profileName);
        Map<String, Object> decryptedRequest = decryptPayloadField(request, profile);
        String entityId = String.valueOf(decryptedRequest.get("entityId"));
        Map<String, Object> entity = entities.get(entityId);

        if (entity == null) {
            return errorResponse(profileName, "NOT_FOUND", "Entity not found", null);
        }

        Map<String, Object> queried = new LinkedHashMap<>(entity);
        queried.put("status", "ACTIVE");
        return baseResponse(profileName, Map.of(
                "payload", encryptObject(queried, profile)));
    }

    public Map<String, Object> errorCase(String profileName, String mode) {
        CryptoProfileProperties profile = resolveProfile(profileName);
        if ("bad-payload".equalsIgnoreCase(mode)) {
            return baseResponse(profileName, Map.of("payload", "BROKEN_ENCRYPTED_DATA"));
        }
        if ("wrong-field".equalsIgnoreCase(mode)) {
            return baseResponse(profileName, Map.of(
                    "wrongPayloadField", encryptObject(Map.of("status", "CREATED"), profile)));
        }
        if ("decrypt-fail".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("Simulated decrypt failure");
        }
        if ("bad-signature".equalsIgnoreCase(mode)) {
            return errorResponse(profileName, "BAD_SIGNATURE", "Signature verification failed", null);
        }
        return errorResponse(profileName, "UNKNOWN_MODE", "Unsupported error mode", null);
    }

    private Map<String, Object> decryptPayloadField(Map<String, Object> request, CryptoProfileProperties profile) {
        Object encryptedField = request.get("payload");
        if (!(encryptedField instanceof String cipherText)) {
            throw new IllegalArgumentException("payload must contain an armored PGP string");
        }
        return asMap(decryptToObject(cipherText, profile));
    }

    private CryptoProfileProperties resolveProfile(String requestedProfile) {
        String profileName = requestedProfile == null || requestedProfile.isBlank()
                ? cryptoProfilesProperties.getDefaultProfile()
                : requestedProfile;
        CryptoProfileProperties profile = cryptoProfilesProperties.getProfiles().get(profileName);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown crypto profile: " + profileName);
        }
        return profile;
    }

    private Map<String, Object> baseResponse(String profileName, Map<String, Object> additions) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "SUCCESS");
        response.put("message", "ok");
        response.put("traceId", "T" + sequence.incrementAndGet());
        response.put("profileUsed", normalizeProfileName(profileName));
        response.putAll(additions);
        return response;
    }

    private Map<String, Object> errorResponse(String profileName, String code, String message, String payload) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", code);
        response.put("message", message);
        response.put("traceId", "T" + sequence.incrementAndGet());
        response.put("profileUsed", normalizeProfileName(profileName));
        if (payload != null) {
            response.put("payload", payload);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected decrypted payload to be a JSON object");
    }

    private String normalizeProfileName(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return cryptoProfilesProperties.getDefaultProfile();
        }
        return profileName;
    }

    private Object decryptToObject(String cipherText, CryptoProfileProperties profile) {
        String plaintext = pgpCryptoService.decryptAndVerify(cipherText, profile.toPgpProfileMaterial());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(plaintext, Object.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse decrypted JSON payload", e);
        }
    }

    private String encryptObject(Object payload, CryptoProfileProperties profile) {
        try {
            String plaintext = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            return pgpCryptoService.signAndEncrypt(plaintext, profile.toPgpProfileMaterial());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize encrypted response payload", e);
        }
    }
}
