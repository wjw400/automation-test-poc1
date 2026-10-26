package com.example.poc.engine;

public class ValidationSpec {
    private String source = CaseDslDataSources.RESPONSE_BODY;
    private String path;
    private String operator = "equals";
    private Object expected;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Object getExpected() {
        return expected;
    }

    public void setExpected(Object expected) {
        this.expected = expected;
    }
}
