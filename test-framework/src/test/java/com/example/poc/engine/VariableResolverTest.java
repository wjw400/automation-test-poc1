package com.example.poc.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class VariableResolverTest {
    private final VariableResolver resolver = new VariableResolver();

    @Test
    void resolvesCaseDslVariablesInsideStructuredBodies() {
        TestContext context = new TestContext();
        context.put("productId", "P102");

        Object resolved = resolver.resolveObject(
                Map.of("productId", "${productId}", "quantity", 2),
                context);

        assertThat(resolved).isEqualTo(Map.of("productId", "P102", "quantity", 2));
    }

    @Test
    void rejectsPostmanStylePlaceholdersInsteadOfSendingThemLiterally() {
        TestContext context = new TestContext();
        context.put("productId", "P102");

        assertThatThrownBy(() -> resolver.resolveObject(
                Map.of("productId", "{{list-products.productId}}"),
                context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Postman-style placeholders")
                .hasMessageContaining("${variableName}");
    }
}
