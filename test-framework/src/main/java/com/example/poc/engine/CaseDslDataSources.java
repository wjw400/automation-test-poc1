package com.example.poc.engine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CaseDslDataSources {
    public static final String RESPONSE_BODY = "response.body";
    public static final String DECRYPTED_PREFIX = "decrypted.";
    public static final String JSON_PATH = "JSONPath";
    public static final String BUSINESS_RESPONSE_BODY = "business.response.body";
    public static final String WIRE_RESPONSE_BODY = "wire.response.body";

    private CaseDslDataSources() {}

    public static List<ResponseDataSource> responseSources(ApiStep step) {
        List<ResponseDataSource> sources = new ArrayList<>();
        sources.add(new ResponseDataSource(
                RESPONSE_BODY, "HTTP_RESPONSE_BODY", JSON_PATH, null, null));
        sources.add(new ResponseDataSource(BUSINESS_RESPONSE_BODY, "BUSINESS_RESPONSE_BODY", JSON_PATH, null, "API response processing"));
        sources.add(new ResponseDataSource(WIRE_RESPONSE_BODY, "HTTP_RESPONSE_BODY", JSON_PATH, null, null));
        if (step == null || step.getActions() == null) return List.copyOf(sources);

        Set<String> names = new LinkedHashSet<>();
        names.add(RESPONSE_BODY);
        List<CaseActionSpec> actions = step.getActions().getAfterReceive();
        for (int index = 0; index < actions.size(); index++) {
            CaseActionSpec action = actions.get(index);
            if (action == null || !action.isEnabled() || action.getOutput() == null) continue;
            String target = action.getOutput().getTarget();
            if (!isNamedSource(target, DECRYPTED_PREFIX) || !names.add(target)) continue;
            sources.add(new ResponseDataSource(
                    target,
                    "DECRYPTED_PAYLOAD",
                    JSON_PATH,
                    index,
                    action.getName()));
        }
        return List.copyOf(sources);
    }

    public static Set<String> responseSourceNames(ApiStep step) {
        return responseSources(step).stream()
                .map(ResponseDataSource::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public static boolean isResponseSourceSyntax(String source) {
        return RESPONSE_BODY.equals(source) || BUSINESS_RESPONSE_BODY.equals(source) || WIRE_RESPONSE_BODY.equals(source) || isNamedSource(source, DECRYPTED_PREFIX);
    }

    private static boolean isNamedSource(String source, String prefix) {
        if (source == null || !source.startsWith(prefix)) return false;
        String name = source.substring(prefix.length());
        return name.matches("[A-Za-z][A-Za-z0-9_-]*");
    }

    public record ResponseDataSource(
            String name,
            String kind,
            String pathLanguage,
            Integer producerActionIndex,
            String producerActionName) {}
}
