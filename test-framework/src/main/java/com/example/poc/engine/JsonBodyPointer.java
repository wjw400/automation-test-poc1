package com.example.poc.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact JSON Pointer addressing, without JSONPath expressions or implicit field guessing. */
public final class JsonBodyPointer {
    private JsonBodyPointer() {}

    public static List<String> tokens(String pointer) {
        if (pointer == null || !pointer.startsWith("/")) {
            throw new IllegalArgumentException("Ciphertext pointer must start with / (for example /data/encryptdata)");
        }
        List<String> result = new ArrayList<>();
        for (String token : pointer.substring(1).split("/", -1)) {
            if (token.matches(".*~(?:[^01]|$).*")) throw new IllegalArgumentException("Invalid JSON Pointer escape: " + pointer);
            result.add(token.replace("~1", "/").replace("~0", "~"));
        }
        return result;
    }

    public static Object read(Object body, String pointer) {
        Object value = body;
        for (String token : tokens(pointer)) {
            if (value instanceof Map<?, ?> map && map.containsKey(token)) value = map.get(token);
            else if (value instanceof List<?> list && token.matches("0|[1-9][0-9]*")) {
                try { value = list.get(Integer.parseInt(token)); }
                catch (IndexOutOfBoundsException | NumberFormatException error) {
                    throw new IllegalArgumentException("Ciphertext pointer does not exist: " + pointer);
                }
            } else throw new IllegalArgumentException("Ciphertext pointer does not exist: " + pointer);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Object write(Object body, String pointer, Object value) {
        List<String> tokens = tokens(pointer);
        Object current = body;
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            boolean last = index == tokens.size() - 1;
            if (current instanceof Map<?, ?> raw) {
                Map<String, Object> map = (Map<String, Object>) raw;
                if (last) map.put(token, value);
                else {
                    if (!map.containsKey(token)) map.put(token, new LinkedHashMap<String, Object>());
                    current = map.get(token);
                }
            } else if (current instanceof List<?> raw && token.matches("0|[1-9][0-9]*")) {
                List<Object> list = (List<Object>) raw;
                try {
                    int position = Integer.parseInt(token);
                    if (last) list.set(position, value);
                    else current = list.get(position);
                } catch (IndexOutOfBoundsException | NumberFormatException error) {
                    throw new IllegalArgumentException("Ciphertext array position does not exist: " + pointer);
                }
            } else throw new IllegalArgumentException("Ciphertext pointer crosses a non-container value: " + pointer);
        }
        return body;
    }
}
