package com.example.poc.pgp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;

public class PgpCryptoService {
    private static final String PROVIDER = "BC";

    static {
        if (Security.getProvider(PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public String signAndEncrypt(String plaintext, PgpProfileMaterial profile) {
        ensureOutboundProfile(profile);
        try {
            byte[] data = plaintext.getBytes(StandardCharsets.UTF_8);
            PGPSecretKey signingKey = loadSigningSecretKey(profile.getSenderPrivateKey());
            PGPPrivateKey privateKey = extractPrivateKey(signingKey, profile.getSenderPrivateKeyPassphrase());
            PGPPublicKey encryptionKey = loadEncryptionKey(profile.getRecipientPublicKey());

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ArmoredOutputStream armoredOutputStream = new ArmoredOutputStream(outputStream)) {
                PGPEncryptedDataGenerator encryptedDataGenerator = new PGPEncryptedDataGenerator(
                        new JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                                .setWithIntegrityPacket(true)
                                .setSecureRandom(new SecureRandom())
                                .setProvider(PROVIDER));
                encryptedDataGenerator.addMethod(
                        new JcePublicKeyKeyEncryptionMethodGenerator(encryptionKey).setProvider(PROVIDER));

                try (OutputStream encryptedOutput = encryptedDataGenerator.open(armoredOutputStream, new byte[1 << 16])) {
                    PGPCompressedDataGenerator compressedDataGenerator =
                            new PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP);
                    try (OutputStream compressedOutput = compressedDataGenerator.open(encryptedOutput)) {
                        PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(
                                new JcaPGPContentSignerBuilder(
                                        signingKey.getPublicKey().getAlgorithm(),
                                        HashAlgorithmTags.SHA256)
                                        .setProvider(PROVIDER));
                        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);

                        Iterator<String> userIds = signingKey.getPublicKey().getUserIDs();
                        if (userIds.hasNext()) {
                            PGPSignatureSubpacketGenerator subpacketGenerator = new PGPSignatureSubpacketGenerator();
                            subpacketGenerator.setSignerUserID(false, userIds.next());
                            signatureGenerator.setHashedSubpackets(subpacketGenerator.generate());
                        }

                        signatureGenerator.generateOnePassVersion(false).encode(compressedOutput);

                        PGPLiteralDataGenerator literalDataGenerator = new PGPLiteralDataGenerator();
                        try (OutputStream literalOutput = literalDataGenerator.open(
                                compressedOutput,
                                PGPLiteralData.BINARY,
                                PGPLiteralData.CONSOLE,
                                data.length,
                                new Date())) {
                            literalOutput.write(data);
                            signatureGenerator.update(data);
                        }

                        signatureGenerator.generate().encode(compressedOutput);
                    } finally {
                        compressedDataGenerator.close();
                    }
                }
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (IOException | PGPException e) {
            throw new IllegalArgumentException("Failed to sign and encrypt payload", e);
        }
    }

    public String decryptAndVerify(String armoredMessage, PgpProfileMaterial profile) {
        ensureInboundProfile(profile);
        try (InputStream decoderInput = PGPUtil.getDecoderStream(
                new ByteArrayInputStream(armoredMessage.getBytes(StandardCharsets.UTF_8)))) {
            JcaPGPObjectFactory objectFactory = new JcaPGPObjectFactory(decoderInput);
            Object firstObject = objectFactory.nextObject();
            PGPEncryptedDataList encryptedDataList = firstObject instanceof PGPEncryptedDataList
                    ? (PGPEncryptedDataList) firstObject
                    : (PGPEncryptedDataList) objectFactory.nextObject();

            PGPSecretKeyRingCollection secretKeyRings = loadSecretKeyRingCollection(profile.getRecipientPrivateKey());
            PGPPublicKeyEncryptedData encryptedData = null;
            PGPPrivateKey privateKey = null;

            for (Iterator<?> iterator = encryptedDataList.getEncryptedDataObjects(); iterator.hasNext();) {
                PGPPublicKeyEncryptedData candidate = (PGPPublicKeyEncryptedData) iterator.next();
                PGPSecretKey secretKey = secretKeyRings.getSecretKey(candidate.getKeyID());
                if (secretKey != null) {
                    encryptedData = candidate;
                    privateKey = extractPrivateKey(secretKey, profile.getRecipientPrivateKeyPassphrase());
                    break;
                }
            }

            if (encryptedData == null || privateKey == null) {
                throw new IllegalArgumentException("Could not find matching private key for encrypted payload");
            }

            try (InputStream clearData = encryptedData.getDataStream(
                    new JcePublicKeyDataDecryptorFactoryBuilder().setProvider(PROVIDER).build(privateKey))) {
                JcaPGPObjectFactory clearFactory = new JcaPGPObjectFactory(clearData);
                Object message = clearFactory.nextObject();

                if (message instanceof PGPCompressedData compressedData) {
                    clearFactory = new JcaPGPObjectFactory(compressedData.getDataStream());
                    message = clearFactory.nextObject();
                }

                PGPOnePassSignatureList onePassSignatures = null;
                PGPLiteralData literalData = null;
                PGPSignatureList signatureList = null;

                while (message != null) {
                    if (message instanceof PGPOnePassSignatureList signatures) {
                        onePassSignatures = signatures;
                    } else if (message instanceof PGPLiteralData data) {
                        literalData = data;
                    } else if (message instanceof PGPSignatureList signatures) {
                        signatureList = signatures;
                    }
                    message = clearFactory.nextObject();
                }

                if (literalData == null) {
                    throw new IllegalArgumentException("PGP payload did not contain literal data");
                }

                byte[] literalBytes;
                try (InputStream literalInput = literalData.getInputStream()) {
                    literalBytes = literalInput.readAllBytes();
                }

                if (onePassSignatures == null || signatureList == null || onePassSignatures.size() == 0) {
                    throw new IllegalArgumentException("PGP payload did not contain a verifiable signature");
                }

                PGPOnePassSignature onePassSignature = onePassSignatures.get(0);
                PGPPublicKey verificationKey = loadVerificationKey(profile.getSenderPublicKey(), onePassSignature.getKeyID());
                onePassSignature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(PROVIDER), verificationKey);
                onePassSignature.update(literalBytes);

                PGPSignature signature = signatureList.get(0);
                if (!onePassSignature.verify(signature)) {
                    throw new IllegalArgumentException("PGP signature verification failed");
                }

                if (encryptedData.isIntegrityProtected() && !encryptedData.verify()) {
                    throw new IllegalArgumentException("PGP integrity check failed");
                }

                return new String(literalBytes, StandardCharsets.UTF_8);
            }
        } catch (IOException | PGPException e) {
            throw new IllegalArgumentException("Failed to decrypt and verify payload", e);
        }
    }

    private void ensureOutboundProfile(PgpProfileMaterial profile) {
        if (profile == null
                || isBlank(profile.getSenderPrivateKey())
                || isBlank(profile.getSenderPrivateKeyPassphrase())
                || isBlank(profile.getRecipientPublicKey())) {
            throw new IllegalArgumentException("Outbound PGP profile is incomplete");
        }
    }

    private void ensureInboundProfile(PgpProfileMaterial profile) {
        if (profile == null
                || isBlank(profile.getRecipientPrivateKey())
                || isBlank(profile.getRecipientPrivateKeyPassphrase())
                || isBlank(profile.getSenderPublicKey())) {
            throw new IllegalArgumentException("Inbound PGP profile is incomplete");
        }
    }

    private PGPPublicKey loadEncryptionKey(String location) throws IOException, PGPException {
        PGPPublicKeyRingCollection publicKeyRings = loadPublicKeyRingCollection(location);
        for (Iterator<PGPPublicKeyRing> ringIterator = publicKeyRings.getKeyRings(); ringIterator.hasNext();) {
            PGPPublicKeyRing ring = ringIterator.next();
            for (Iterator<PGPPublicKey> keyIterator = ring.getPublicKeys(); keyIterator.hasNext();) {
                PGPPublicKey key = keyIterator.next();
                if (key.isEncryptionKey()) {
                    return key;
                }
            }
        }
        throw new IllegalArgumentException("No encryption key found in " + location);
    }

    private PGPPublicKey loadVerificationKey(String location, long keyId) throws IOException, PGPException {
        PGPPublicKeyRingCollection publicKeyRings = loadPublicKeyRingCollection(location);
        PGPPublicKey key = publicKeyRings.getPublicKey(keyId);
        if (key != null) {
            return key;
        }
        for (Iterator<PGPPublicKeyRing> ringIterator = publicKeyRings.getKeyRings(); ringIterator.hasNext();) {
            PGPPublicKeyRing ring = ringIterator.next();
            for (Iterator<PGPPublicKey> keyIterator = ring.getPublicKeys(); keyIterator.hasNext();) {
                PGPPublicKey candidate = keyIterator.next();
                if (!candidate.isEncryptionKey()) {
                    return candidate;
                }
            }
        }
        throw new IllegalArgumentException("No verification key found in " + location);
    }

    private PGPSecretKey loadSigningSecretKey(String location) throws IOException, PGPException {
        PGPSecretKeyRingCollection secretKeyRings = loadSecretKeyRingCollection(location);
        for (Iterator<PGPSecretKeyRing> ringIterator = secretKeyRings.getKeyRings(); ringIterator.hasNext();) {
            PGPSecretKeyRing ring = ringIterator.next();
            for (Iterator<PGPSecretKey> keyIterator = ring.getSecretKeys(); keyIterator.hasNext();) {
                PGPSecretKey key = keyIterator.next();
                if (key.isSigningKey()) {
                    return key;
                }
            }
        }
        throw new IllegalArgumentException("No signing key found in " + location);
    }

    private PGPPrivateKey extractPrivateKey(PGPSecretKey secretKey, String passphrase) throws PGPException {
        PBESecretKeyDecryptor decryptor = new JcePBESecretKeyDecryptorBuilder()
                .setProvider(PROVIDER)
                .build(passphrase == null ? new char[0] : passphrase.toCharArray());
        return secretKey.extractPrivateKey(decryptor);
    }

    private PGPPublicKeyRingCollection loadPublicKeyRingCollection(String location) throws IOException, PGPException {
        try (InputStream inputStream = PGPUtil.getDecoderStream(openLocation(location))) {
            return new PGPPublicKeyRingCollection(inputStream, new JcaKeyFingerprintCalculator());
        }
    }

    private PGPSecretKeyRingCollection loadSecretKeyRingCollection(String location) throws IOException, PGPException {
        try (InputStream inputStream = PGPUtil.getDecoderStream(openLocation(location))) {
            return new PGPSecretKeyRingCollection(inputStream, new JcaKeyFingerprintCalculator());
        }
    }

    private InputStream openLocation(String location) throws IOException {
        if (isBlank(location)) {
            throw new IllegalArgumentException("Key location is blank");
        }
        if (location.stripLeading().startsWith("-----BEGIN PGP ")) {
            return new ByteArrayInputStream(location.getBytes(StandardCharsets.UTF_8));
        }
        if (location.startsWith("classpath:")) {
            String resourcePath = location.substring("classpath:".length());
            InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                throw new IOException("Classpath resource not found: " + resourcePath);
            }
            return inputStream;
        }
        return Files.newInputStream(Path.of(location));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
