package com.example.poc.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public class RequestSpec {
    private String method;
    private String baseUrl;
    private String path;
    private Map<String, String> headers = new LinkedHashMap<>();
    private Map<String, Object> query = new LinkedHashMap<>();
    private Object body;
    private RequestTransformSpec transform;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
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

    public RequestTransformSpec getTransform() {
        return transform;
    }

    public void setTransform(RequestTransformSpec transform) {
        this.transform = transform;
    }
}
