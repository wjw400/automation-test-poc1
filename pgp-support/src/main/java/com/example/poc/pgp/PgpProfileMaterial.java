package com.example.poc.pgp;

public class PgpProfileMaterial {
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
}
