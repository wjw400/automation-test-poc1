package com.example.poc.engine;

import java.util.List;

/** Compiles API configuration into the same visible actions used by manual cases. */
public final class OperationProcessingActions {
    private OperationProcessingActions() {}
    public static List<CaseActionSpec> request(OperationProcessingSpec.MessageRule rule) {
        if ("none".equals(rule.getProtection())) return List.of();
        CaseActionSpec encrypt = action("pgp-sign-encrypt".equals(rule.getProtection())
                ? CaseActionExecutor.SIGN_AND_ENCRYPT : CaseActionExecutor.ENCRYPT, "Protect business request");
        encrypt.getInput().setSource("request.body");
        encrypt.getInput().setFormat("json");
        encrypt.getOutput().setTarget("artifacts.__platformCiphertext");
        CaseActionSpec envelope = action(CaseActionExecutor.BUILD_JSON, "Write ciphertext to " + ("body".equals(rule.getCiphertext().getLocation()) ? "HTTP body" : rule.getCiphertext().getPointer()));
        Object template = "body".equals(rule.getCiphertext().getLocation()) ? "${artifacts.__platformCiphertext}"
                : JsonBodyPointer.write(OperationProcessingSpec.copy(rule.getEnvelope()), rule.getCiphertext().getPointer(), "${artifacts.__platformCiphertext}");
        envelope.setTemplate(template);
        envelope.getOutput().setTarget("request.body");
        return List.of(encrypt, envelope);
    }

    public static List<CaseActionSpec> response(OperationProcessingSpec.MessageRule rule) {
        if ("none".equals(rule.getProtection())) return List.of();
        CaseActionSpec decrypt = action("pgp-decrypt-verify".equals(rule.getProtection())
                ? CaseActionExecutor.DECRYPT_AND_VERIFY : CaseActionExecutor.DECRYPT, "Decode business response");
        decrypt.getInput().setSource("response.body");
        if (!"body".equals(rule.getCiphertext().getLocation())) decrypt.getInput().setPointer(rule.getCiphertext().getPointer());
        decrypt.getOutput().setTarget("decrypted.business");
        decrypt.getOutput().setFormat("json");
        return List.of(decrypt);
    }

    private static CaseActionSpec action(String type, String name) {
        CaseActionSpec action = new CaseActionSpec();
        action.setType(type);
        action.setName(name);
        action.setOrigin("API");
        return action;
    }
}
