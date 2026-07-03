package com.example.poc.mockservice.config;

import com.example.poc.pgp.PgpProfileMaterial;

public class CryptoProfileProperties {
    private String senderPublicKey;
    private String senderPrivateKey;
    private String senderPrivateKeyPassphrase;
    private String recipientPublicKey;
    private String recipientPrivateKey;
    private String recipientPrivateKeyPassphrase;

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public void setSenderPublicKey(String senderPublicKey) {
        this.senderPublicKey = senderPublicKey;
    }

    public String getSenderPrivateKey() {
        return senderPrivateKey;
    }

    public void setSenderPrivateKey(String senderPrivateKey) {
        this.senderPrivateKey = senderPrivateKey;
    }

    public String getSenderPrivateKeyPassphrase() {
        return senderPrivateKeyPassphrase;
    }

    public void setSenderPrivateKeyPassphrase(String senderPrivateKeyPassphrase) {
        this.senderPrivateKeyPassphrase = senderPrivateKeyPassphrase;
    }

    public String getRecipientPublicKey() {
        return recipientPublicKey;
    }

    public void setRecipientPublicKey(String recipientPublicKey) {
        this.recipientPublicKey = recipientPublicKey;
    }

    public String getRecipientPrivateKey() {
        return recipientPrivateKey;
    }

    public void setRecipientPrivateKey(String recipientPrivateKey) {
        this.recipientPrivateKey = recipientPrivateKey;
    }

    public String getRecipientPrivateKeyPassphrase() {
        return recipientPrivateKeyPassphrase;
    }

    public void setRecipientPrivateKeyPassphrase(String recipientPrivateKeyPassphrase) {
        this.recipientPrivateKeyPassphrase = recipientPrivateKeyPassphrase;
    }

    public PgpProfileMaterial toPgpProfileMaterial() {
        PgpProfileMaterial material = new PgpProfileMaterial();
        material.setSenderPublicKey(senderPublicKey);
        material.setSenderPrivateKey(senderPrivateKey);
        material.setSenderPrivateKeyPassphrase(senderPrivateKeyPassphrase);
        material.setRecipientPublicKey(recipientPublicKey);
        material.setRecipientPrivateKey(recipientPrivateKey);
        material.setRecipientPrivateKeyPassphrase(recipientPrivateKeyPassphrase);
        return material;
    }
}
