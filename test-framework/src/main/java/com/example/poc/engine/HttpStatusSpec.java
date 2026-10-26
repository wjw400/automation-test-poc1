package com.example.poc.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpStatusSpec {
    public static final String EQUALS = "equals";
    public static final String ONE_OF = "oneOf";
    public static final String RANGE = "range";

    private String operator;
    private Object expected;
    private Integer min;
    private Integer max;
    private boolean expectedDefined;
    private boolean minDefined;
    private boolean maxDefined;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public Object getExpected() { return expected; }
    public void setExpected(Object expected) {
        this.expected = expected;
        this.expectedDefined = true;
    }
    public Integer getMin() { return min; }
    public void setMin(Integer min) {
        this.min = min;
        this.minDefined = true;
    }
    public Integer getMax() { return max; }
    public void setMax(Integer max) {
        this.max = max;
        this.maxDefined = true;
    }

    public void validate() {
        if (EQUALS.equals(operator)) {
            requireNoRangeFields();
            statusCode(expected, "response.status.expected");
            return;
        }
        if (ONE_OF.equals(operator)) {
            requireNoRangeFields();
            List<Integer> statuses = expectedStatuses();
            if (statuses.isEmpty()) {
                throw new IllegalArgumentException("response.status.expected must contain at least one status code");
            }
            if (statuses.stream().distinct().count() != statuses.size()) {
                throw new IllegalArgumentException("response.status.expected must not contain duplicate status codes");
            }
            return;
        }
        if (RANGE.equals(operator)) {
            if (expectedDefined) {
                throw new IllegalArgumentException("response.status.expected is not supported by range");
            }
            int lower = statusCode(min, "response.status.min");
            int upper = statusCode(max, "response.status.max");
            if (lower > upper) {
                throw new IllegalArgumentException("response.status.min must not exceed response.status.max");
            }
            return;
        }
        throw new IllegalArgumentException("response.status.operator must be equals, oneOf, or range");
    }

    public boolean matches(int actualStatus) {
        validate();
        return switch (operator) {
            case EQUALS -> actualStatus == statusCode(expected, "response.status.expected");
            case ONE_OF -> expectedStatuses().contains(actualStatus);
            case RANGE -> actualStatus >= min && actualStatus <= max;
            default -> false;
        };
    }

    public Object reportExpected() {
        validate();
        if (EQUALS.equals(operator)) return statusCode(expected, "response.status.expected");
        if (ONE_OF.equals(operator)) return expectedStatuses();
        Map<String, Integer> range = new LinkedHashMap<>();
        range.put("min", min);
        range.put("max", max);
        return range;
    }

    public String expectationDescription() {
        validate();
        if (EQUALS.equals(operator)) return String.valueOf(reportExpected());
        if (ONE_OF.equals(operator)) return "one of " + reportExpected();
        return min + " through " + max;
    }

    private List<Integer> expectedStatuses() {
        if (!(expected instanceof List<?> values)) {
            throw new IllegalArgumentException("response.status.expected must be an array for oneOf");
        }
        List<Integer> statuses = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            statuses.add(statusCode(values.get(index), "response.status.expected[" + index + "]"));
        }
        return List.copyOf(statuses);
    }

    private int statusCode(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be an integer from 100 to 599");
        }
        double decimal = number.doubleValue();
        int status = number.intValue();
        if (!Double.isFinite(decimal) || decimal != status || status < 100 || status > 599) {
            throw new IllegalArgumentException(field + " must be an integer from 100 to 599");
        }
        return status;
    }

    private void requireNoRangeFields() {
        if (minDefined || maxDefined) {
            throw new IllegalArgumentException("response.status.min/max are only supported by range");
        }
    }
}
