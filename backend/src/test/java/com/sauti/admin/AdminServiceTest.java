package com.sauti.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.calendar.BookingRepository;
import com.sauti.call.CallRepository;
import com.sauti.agent.AgentRepository;
import com.sauti.demo.DemoRequestRepository;
import com.sauti.demo.PilotInvitationService;
import com.sauti.demo.PilotInvitationRepository;
import com.sauti.tenant.TenantRepository;
import com.sauti.billing.PlatformCostInsightsService;
import com.sauti.integration.PlatformIntegrationHealthService;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import com.sauti.provisioning.PilotReadinessService;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;

class AdminServiceTest {
    @Test
    void aggregatesPlatformCountsWithoutTenantScopedEndpoints() {
        var tenants = mock(TenantRepository.class);
        var calls = mock(CallRepository.class);
        var bookings = mock(BookingRepository.class);
        var requests = mock(DemoRequestRepository.class);
        when(tenants.count()).thenReturn(4L);
        when(calls.count()).thenReturn(46L);
        when(calls.countDistinctCustomerNumbers()).thenReturn(19L);
        when(bookings.count()).thenReturn(12L);
        when(requests.countByStatus("new")).thenReturn(3L);
        when(requests.countByStatus("invited")).thenReturn(2L);
        when(requests.countByStatus("activated")).thenReturn(1L);

        var overview = new AdminService(tenants, calls, bookings, mock(AgentRepository.class),
                requests,
                mock(PilotInvitationService.class), mock(PlatformCostInsightsService.class),
                mock(PlatformIntegrationHealthService.class), mock(PilotInvitationRepository.class),
                mock(PlatformAdminAuditService.class), mock(PilotProvisioningPolicyService.class),
                mock(ApplicationEventPublisher.class), mock(PilotReadinessService.class)).overview();

        assertThat(overview.workspaces()).isEqualTo(4);
        assertThat(overview.customers()).isEqualTo(19);
        assertThat(overview.calls()).isEqualTo(46);
        assertThat(overview.newDemoRequests()).isEqualTo(3);
    }
}
