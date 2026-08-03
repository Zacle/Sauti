package com.sauti.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class PlatformAdminPolicyTest {
    @Test
    void grantsPlatformRoleOnlyToConfiguredEmails() {
        var policy = new PlatformAdminPolicy(" admin@sauti.uk,ops@sauti.uk ");
        var user = mock(User.class);
        when(user.getEmail()).thenReturn("ADMIN@SAUTI.UK");
        when(user.getRole()).thenReturn("OWNER");

        assertThat(policy.roleFor(user)).isEqualTo("PLATFORM_ADMIN");

        when(user.getEmail()).thenReturn("owner@example.com");
        assertThat(policy.roleFor(user)).isEqualTo("OWNER");
    }
}
