package com.example.poc.engine.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportResponse {
    private int statusCode;
    private Map<String, List<String>> headers = new LinkedHashMap<>();
    private String body;

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
