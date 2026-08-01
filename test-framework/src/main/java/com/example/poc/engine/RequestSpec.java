package com.example.poc.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public class RequestSpec {
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, Object> path = new LinkedHashMap<>();
    private Map<String, Object> query = new LinkedHashMap<>();
    private Object body;

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }

    public Map<String, Object> getPath() {
        return path;
    }

    public void setPath(Map<String, Object> path) {
        this.path = path == null ? new LinkedHashMap<>() : path;
    }

    public Map<String, Object> getQuery() {
        return query;
    }

    public void setQuery(Map<String, Object> query) {
        this.query = query == null ? new LinkedHashMap<>() : query;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

}
