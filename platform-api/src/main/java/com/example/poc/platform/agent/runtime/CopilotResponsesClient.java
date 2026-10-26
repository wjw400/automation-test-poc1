package com.example.poc.platform.agent.runtime;

import com.example.poc.platform.agent.AgentProperties;
import com.example.poc.platform.persistence.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/** Copilot's Responses wire protocol; credentials and provider bodies never enter platform events. */
final class CopilotResponsesClient {
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE =
            new ParameterizedTypeReference<>() {};
    private final AgentProperties properties;
    private final JsonCodec json;

    CopilotResponsesClient(AgentProperties properties, JsonCodec json) {
        this.properties = properties;
        this.json = json;
    }

    Response request(Map<String, Object> body, Consumer<String> onText, Runnable checkRunning) {
        var config = properties.getCopilot();
        String baseUrl = config.getBaseUrl().trim().replaceAll("/+$", "");
        String url = baseUrl.endsWith("/responses") ? baseUrl : baseUrl + "/responses";
        WebClient client = WebClient.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                .build();
        Stream state = new Stream(onText);
        client.post().uri(url).headers(headers -> {
                    config.getHeaders().forEach(headers::set);
                    if (!config.getApiVersion().isBlank()) headers.set("X-GitHub-Api-Version", config.getApiVersion());
                    if (!config.getIntegrationId().isBlank()) headers.set("Copilot-Integration-Id", config.getIntegrationId());
                    headers.setBearerAuth(config.getApiToken().trim());
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));
                    headers.set("X-Request-Id", UUID.randomUUID().toString());
                })
                .bodyValue(json.write(body))
                .exchangeToFlux(response -> {
                    if (!response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().thenMany(Flux.<Map<String, Object>>error(
                                httpError(response.statusCode().value())));
                    }
                    if (response.headers().contentType().filter(MediaType.APPLICATION_JSON::isCompatibleWith).isPresent()) {
                        return response.bodyToMono(String.class).map(data -> Map.<String, Object>of(
                                "type", "response.completed", "response", json.readObjectMap(data))).flux();
                    }
                    return response.bodyToFlux(SSE)
                            .filter(event -> event.data() != null && !event.data().isBlank()
                                    && !"[DONE]".equals(event.data().trim()))
                            .map(event -> {
                                Map<String, Object> value = new LinkedHashMap<>(json.readObjectMap(event.data()));
                                if (!value.containsKey("type") && event.event() != null) value.put("type", event.event());
                                return value;
                            });
                })
                .doOnNext(event -> {
                    checkRunning.run();
                    state.consume(event);
                })
                // Completion is authoritative even when the server keeps its connection open.
                .takeUntil(event -> "response.completed".equals(text(event, "type"))
                        || "response.incomplete".equals(text(event, "type")))
                .blockLast(properties.getModelRequestTimeout());
        checkRunning.run();
        return state.finish();
    }

    static AgentRuntimeException httpError(int status) {
        String code = switch (status) {
            case 401 -> "MODEL_AUTHENTICATION_FAILED";
            case 402 -> "MODEL_BALANCE_EXHAUSTED";
            case 403 -> "MODEL_ACCESS_DENIED";
            case 408, 504 -> "MODEL_TIMEOUT";
            case 429 -> "MODEL_RATE_LIMITED";
            default -> status >= 500 ? "MODEL_UNAVAILABLE" : "MODEL_PROVIDER_ERROR";
        };
        return new AgentRuntimeException(code, "Copilot Responses request failed (HTTP " + status + ")",
                status == 408 || status == 429 || status >= 500);
    }

    static AgentRuntimeException streamError(Map<String, Object> error) {
        String providerCode = text(error, "code").toLowerCase(java.util.Locale.ROOT);
        String code = switch (providerCode) {
            case "rate_limit_exceeded", "rate_limited" -> "MODEL_RATE_LIMITED";
            case "invalid_api_key", "authentication_error" -> "MODEL_AUTHENTICATION_FAILED";
            case "permission_denied", "access_denied" -> "MODEL_ACCESS_DENIED";
            case "insufficient_quota", "quota_exceeded" -> "MODEL_BALANCE_EXHAUSTED";
            case "server_error", "overloaded_error" -> "MODEL_UNAVAILABLE";
            default -> "MODEL_PROVIDER_ERROR";
        };
        return new AgentRuntimeException(code, "Copilot returned a Responses error",
                "MODEL_RATE_LIMITED".equals(code) || "MODEL_UNAVAILABLE".equals(code));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    static List<Map<String, Object>> objects(Object value) {
        return value instanceof List<?> list ? list.stream().map(CopilotResponsesClient::object).toList() : List.of();
    }

    static String text(Map<String, Object> value, String key) {
        return value.get(key) instanceof String string ? string : "";
    }

    private static long tokens(Map<String, Object> usage, String key) {
        return usage.get(key) instanceof Number number ? Math.max(0, number.longValue()) : 0;
    }

    record Response(List<Map<String, Object>> output, long inputTokens, long outputTokens, boolean incomplete) {}

    private static final class Stream {
        private final Consumer<String> onText;
        private final Map<String, StringBuilder> streamedParts = new LinkedHashMap<>();
        private Map<String, Object> response;
        private boolean incomplete;

        private Stream(Consumer<String> onText) { this.onText = onText; }

        private void consume(Map<String, Object> event) {
            switch (text(event, "type")) {
                case "response.output_text.delta" -> {
                    String delta = text(event, "delta");
                    String key = event.getOrDefault("output_index", 0) + ":" + event.getOrDefault("content_index", 0);
                    streamedParts.computeIfAbsent(key, ignored -> new StringBuilder()).append(delta);
                    if (!delta.isEmpty()) onText.accept(delta);
                }
                case "error" -> throw streamError(event);
                case "response.failed" -> throw streamError(object(object(event.get("response")).get("error")));
                case "response.incomplete" -> {
                    response = object(event.get("response"));
                    incomplete = true;
                }
                case "response.completed" -> {
                    response = object(event.get("response"));
                    if (response.get("error") != null) throw streamError(object(response.get("error")));
                    String status = text(response, "status");
                    if ("failed".equals(status)) throw streamError(object(response.get("error")));
                    if ("incomplete".equals(status)) {
                        incomplete = true;
                        return;
                    }
                    if (!status.isEmpty() && !"completed".equals(status)) throw incomplete();
                    if (!(response.get("output") instanceof List<?>)) throw incomplete();
                    // Recover non-streamed text without duplicating text already delivered as deltas.
                    List<Map<String, Object>> output = objects(response.get("output"));
                    for (int index = 0; index < output.size(); index++) {
                        Map<String, Object> item = output.get(index);
                        if (!"message".equals(text(item, "type"))) continue;
                        List<Map<String, Object>> parts = objects(item.get("content"));
                        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                            Map<String, Object> part = parts.get(partIndex);
                            String fullText = switch (text(part, "type")) {
                                case "output_text" -> text(part, "text");
                                case "refusal" -> text(part, "refusal");
                                default -> "";
                            };
                            String streamed = streamedParts.getOrDefault(index + ":" + partIndex, new StringBuilder()).toString();
                            if (!fullText.startsWith(streamed)) throw incomplete();
                            String remaining = fullText.substring(streamed.length());
                            if (!remaining.isEmpty()) onText.accept(remaining);
                        }
                    }
                }
                default -> { /* Lifecycle, reasoning and tool deltas are resolved from completed output. */ }
            }
        }

        private Response finish() {
            if (response == null) throw incomplete();
            Map<String, Object> usage = object(response.get("usage"));
            return new Response(new ArrayList<>(objects(response.get("output"))),
                    tokens(usage, "input_tokens"), tokens(usage, "output_tokens"), incomplete);
        }

        private static AgentRuntimeException incomplete() {
            return new AgentRuntimeException("MODEL_RESPONSE_INCOMPLETE",
                    "Copilot did not return a complete model response", false);
        }
    }
}
