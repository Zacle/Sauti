package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProviderOAuthScopeTest {
    private static final String SHEETS = "https://www.googleapis.com/auth/spreadsheets";

    @Test
    void acceptsTheRequiredSheetsScope() {
        assertThatCode(() -> ProviderOAuthService.requireGrantedScopes(SHEETS, SHEETS))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnEmptyGoogleGrant() {
        assertThatThrownBy(() -> ProviderOAuthService.requireGrantedScopes(SHEETS, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required permission");
    }
}
