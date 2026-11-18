package com.sauti.auth.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    @Test
    void acceptsALongEnoughUncommonPassword() {
        assertThatCode(() -> PasswordPolicy.validate("river-mango-42", "owner@example.com")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortCommonRepeatedAndEmailPasswords() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short", "owner@example.com"))
                .hasMessageContaining("at least 8");
        assertThatThrownBy(() -> PasswordPolicy.validate("Password123", "owner@example.com"))
                .hasMessageContaining("too easy");
        assertThatThrownBy(() -> PasswordPolicy.validate("aaaaaaaaaa", "owner@example.com"))
                .hasMessageContaining("too easy");
        assertThatThrownBy(() -> PasswordPolicy.validate("Owner@Example.com", "owner@example.com"))
                .hasMessageContaining("email");
        assertThatThrownBy(() -> PasswordPolicy.validate("ownerfirst", "ownerfirst@example.com"))
                .hasMessageContaining("email");
    }
}
