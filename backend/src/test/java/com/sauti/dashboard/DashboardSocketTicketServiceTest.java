package com.sauti.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sauti.auth.AuthenticatedUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardSocketTicketServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void issuesPurposeBoundTenantTicket() {
        var service = new DashboardSocketTicketService("sauti", SECRET);
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "owner@example.com", "OWNER");

        var principal = service.verify(service.issue(user).ticket());

        assertThat(principal.userId()).isEqualTo(user.userId());
        assertThat(principal.tenantId()).isEqualTo(user.tenantId());
    }

    @Test
    void rejectsTicketFromAnotherIssuer() {
        var first = new DashboardSocketTicketService("first", SECRET);
        var second = new DashboardSocketTicketService("second", SECRET);
        var user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "owner@example.com", "OWNER");

        assertThatThrownBy(() -> second.verify(first.issue(user).ticket())).isInstanceOf(RuntimeException.class);
    }
}
