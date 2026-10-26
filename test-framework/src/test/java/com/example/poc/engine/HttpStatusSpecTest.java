package com.example.poc.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class HttpStatusSpecTest {
    @Test
    void matchesEqualsOneOfAndRangeRules() {
        HttpStatusSpec equals = status(HttpStatusSpec.EQUALS, 201, null, null);
        HttpStatusSpec oneOf = status(HttpStatusSpec.ONE_OF, List.of(200, 202, 204), null, null);
        HttpStatusSpec range = status(HttpStatusSpec.RANGE, null, 200, 299);

        assertThat(equals.matches(201)).isTrue();
        assertThat(equals.matches(200)).isFalse();
        assertThat(oneOf.matches(202)).isTrue();
        assertThat(oneOf.matches(201)).isFalse();
        assertThat(range.matches(299)).isTrue();
        assertThat(range.matches(300)).isFalse();
    }

    @Test
    void rejectsMalformedOrAmbiguousRules() {
        assertThatThrownBy(() -> status(HttpStatusSpec.ONE_OF, List.of(), null, null).validate())
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> status(HttpStatusSpec.ONE_OF, List.of(200, 200), null, null).validate())
                .hasMessageContaining("duplicate");
        assertThatThrownBy(() -> status(HttpStatusSpec.RANGE, null, 300, 200).validate())
                .hasMessageContaining("must not exceed");
        assertThatThrownBy(() -> status(HttpStatusSpec.EQUALS, 200, 200, 299).validate())
                .hasMessageContaining("only supported by range");
    }

    @Test
    void rejectsProhibitedFieldsEvenWhenTheyAreExplicitlyNull() {
        HttpStatusSpec equals = status(HttpStatusSpec.EQUALS, 200, null, null);
        equals.setMin(null);
        HttpStatusSpec range = status(HttpStatusSpec.RANGE, null, 200, 299);
        range.setExpected(null);

        assertThatThrownBy(equals::validate).hasMessageContaining("only supported by range");
        assertThatThrownBy(range::validate).hasMessageContaining("expected is not supported");
    }

    private HttpStatusSpec status(String operator, Object expected, Integer min, Integer max) {
        HttpStatusSpec status = new HttpStatusSpec();
        status.setOperator(operator);
        if (expected != null) status.setExpected(expected);
        if (min != null) status.setMin(min);
        if (max != null) status.setMax(max);
        return status;
    }
}
