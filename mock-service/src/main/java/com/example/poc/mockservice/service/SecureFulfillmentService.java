package com.example.poc.mockservice.service;

import com.example.poc.mockservice.activity.ApiActivityService;
import com.example.poc.pgp.PgpCryptoService;
import com.example.poc.pgp.PgpProfileMaterial;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class SecureFulfillmentService {
    private final FulfillmentService fulfillmentService;
    private final PartnerKeyService partnerKeyService;
    private final ApiActivityService activityService;
    private final ObjectMapper objectMapper;
    private final PgpCryptoService pgpCryptoService = new PgpCryptoService();
    private final AtomicInteger sequence = new AtomicInteger(1000);

    public SecureFulfillmentService(
            FulfillmentService fulfillmentService,
            PartnerKeyService partnerKeyService,
            ApiActivityService activityService,
            ObjectMapper objectMapper) {
        this.fulfillmentService = fulfillmentService;
        this.partnerKeyService = partnerKeyService;
        this.activityService = activityService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> createFulfillmentOrder(String profileName, Map<String, Object> request) {
        return executeFulfillment(
                profileName,
                "Create Order",
                "/secure/fulfillment/orders/create",
                request,
                fulfillmentService::createOrder);
    }

    public Map<String, Object> queryFulfillmentOrder(String profileName, Map<String, Object> request) {
        return executeFulfillment(
                profileName,
                "Query Order",
                "/secure/fulfillment/orders/query",
                request,
                fulfillmentService::queryOrder);
    }

    public Map<String, Object> reserveFulfillmentOrder(String profileName, Map<String, Object> request) {
        return executeFulfillment(
                profileName,
                "Reserve Inventory",
                "/secure/fulfillment/orders/reserve",
                request,
                fulfillmentService::reserveOrder);
    }

    public Map<String, Object> fulfillFulfillmentOrder(String profileName, Map<String, Object> request) {
        return executeFulfillment(
                profileName,
                "Dispatch Shipment",
                "/secure/fulfillment/orders/fulfill",
                request,
                fulfillmentService::fulfillOrder);
    }

    public Map<String, Object> cancelFulfillmentOrder(String profileName, Map<String, Object> request) {
        return executeFulfillment(
                profileName,
                "Cancel Order",
                "/secure/fulfillment/orders/cancel",
                request,
                fulfillmentService::cancelOrder);
    }

    private Map<String, Object> executeFulfillment(
            String profileName,
            String operation,
            String path,
            Map<String, Object> request,
            Function<Map<String, Object>, Map<String, Object>> action) {
        Instant startedAt = Instant.now();
        Map<String, Object> decryptedRequest = null;
        try {
            PgpProfileMaterial profile = partnerKeyService.resolveMaterial(profileName);
            decryptedRequest = decryptPayloadField(request, profile);
            Map<String, Object> businessResult = action.apply(decryptedRequest);
            Map<String, Object> response = baseResponse(profileName, Map.of(
                    "operation", operation,
                    "payload", encryptObject(businessResult, profile)));
            activityService.record(
                    startedAt,
                    "SECURE_API",
                    operation,
                    "POST",
                    path,
                    normalizeProfileName(profileName),
                    200,
                    request,
                    decryptedRequest,
                    businessResult,
                    response,
                    null);
            return response;
        } catch (RuntimeException error) {
            activityService.record(
                    startedAt,
                    "SECURE_API",
                    operation,
                    "POST",
                    path,
                    normalizeProfileName(profileName),
                    400,
                    request,
                    decryptedRequest,
                    null,
                    null,
                    error.getMessage());
            throw error;
        }
    }

    private Map<String, Object> decryptPayloadField(Map<String, Object> request, PgpProfileMaterial profile) {
        Object encryptedField = request.get("payload");
        if (!(encryptedField instanceof String cipherText)) {
            throw new IllegalArgumentException("payload must contain an armored PGP string");
        }
        return asMap(decryptToObject(cipherText, profile));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected decrypted payload to be a JSON object");
    }

    private String normalizeProfileName(String profileName) {
        return partnerKeyService.normalizeProfileName(profileName);
    }

    private Object decryptToObject(String cipherText, PgpProfileMaterial profile) {
        String plaintext = pgpCryptoService.decryptAndVerify(cipherText, profile);
        try {
            return objectMapper.readValue(plaintext, Object.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse decrypted JSON payload", e);
        }
    }

    private String encryptObject(Object payload, PgpProfileMaterial profile) {
        try {
            String plaintext = objectMapper.writeValueAsString(payload);
            return pgpCryptoService.signAndEncrypt(plaintext, profile);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize encrypted response payload", e);
        }
    }
}
