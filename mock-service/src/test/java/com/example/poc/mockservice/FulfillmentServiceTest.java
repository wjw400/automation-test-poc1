package com.example.poc.mockservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.poc.mockservice.activity.ApiActivityService;
import com.example.poc.mockservice.service.FulfillmentService;
import com.example.poc.mockservice.service.PartnerKeyService;
import com.example.poc.mockservice.service.SecureFulfillmentService;
import com.example.poc.pgp.PgpCryptoService;
import com.example.poc.pgp.PgpProfileMaterial;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class FulfillmentServiceTest {
    @Autowired
    private FulfillmentService fulfillmentService;

    @Autowired
    private SecureFulfillmentService secureFulfillmentService;

    @Autowired
    private PartnerKeyService partnerKeyService;

    @Autowired
    private ApiActivityService activityService;

    @Autowired
    private MockMvc mockMvc;

    private final PgpCryptoService pgpCryptoService = new PgpCryptoService();
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void reset() {
        fulfillmentService.resetDemoData();
        activityService.clear();
    }

    @Test
    void createsReservesAndDispatchesAnOrder() {
        Map<String, Object> created = fulfillmentService.createOrder(Map.of(
                "customerName", "Test Partner",
                "customerReference", "TEST-PO-1",
                "items", List.of(Map.of("productId", "P101", "quantity", 2))));

        Map<String, Object> reserved = fulfillmentService.reserveOrder(Map.of("orderId", created.get("id")));
        Map<String, Object> dispatched = fulfillmentService.fulfillOrder(Map.of(
                "orderId", created.get("id"),
                "carrier", "Test Carrier"));

        assertThat(created.get("status")).isEqualTo("CREATED");
        assertThat(reserved.get("status")).isEqualTo("RESERVED");
        assertThat(dispatched.get("status")).isEqualTo("DISPATCHED");
        assertThat(dispatched).containsKey("shipment");
        assertThat(fulfillmentService.listShipments()).hasSize(2);
        assertThat(product("P101").get("availableQuantity")).isEqualTo(22);
    }

    @Test
    void acceptsEncryptedPartnerOrderAndRecordsTheExchange() throws Exception {
        String plaintext = objectMapper.writeValueAsString(Map.of(
                "customerName", "Encrypted Partner",
                "customerReference", "SECURE-PO-7",
                "items", List.of(Map.of("productId", "P102", "quantity", 3))));
        String encryptedRequest = pgpCryptoService.signAndEncrypt(plaintext, clientProfile());

        Map<String, Object> response = secureFulfillmentService.createFulfillmentOrder(
                "secureEcho",
                Map.of("payload", encryptedRequest));

        String decryptedResponse = pgpCryptoService.decryptAndVerify(
                String.valueOf(response.get("payload")),
                clientProfile());
        Map<String, Object> order = objectMapper.readValue(decryptedResponse, new TypeReference<>() {
        });

        assertThat(response)
                .containsEntry("code", "SUCCESS")
                .containsEntry("operation", "Create Order");
        assertThat(order)
                .containsEntry("customerName", "Encrypted Partner")
                .containsEntry("status", "CREATED");
        assertThat(activityService.listSummaries()).singleElement()
                .satisfies(activity -> {
                    assertThat(activity).containsEntry("channel", "SECURE_API");
                    assertThat(activity).containsEntry("outcome", "SUCCEEDED");
                });
    }

    @Test
    void resetRestoresTheSeededDataset() {
        fulfillmentService.createOrder(Map.of(
                "customerName", "Temporary",
                "items", List.of(Map.of("productId", "P100", "quantity", 1))));

        fulfillmentService.resetDemoData();

        assertThat(fulfillmentService.listProducts()).hasSize(4);
        assertThat(fulfillmentService.listOrders()).hasSize(2);
        assertThat(fulfillmentService.listShipments()).hasSize(1);
    }

    @Test
    void createsQueriesAndCancelsUsingEncryptedBusinessPayloadsAndTheReturnedId() throws Exception {
        Map<String, Object> created = decodeBusiness(secureFulfillmentService.createFulfillmentOrder("secureEcho", encryptedBody(Map.of(
                "customerName", "Automation Partner", "customerReference", "AUTO-CANCEL-1",
                "items", List.of(Map.of("productId", "P100", "quantity", 1))))));
        assertThat(created).containsEntry("status", "CREATED").containsKey("id").doesNotContainKey("orderId");
        Map<String, Object> reference = Map.of("orderId", created.get("id"));
        Map<String, Object> queried = decodeBusiness(secureFulfillmentService.queryFulfillmentOrder("secureEcho", encryptedBody(reference)));
        Map<String, Object> cancelled = decodeBusiness(secureFulfillmentService.cancelFulfillmentOrder("secureEcho", encryptedBody(reference)));
        assertThat(queried).containsEntry("id", created.get("id")).containsEntry("status", "CREATED");
        assertThat(cancelled).containsEntry("id", created.get("id")).containsEntry("status", "CANCELLED");
    }

    private Map<String, Object> encryptedBody(Map<String, Object> business) throws Exception {
        return Map.of("payload", pgpCryptoService.signAndEncrypt(objectMapper.writeValueAsString(business), clientProfile()));
    }

    private Map<String, Object> decodeBusiness(Map<String, Object> envelope) throws Exception {
        String plaintext = pgpCryptoService.decryptAndVerify((String) envelope.get("payload"), clientProfile());
        return objectMapper.readValue(plaintext, new TypeReference<>() {});
    }

    @Test
    void exposesOnlyTheSecureFulfillmentEndpoints() throws Exception {
        mockMvc.perform(post("/secure/raw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/secure/fulfillment/orders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managesPartnerPublicKeysOutsideTheBusinessDataReset() {
        Map<String, Object> serviceKey = partnerKeyService.servicePublicKey();
        assertThat(serviceKey)
                .containsKeys("publicKey", "fingerprint", "keyId", "algorithm");
        assertThat(partnerKeyService.listPartners())
                .extracting(partner -> partner.get("profileName"))
                .contains("secureEcho");

        partnerKeyService.createPartner(
                "qaPartner",
                "QA Partner",
                keyMaterial("keys/client-public.asc"));
        fulfillmentService.resetDemoData();

        assertThat(partnerKeyService.listPartners())
                .extracting(partner -> partner.get("profileName"))
                .contains("secureEcho", "qaPartner");
        assertThat(partnerKeyService.resolveMaterial("qaPartner").getVerificationPublicKey())
                .contains("BEGIN PGP PUBLIC KEY BLOCK");

        partnerKeyService.deletePartner("qaPartner");
    }

    private Map<String, Object> product(String productId) {
        return fulfillmentService.listProducts().stream()
                .filter(product -> productId.equals(product.get("id")))
                .findFirst()
                .orElseThrow();
    }

    private PgpProfileMaterial clientProfile() {
        PgpProfileMaterial profile = new PgpProfileMaterial();
        profile.setVerificationPublicKey(keyMaterial("keys/server-public.asc"));
        profile.setSigningPrivateKey(keyMaterial("keys/client-private.asc"));
        profile.setSigningPrivateKeyPassphrase("demo-client-passphrase");
        profile.setEncryptionPublicKey(keyMaterial("keys/server-public.asc"));
        profile.setDecryptionPrivateKey(keyMaterial("keys/client-private.asc"));
        profile.setDecryptionPrivateKeyPassphrase("demo-client-passphrase");
        return profile;
    }

    private String keyMaterial(String resourcePath) {
        try (var inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Test key not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read test key: " + resourcePath, error);
        }
    }
}
