package com.sauti.provisioning;

import com.sauti.billing.BillingLedgerService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PilotProvisioningPolicyService {
    private final PilotProvisioningPolicyRepository policies;
    private final BillingLedgerService ledger;
    public PilotProvisioningPolicyService(PilotProvisioningPolicyRepository policies, BillingLedgerService ledger) {
        this.policies = policies; this.ledger = ledger;
    }

    @Transactional
    public PilotProvisioningPolicy ensurePending(UUID tenantId) {
        return policies.findByTenantId(tenantId).orElseGet(() -> policies.save(new PilotProvisioningPolicy(tenantId)));
    }

    @Transactional(readOnly = true)
    public PilotProvisioningPolicy find(UUID tenantId) { return policies.findByTenantId(tenantId).orElse(null); }

    @Transactional
    public PilotProvisioningPolicy configure(UUID tenantId, String status, String currency, BigDecimal budget,
            boolean phoneNumbers, boolean liveCalling, boolean sms, boolean whatsapp, String notes, String actor) {
        var policy = ensurePending(tenantId);
        policy.configure(status, currency, budget, phoneNumbers, liveCalling, sms, whatsapp, notes,
                actor, OffsetDateTime.now(ZoneOffset.UTC));
        return policy;
    }

    @Transactional(readOnly = true)
    public void authorize(UUID tenantId, String capability) { authorize(tenantId, capability, BigDecimal.ZERO, null); }

    @Transactional(readOnly = true)
    public void authorize(UUID tenantId, String capability, BigDecimal estimatedAmount, String estimatedCurrency) {
        var policy = policies.findByTenantId(tenantId).orElse(null);
        if (policy == null) return; // Existing workspaces remain unchanged; invited pilots are always managed.
        if (!policy.permits(capability)) throw new IllegalStateException(label(capability) + " requires platform approval for this pilot workspace");
        if (policy.getMonthlyBudget().signum() <= 0) {
            throw new IllegalStateException("A positive monthly pilot budget is required for paid provider operations");
        }
        if (estimatedCurrency != null && !policy.getCurrency().equalsIgnoreCase(estimatedCurrency)) {
            throw new IllegalStateException("Provider charge currency does not match the approved pilot budget");
        }
        var spent = ledger.currentMonthDebitTotal(tenantId, policy.getCurrency());
        var estimate = estimatedAmount == null ? BigDecimal.ZERO : estimatedAmount.max(BigDecimal.ZERO);
        if (spent.add(estimate).compareTo(policy.getMonthlyBudget()) > 0) {
            throw new IllegalStateException("This operation exceeds the approved monthly pilot budget");
        }
    }

    private String label(String value) { return value.replace('_', ' '); }
}
