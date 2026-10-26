package com.example.poc.engine;

/** Our signing/decryption private keys and their encryption/verification public keys. */
public class CryptoProfile {
    private String verificationPublicKey;
    private String signingPrivateKey;
    private String signingPrivateKeyPassphrase;
    private String encryptionPublicKey;
    private String decryptionPrivateKey;
    private String decryptionPrivateKeyPassphrase;

    public String getVerificationPublicKey() {
        return verificationPublicKey;
    }

    public void setVerificationPublicKey(String verificationPublicKey) {
        this.verificationPublicKey = verificationPublicKey;
    }

    public String getSigningPrivateKey() {
        return signingPrivateKey;
    }

    public void setSigningPrivateKey(String signingPrivateKey) {
        this.signingPrivateKey = signingPrivateKey;
    }

    public String getSigningPrivateKeyPassphrase() {
        return signingPrivateKeyPassphrase;
    }

    public void setSigningPrivateKeyPassphrase(String signingPrivateKeyPassphrase) {
        this.signingPrivateKeyPassphrase = signingPrivateKeyPassphrase;
    }

    public String getEncryptionPublicKey() {
        return encryptionPublicKey;
    }

    public void setEncryptionPublicKey(String encryptionPublicKey) {
        this.encryptionPublicKey = encryptionPublicKey;
    }

    public String getDecryptionPrivateKey() {
        return decryptionPrivateKey;
    }

    public void setDecryptionPrivateKey(String decryptionPrivateKey) {
        this.decryptionPrivateKey = decryptionPrivateKey;
    }

    public String getDecryptionPrivateKeyPassphrase() {
        return decryptionPrivateKeyPassphrase;
    }

    public void setDecryptionPrivateKeyPassphrase(String decryptionPrivateKeyPassphrase) {
        this.decryptionPrivateKeyPassphrase = decryptionPrivateKeyPassphrase;
    }
}
