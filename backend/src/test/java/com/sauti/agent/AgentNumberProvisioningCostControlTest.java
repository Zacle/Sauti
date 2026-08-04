package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sauti.calendar.BookingRepository;
import com.sauti.billing.BillingLedgerService;
import com.sauti.call.CallRepository;
import com.sauti.outbound.ScheduledCallRepository;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import com.sauti.tool.AgentToolRepository;
import com.sauti.tool.DefaultToolSeeder;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import com.sauti.provisioning.PilotProvisioningPolicyService;

class AgentNumberProvisioningCostControlTest {
    private final AgentRepository agents = mock(AgentRepository.class);
    private final TelephonyProvider telephony = mock(TelephonyProvider.class);
    private final BillingLedgerService billing = mock(BillingLedgerService.class);
    private final AgentService service = new AgentService(
            agents,
            mock(TenantRepository.class),
            telephony,
            mock(DefaultToolSeeder.class),
            mock(AgentVariableService.class),
            mock(AgentToolRepository.class),
            mock(CallRepository.class),
            mock(BookingRepository.class),
            mock(ScheduledCallRepository.class),
            mock(KnowledgeBaseService.class),
            mock(ApplicationEventPublisher.class),
            billing,
            mock(PilotProvisioningPolicyService.class)
    );

    @Test
    void refusesProviderPurchaseWithoutExplicitChargeConfirmation() {
        assertThatThrownBy(() -> service.provisionNumber(
                UUID.randomUUID(), UUID.randomUUID(), "+254700000001", false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recurring phone-number charges");

        verifyNoInteractions(telephony);
    }

    @Test
    void provisionsTheSelectedNumberAfterExplicitChargeConfirmation() {
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var agent = new Agent(new Tenant("Clinic", "owner@example.com", "KE"),
                "Amina", "Hello", "Prompt");
        when(agents.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(agent));
        when(telephony.provisionPhoneNumber("KE", "+254700000001"))
                .thenReturn(new TelephonyProvider.PhoneNumberProvisioning(
                        "+254700000001", "telnyx", "order-1", "pending", true));
        when(telephony.quotePhoneNumber("KE", "+254700000001"))
                .thenReturn(new TelephonyProvider.PhoneNumberCostQuote(
                        "+254700000001", new java.math.BigDecimal("1.00"),
                        new java.math.BigDecimal("2.00"), "USD"));

        var result = service.provisionNumber(
                tenantId, agentId, "+254700000001", false, true);

        assertThat(result.getTwilioPhoneNumber()).isEqualTo("+254700000001");
        verify(telephony).provisionPhoneNumber("KE", "+254700000001");
        verify(billing).authorizePaidResource(tenantId, new java.math.BigDecimal("3.00"), "USD");
    }
}
