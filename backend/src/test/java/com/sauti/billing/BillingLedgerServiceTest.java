package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingLedgerServiceTest {
    private final BillingAccountRepository accounts = mock(BillingAccountRepository.class);
    private final CommunicationLedgerRepository ledger = mock(CommunicationLedgerRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final BillingLedgerService service = new BillingLedgerService(
            accounts, ledger, tenants, new ObjectMapper());

    @Test
    void createsObserveModeAccountForAnExistingWorkspace() {
        var tenantId = UUID.randomUUID();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Clinic", "owner@example.com", "KE")));
        when(accounts.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(accounts.saveAndFlush(any(BillingAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var account = service.account(tenantId);

        assertThat(account.getStatus()).isEqualTo("preview");
        assertThat(account.getEnforcementMode()).isEqualTo("observe");
    }

    @Test
    void returnsExistingEntryForRepeatedIdempotencyKey() {
        var tenantId = UUID.randomUUID();
        var existing = mock(CommunicationLedgerEntry.class);
        when(ledger.findByTenantIdAndIdempotencyKey(tenantId, "sms:message-1"))
                .thenReturn(Optional.of(existing));

        var result = service.recordDebit(
                tenantId, "sms", BigDecimal.ONE, "message", new BigDecimal("0.01"), "USD",
                "sms:message-1", "message-1", "SMS", Map.of());

        assertThat(result).isSameAs(existing);
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void enforcedAccountRejectsPurchaseWithoutEnoughBalance() {
        var tenantId = UUID.randomUUID();
        var account = new BillingAccount(tenantId);
        account.configure("active", "enforce", "USD", null, BigDecimal.ZERO);
        when(accounts.findByTenantIdForUpdate(tenantId)).thenReturn(Optional.of(account));
        when(ledger.findAllByTenantId(tenantId)).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> service.authorizePaidResource(tenantId, new BigDecimal("2.00"), "USD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient communication balance");
    }
}
