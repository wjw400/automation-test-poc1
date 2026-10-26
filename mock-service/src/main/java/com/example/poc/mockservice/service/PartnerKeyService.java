package com.example.poc.mockservice.service;

import com.example.poc.mockservice.config.CryptoConfigurationProperties;
import com.example.poc.mockservice.config.ServiceIdentityProperties;
import com.example.poc.mockservice.domain.PartnerKeyEntity;
import com.example.poc.mockservice.domain.PartnerKeyRepository;
import com.example.poc.pgp.PgpProfileMaterial;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerKeyService {
    private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    private final PartnerKeyRepository repository;
    private final CryptoConfigurationProperties cryptoProperties;

    public PartnerKeyService(
            PartnerKeyRepository repository,
            CryptoConfigurationProperties cryptoProperties) {
        this.repository = repository;
        this.cryptoProperties = cryptoProperties;
    }

    @Transactional
    public void ensureSeededPartner() {
        String profileName = cryptoProperties.getDefaultProfile();
        if (repository.existsById(profileName)) {
            return;
        }
        String publicKey = readLocation(cryptoProperties.getSeededPartner().getPublicKey());
        save(profileName, cryptoProperties.getSeededPartner().getDisplayName(), publicKey, false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> servicePublicKey() {
        String publicKey = readLocation(cryptoProperties.getService().getPublicKey());
        KeyMetadata metadata = parsePublicKey(publicKey);
        Map<String, Object> result = metadataMap(metadata);
        result.put("publicKey", publicKey);
        return result;
    }

    public String servicePublicKeyArmored() {
        return readLocation(cryptoProperties.getService().getPublicKey());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPartners() {
        return repository.findAllByOrderByProfileNameAsc().stream()
                .map(this::partnerSummary)
                .toList();
    }

    @Transactional
    public Map<String, Object> createPartner(String profileName, String displayName, String publicKey) {
        if (repository.existsById(profileName)) {
            throw new IllegalArgumentException("Partner profile already exists: " + profileName);
        }
        return partnerSummary(save(profileName, displayName, publicKey, false));
    }

    @Transactional
    public Map<String, Object> replacePartner(String profileName, String displayName, String publicKey) {
        if (!repository.existsById(profileName)) {
            throw new IllegalArgumentException("Unknown partner profile: " + profileName);
        }
        return partnerSummary(save(profileName, displayName, publicKey, true));
    }

    @Transactional
    public void deletePartner(String profileName) {
        if (!repository.existsById(profileName)) {
            throw new IllegalArgumentException("Unknown partner profile: " + profileName);
        }
        repository.deleteById(profileName);
    }

    @Transactional(readOnly = true)
    public PgpProfileMaterial resolveMaterial(String requestedProfile) {
        String profileName = normalizeProfileName(requestedProfile);
        PartnerKeyEntity partner = repository.findById(profileName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner profile: " + profileName));
        ServiceIdentityProperties service = cryptoProperties.getService();

        PgpProfileMaterial material = new PgpProfileMaterial();
        material.setVerificationPublicKey(partner.getPublicKey());
        material.setSigningPrivateKey(service.getPrivateKey());
        material.setSigningPrivateKeyPassphrase(service.getPrivateKeyPassphrase());
        material.setEncryptionPublicKey(partner.getPublicKey());
        material.setDecryptionPrivateKey(service.getPrivateKey());
        material.setDecryptionPrivateKeyPassphrase(service.getPrivateKeyPassphrase());
        return material;
    }

    public String normalizeProfileName(String profileName) {
        return profileName == null || profileName.isBlank()
                ? cryptoProperties.getDefaultProfile()
                : profileName;
    }

    private PartnerKeyEntity save(
            String profileName,
            String displayName,
            String publicKey,
            boolean replacing) {
        validateProfileName(profileName);
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        KeyMetadata metadata = parsePublicKey(publicKey);
        Instant now = Instant.now();
        PartnerKeyEntity entity = replacing
                ? repository.findById(profileName).orElseThrow()
                : new PartnerKeyEntity();
        entity.setProfileName(profileName);
        entity.setDisplayName(displayName.strip());
        entity.setPublicKey(publicKey.strip());
        entity.setKeyId(metadata.keyId());
        entity.setFingerprint(metadata.fingerprint());
        entity.setAlgorithm(metadata.algorithm());
        entity.setUserId(metadata.userId());
        entity.setExpiresAt(metadata.expiresAt());
        entity.setCreatedAt(entity.getCreatedAt() == null ? now : entity.getCreatedAt());
        entity.setUpdatedAt(now);
        return repository.save(entity);
    }

    private void validateProfileName(String profileName) {
        if (profileName == null || !PROFILE_NAME.matcher(profileName).matches()) {
            throw new IllegalArgumentException(
                    "Profile name must contain only letters, numbers, dots, underscores, or hyphens");
        }
    }

    private Map<String, Object> partnerSummary(PartnerKeyEntity entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileName", entity.getProfileName());
        result.put("displayName", entity.getDisplayName());
        result.put("keyId", entity.getKeyId());
        result.put("fingerprint", entity.getFingerprint());
        result.put("algorithm", entity.getAlgorithm());
        result.put("userId", entity.getUserId());
        result.put("expiresAt", entity.getExpiresAt());
        result.put("updatedAt", entity.getUpdatedAt());
        result.put("defaultProfile", entity.getProfileName().equals(cryptoProperties.getDefaultProfile()));
        return result;
    }

    private Map<String, Object> metadataMap(KeyMetadata metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keyId", metadata.keyId());
        result.put("fingerprint", metadata.fingerprint());
        result.put("algorithm", metadata.algorithm());
        result.put("userId", metadata.userId());
        result.put("createdAt", metadata.createdAt());
        result.put("expiresAt", metadata.expiresAt());
        return result;
    }

    private KeyMetadata parsePublicKey(String armoredKey) {
        if (armoredKey == null || !armoredKey.contains("-----BEGIN PGP PUBLIC KEY BLOCK-----")) {
            throw new IllegalArgumentException("A valid ASCII-armored PGP public key is required");
        }
        try (InputStream decoded = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armoredKey.getBytes(StandardCharsets.UTF_8)))) {
            PGPPublicKeyRingCollection rings = new PGPPublicKeyRingCollection(
                    decoded,
                    new JcaKeyFingerprintCalculator());
            PGPPublicKey primary = null;
            boolean encryptionKeyPresent = false;
            for (Iterator<PGPPublicKeyRing> ringIterator = rings.getKeyRings(); ringIterator.hasNext();) {
                PGPPublicKeyRing ring = ringIterator.next();
                for (Iterator<PGPPublicKey> keyIterator = ring.getPublicKeys(); keyIterator.hasNext();) {
                    PGPPublicKey key = keyIterator.next();
                    if (primary == null || key.isMasterKey()) {
                        primary = key;
                    }
                    encryptionKeyPresent |= key.isEncryptionKey();
                }
            }
            if (primary == null || !encryptionKeyPresent) {
                throw new IllegalArgumentException("Public key must contain an encryption-capable key");
            }
            String userId = primary.getUserIDs().hasNext() ? primary.getUserIDs().next() : null;
            Instant createdAt = primary.getCreationTime().toInstant();
            Instant expiresAt = primary.getValidSeconds() == 0
                    ? null
                    : createdAt.plus(primary.getValidSeconds(), ChronoUnit.SECONDS);
            return new KeyMetadata(
                    String.format("%016X", primary.getKeyID()),
                    formatFingerprint(primary.getFingerprint()),
                    algorithmName(primary.getAlgorithm()),
                    userId,
                    createdAt,
                    expiresAt);
        } catch (IOException | PGPException error) {
            throw new IllegalArgumentException("Failed to parse PGP public key", error);
        }
    }

    private String formatFingerprint(byte[] fingerprint) {
        String hex = HexFormat.of().withUpperCase().formatHex(fingerprint);
        return hex.replaceAll("(.{4})(?!$)", "$1 ");
    }

    private String algorithmName(int algorithm) {
        return switch (algorithm) {
            case 1, 2, 3 -> "RSA";
            case 16 -> "ElGamal";
            case 17 -> "DSA";
            case 18 -> "ECDH";
            case 19 -> "ECDSA";
            case 22 -> "EdDSA";
            default -> "Algorithm " + algorithm;
        };
    }

    private String readLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Key location is blank");
        }
        if (location.stripLeading().startsWith("-----BEGIN PGP ")) {
            return location;
        }
        try {
            if (location.startsWith("classpath:")) {
                String resourcePath = location.substring("classpath:".length());
                try (InputStream inputStream = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream(resourcePath)) {
                    if (inputStream == null) {
                        throw new IllegalArgumentException("Classpath key not found: " + resourcePath);
                    }
                    return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return Files.readString(Path.of(location), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalArgumentException("Failed to read key: " + location, error);
        }
    }

    private record KeyMetadata(
            String keyId,
            String fingerprint,
            String algorithm,
            String userId,
            Instant createdAt,
            Instant expiresAt) {
    }
}
