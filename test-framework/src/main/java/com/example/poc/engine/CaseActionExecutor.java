package com.example.poc.engine;

import com.example.poc.pgp.PgpCryptoService;
import com.example.poc.pgp.PgpProfileMaterial;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CaseActionExecutor {
    public static final String SIGN_AND_ENCRYPT = "crypto.pgp.signAndEncrypt";
    public static final String ENCODE_BASE64 = "data.encodeBase64";
    public static final String BUILD_JSON = "body.buildJson";
    public static final String DECRYPT_AND_VERIFY = "crypto.pgp.decryptAndVerify";

    public static final List<String> BEFORE_SEND_TYPES = List.of(SIGN_AND_ENCRYPT, ENCODE_BASE64, BUILD_JSON);
    public static final List<String> AFTER_RECEIVE_TYPES = List.of(DECRYPT_AND_VERIFY);

    private final ObjectMapper jsonMapper;
    private final VariableResolver variableResolver;
    private final PgpCryptoService pgpCryptoService;

    public CaseActionExecutor(
            ObjectMapper jsonMapper,
            VariableResolver variableResolver,
            PgpCryptoService pgpCryptoService) {
        this.jsonMapper = jsonMapper;
        this.variableResolver = variableResolver;
        this.pgpCryptoService = pgpCryptoService;
    }

    public Object inspectInput(CaseActionSpec action, ActionExecutionState state, TestContext context) {
        if (BUILD_JSON.equals(action.getType())) {
            return variableResolver.resolveObject(action.getTemplate(), context, state.getArtifacts());
        }
        return read(action.getInput().getSource(), action.getInput().getPath(), state, context);
    }

    public Object execute(
            CaseActionSpec action,
            ActionExecutionState state,
            CryptoProfile profile,
            Object input) {
        return switch (action.getType()) {
            case SIGN_AND_ENCRYPT -> signAndEncrypt(action, state, profile, input);
            case ENCODE_BASE64 -> encodeBase64(action, state, input);
            case BUILD_JSON -> buildJson(action, state, input);
            case DECRYPT_AND_VERIFY -> decryptAndVerify(action, state, profile, input);
            default -> throw new IllegalArgumentException("Unsupported action type: " + action.getType());
        };
    }

    private Object signAndEncrypt(
            CaseActionSpec action,
            ActionExecutionState state,
            CryptoProfile profile,
            Object input) {
        requireProfile(profile, action.getType());
        String plaintext = serialize(input, defaultValue(action.getInput().getFormat(), "text"));
        String encrypted = pgpCryptoService.signAndEncrypt(plaintext, toPgpProfile(profile));
        write(action.getOutput(), encrypted, state);
        return encrypted;
    }

    private Object encodeBase64(CaseActionSpec action, ActionExecutionState state, Object input) {
        byte[] bytes;
        if (input instanceof byte[] binary) {
            bytes = binary;
        } else if (input instanceof String text) {
            bytes = text.getBytes(StandardCharsets.UTF_8);
        } else {
            try {
                bytes = jsonMapper.writeValueAsBytes(input);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize Base64 action input", e);
            }
        }
        String encoded = Base64.getEncoder().encodeToString(bytes);
        write(action.getOutput(), encoded, state);
        return encoded;
    }

    private Object buildJson(CaseActionSpec action, ActionExecutionState state, Object input) {
        Object body = input;
        if (!(body instanceof Map<?, ?>) && !(body instanceof List<?>)) {
            throw new IllegalArgumentException("body.buildJson template must resolve to a JSON object or array");
        }
        write(action.getOutput(), body, state);
        return body;
    }

    private Object decryptAndVerify(
            CaseActionSpec action,
            ActionExecutionState state,
            CryptoProfile profile,
            Object input) {
        requireProfile(profile, action.getType());
        String plaintext = pgpCryptoService.decryptAndVerify(Objects.toString(input, null), toPgpProfile(profile));
        Object output = deserialize(plaintext, defaultValue(action.getOutput().getFormat(), "text"));
        write(action.getOutput(), output, state);
        return output;
    }

    private Object read(
            String source,
            String path,
            ActionExecutionState state,
            TestContext context) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Action input.source is required");
        }
        Object value;
        if ("request.body".equals(source)) {
            value = state.getRequestBody();
        } else if ("response.body".equals(source)) {
            value = state.getResponseBody();
        } else if (source.startsWith("artifacts.")) {
            value = namedValue(state.getArtifacts(), source, "artifacts.");
        } else if (source.startsWith("decrypted.")) {
            value = namedValue(state.getDecryptedPayloads(), source, "decrypted.");
        } else if (source.startsWith("variables.")) {
            String name = source.substring("variables.".length());
            value = context.get(name)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown variable source: " + source));
        } else {
            throw new IllegalArgumentException("Unsupported action input source: " + source);
        }
        if (path == null || path.isBlank()) {
            return value;
        }
        if (value == null) {
            throw new IllegalArgumentException("Cannot read " + path + " from null source " + source);
        }
        return value instanceof String text
                ? JsonPath.read(text, path)
                : JsonPath.read(value, path);
    }

    private void write(ActionOutputSpec output, Object value, ActionExecutionState state) {
        String target = output.getTarget();
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Action output.target is required");
        }
        if ("request.body".equals(target)) {
            state.setRequestBody(writePath(state.getRequestBody(), output.getPath(), value, target));
            return;
        }
        if (target.startsWith("artifacts.")) {
            writeNamedValue(state.getArtifacts(), target, "artifacts.", output.getPath(), value);
            return;
        }
        if (target.startsWith("decrypted.")) {
            writeNamedValue(state.getDecryptedPayloads(), target, "decrypted.", output.getPath(), value);
            return;
        }
        throw new IllegalArgumentException("Unsupported action output target: " + target);
    }

    private void writeNamedValue(
            Map<String, Object> values,
            String target,
            String prefix,
            String path,
            Object value) {
        String name = target.substring(prefix.length());
        if (name.isBlank()) {
            throw new IllegalArgumentException("Action output target name is required: " + target);
        }
        values.put(name, writePath(values.get(name), path, value, target));
    }

    private Object writePath(Object current, String path, Object value, String target) {
        if (path == null || path.isBlank()) {
            return value;
        }
        if (current == null) {
            throw new IllegalArgumentException("Cannot write " + path + " because " + target + " does not exist");
        }
        return JsonPath.parse(current).set(path, value).json();
    }

    private Object namedValue(Map<String, Object> values, String source, String prefix) {
        String name = source.substring(prefix.length());
        if (!values.containsKey(name)) {
            throw new IllegalArgumentException("Unknown action source: " + source);
        }
        return values.get(name);
    }

    private String serialize(Object value, String format) {
        if ("text".equalsIgnoreCase(format) || "xml".equalsIgnoreCase(format)) {
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("Action input format " + format + " requires a string value");
            }
            return text;
        }
        if ("json".equalsIgnoreCase(format)) {
            try {
                return jsonMapper.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to serialize action input as JSON", e);
            }
        }
        throw new IllegalArgumentException("Unsupported action input format: " + format);
    }

    private Object deserialize(String plaintext, String format) {
        if ("text".equalsIgnoreCase(format) || "xml".equalsIgnoreCase(format)) {
            return plaintext;
        }
        if ("json".equalsIgnoreCase(format)) {
            try {
                return jsonMapper.readValue(plaintext, Object.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Failed to parse decrypted payload as JSON", e);
            }
        }
        throw new IllegalArgumentException("Unsupported action output format: " + format);
    }

    private void requireProfile(CryptoProfile profile, String actionType) {
        if (profile == null) {
            throw new IllegalArgumentException(actionType + " requires a Step or Scenario profile");
        }
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private PgpProfileMaterial toPgpProfile(CryptoProfile profile) {
        PgpProfileMaterial material = new PgpProfileMaterial();
        material.setSenderPublicKey(profile.getSenderPublicKey());
        material.setSenderPrivateKey(profile.getSenderPrivateKey());
        material.setSenderPrivateKeyPassphrase(profile.getSenderPrivateKeyPassphrase());
        material.setRecipientPublicKey(profile.getRecipientPublicKey());
        material.setRecipientPrivateKey(profile.getRecipientPrivateKey());
        material.setRecipientPrivateKeyPassphrase(profile.getRecipientPrivateKeyPassphrase());
        return material;
    }
}
