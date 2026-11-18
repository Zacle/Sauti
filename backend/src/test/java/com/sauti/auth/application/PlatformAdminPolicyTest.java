package com.sauti.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlatformAdminPolicyTest {
    @Test
    void allowsOnlyConfiguredEmailsIgnoringCaseAndSpaces() {
        var policy = new PlatformAdminPolicy(" admin@sauti.uk,ops@sauti.uk ");

        assertThat(policy.allows("ADMIN@SAUTI.UK")).isTrue();
        assertThat(policy.allows("ops@sauti.uk")).isTrue();
        assertThat(policy.allows("owner@example.com")).isFalse();
        assertThat(policy.allows(null)).isFalse();
    }
}
