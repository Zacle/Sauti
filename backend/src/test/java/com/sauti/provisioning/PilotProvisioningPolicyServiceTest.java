package com.sauti.provisioning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.billing.BillingLedgerService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PilotProvisioningPolicyServiceTest {
    private final PilotProvisioningPolicyRepository policies = mock(PilotProvisioningPolicyRepository.class);
    private final BillingLedgerService ledger = mock(BillingLedgerService.class);
    private final PilotProvisioningPolicyService service = new PilotProvisioningPolicyService(policies, ledger);

    @Test
    void blocksManagedPilotsUntilCapabilityIsExplicitlyApproved() {
        var tenantId = UUID.randomUUID();
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(new PilotProvisioningPolicy(tenantId)));
        assertThatThrownBy(() -> service.authorize(tenantId, "sms"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("platform approval");
    }

    @Test
    void enforcesTheApprovedMonthlyBudgetBeforeProviderUse() {
        var tenantId = UUID.randomUUID();
        var policy = new PilotProvisioningPolicy(tenantId);
        policy.configure("approved", "USD", new BigDecimal("10.00"), true, true, true, true,
                null, "admin@sauti.uk", java.time.OffsetDateTime.now());
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(policy));
        when(ledger.currentMonthDebitTotal(tenantId, "USD")).thenReturn(new BigDecimal("8.00"));

        service.authorize(tenantId, "phone_numbers", new BigDecimal("2.00"), "USD");
        assertThatThrownBy(() -> service.authorize(tenantId, "phone_numbers", new BigDecimal("2.01"), "USD"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("monthly pilot budget");
    }

    @Test
    void zeroBudgetNeverAuthorizesPaidTrafficEvenWhenACapabilityIsChecked() {
        var tenantId = UUID.randomUUID();
        var policy = new PilotProvisioningPolicy(tenantId);
        policy.configure("approved", "USD", BigDecimal.ZERO, true, true, true, true,
                null, "admin@sauti.uk", java.time.OffsetDateTime.now());
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.of(policy));
        assertThatThrownBy(() -> service.authorize(tenantId, "sms"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("positive monthly pilot budget");
    }

    @Test
    void leavesLegacyUnmanagedWorkspacesUntouched() {
        var tenantId = UUID.randomUUID();
        when(policies.findByTenantId(tenantId)).thenReturn(Optional.empty());
        service.authorize(tenantId, "live_calling");
    }
}
