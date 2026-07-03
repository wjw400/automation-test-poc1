package com.example.poc.engine.report;

public class ValidationReport {
    private String type;
    private String target;
    private Object expected;
    private Object actual;
    private String status;
    private String message;

    public static ValidationReport passed(String type, String target, Object expected, Object actual) {
        ValidationReport report = new ValidationReport();
        report.setType(type);
        report.setTarget(target);
        report.setExpected(expected);
        report.setActual(actual);
        report.setStatus("PASSED");
        return report;
    }

    public static ValidationReport failed(String type, String target, Object expected, Object actual, String message) {
        ValidationReport report = passed(type, target, expected, actual);
        report.setStatus("FAILED");
        report.setMessage(message);
        return report;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Object getExpected() {
        return expected;
    }

    public void setExpected(Object expected) {
        this.expected = expected;
    }

    public Object getActual() {
        return actual;
    }

    public void setActual(Object actual) {
        this.actual = actual;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
