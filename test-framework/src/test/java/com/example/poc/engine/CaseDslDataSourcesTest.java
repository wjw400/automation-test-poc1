package com.example.poc.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CaseDslDataSourcesTest {
    @Test
    void exposesResponseBodyAndEnabledDecryptedOutputsInActionOrder() {
        ApiStep step = new ApiStep();
        step.getActions().getAfterReceive().add(decryptAction("Read order", "decrypted.order", true));
        step.getActions().getAfterReceive().add(decryptAction("Disabled", "decrypted.disabled", false));
        step.getActions().getAfterReceive().add(decryptAction("Duplicate", "decrypted.order", true));

        assertThat(CaseDslDataSources.responseSources(step))
                .extracting(CaseDslDataSources.ResponseDataSource::name)
                .containsExactly("response.body", "business.response.body", "wire.response.body", "decrypted.order");
        assertThat(CaseDslDataSources.responseSources(step).get(3).producerActionIndex()).isZero();
    }

    @Test
    void acceptsOnlyCaseDslV1ResponseSourceSyntax() {
        assertThat(CaseDslDataSources.isResponseSourceSyntax("response.body")).isTrue();
        assertThat(CaseDslDataSources.isResponseSourceSyntax("decrypted.securePayload")).isTrue();
        assertThat(CaseDslDataSources.isResponseSourceSyntax("raw")).isFalse();
        assertThat(CaseDslDataSources.isResponseSourceSyntax("decrypted.")).isFalse();
    }

    private CaseActionSpec decryptAction(String name, String target, boolean enabled) {
        CaseActionSpec action = new CaseActionSpec();
        action.setType(CaseActionExecutor.DECRYPT_AND_VERIFY);
        action.setName(name);
        action.setEnabled(enabled);
        action.getOutput().setTarget(target);
        return action;
    }
}
