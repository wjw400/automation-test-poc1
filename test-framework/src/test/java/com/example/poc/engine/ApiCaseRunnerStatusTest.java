package com.example.poc.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.poc.engine.report.ApiCaseExecutionReport;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiCaseRunnerStatusTest {
    @Test
    void executesOneOfAndRangeStatusRules() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/accepted", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(202, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/range", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        try {
            String yaml = """
                    dslVersion: v1
                    name: HTTP status rules
                    variables:
                      baseUrl: http://127.0.0.1:%d
                    steps:
                      - name: Accepted response
                        type: http
                        operation:
                          method: GET
                          path: /accepted
                        request: {}
                        response:
                          status:
                            operator: oneOf
                            expected: [200, 202]
                      - name: Successful response range
                        type: http
                        operation:
                          method: GET
                          path: /range
                        request: {}
                        response:
                          status:
                            operator: range
                            min: 200
                            max: 299
                    """.formatted(server.getAddress().getPort());

            ApiCaseExecutionReport report = new ApiCaseRunner().executeInline("status-rules.yaml", yaml);

            assertThat(report.getStatus()).isEqualTo("PASSED");
            assertThat(report.getSteps()).hasSize(2);
            assertThat(report.getSteps()).allSatisfy(step ->
                    assertThat(step.getValidations()).singleElement().satisfies(validation ->
                            assertThat(validation.getStatus()).isEqualTo("PASSED")));
            assertThat(report.getSteps().get(0).getValidations().get(0).getExpected())
                    .isEqualTo(List.of(200, 202));
            assertThat(report.getSteps().get(1).getValidations().get(0).getExpected())
                    .isEqualTo(java.util.Map.of("min", 200, "max", 299));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsLegacyIntegerStatus() {
        String yaml = """
                dslVersion: v1
                name: Legacy status
                steps:
                  - name: Legacy response
                    type: http
                    operation:
                      method: GET
                      path: /legacy
                    request: {}
                    response:
                      status: 200
                """;

        assertThatThrownBy(() -> new ApiCaseRunner().parseInlineCase("legacy-status.yaml", yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse API case");
    }
}
