package com.sauti.shared;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyValidatorTest {
    @Test
    void acceptsExplicitSafeProductionConfiguration() {
        var validator = new ProductionSafetyValidator(safeEnvironment());

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsDevelopmentSecretsAndDisabledProviderValidation() {
        var environment = safeEnvironment()
                .withProperty("sauti.jwt.secret", "dev-only-change-me-dev-only-change-me")
                .withProperty("sauti.telnyx.validate-signature", "false");

        assertThatThrownBy(new ProductionSafetyValidator(environment)::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sauti.jwt.secret")
                .hasMessageContaining("sauti.telnyx.validate-signature");
    }

    private MockEnvironment safeEnvironment() {
        return new MockEnvironment()
                .withProperty("sauti.jwt.secret", "0123456789abcdef0123456789abcdef")
                .withProperty("sauti.web-voice.token-secret", "abcdef0123456789abcdef0123456789")
                .withProperty("sauti.tools.encryption-key", "12345678901234567890123456789012")
                .withProperty("sauti.webhooks.signing-secret", "0123456789abcdef01234567")
                .withProperty("sauti.providers.mode", "live")
                .withProperty("sauti.llm.provider", "spring-ai")
                .withProperty("sauti.telephony.provider", "telnyx")
                .withProperty("sauti.auth.expose-dev-tokens", "false")
                .withProperty("spring.h2.console.enabled", "false")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db/sauti")
                .withProperty("sauti.dashboard.base-url", "https://sauti.uk")
                .withProperty("sauti.web-voice.public-websocket-base-url", "wss://sauti.uk")
                .withProperty("sauti.cors.allowed-origins", "https://sauti.uk")
                .withProperty("sauti.telnyx.validate-signature", "true");
    }
}
