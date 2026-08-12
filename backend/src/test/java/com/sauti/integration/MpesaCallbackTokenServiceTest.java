package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MpesaCallbackTokenServiceTest {
    private final MpesaCallbackTokenService tokens = new MpesaCallbackTokenService(
            "0123456789abcdef0123456789abcdef"
    );

    @Test
    void tokenIsBoundToTheExactConnection() {
        var connectionId = UUID.randomUUID();
        var token = tokens.issue(connectionId);

        assertThat(tokens.isValid(connectionId, token)).isTrue();
        assertThat(tokens.isValid(UUID.randomUUID(), token)).isFalse();
        assertThat(tokens.isValid(connectionId, null)).isFalse();
    }
}
