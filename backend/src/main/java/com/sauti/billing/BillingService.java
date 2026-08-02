package com.sauti.billing;

import com.sauti.billing.BillingDtos.BillingUsageResponse;
import com.sauti.billing.BillingDtos.BillingAccountResponse;
import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    private final TenantRepository tenantRepository;
    private final BillingLedgerService ledger;

    public BillingService(TenantRepository tenantRepository, BillingLedgerService ledger) {
        this.tenantRepository = tenantRepository;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public BillingUsageResponse usage(UUID tenantId) {
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        return BillingUsageResponse.from(tenant);
    }

    @Transactional
    public BillingAccountResponse account(UUID tenantId) {
        var account = ledger.account(tenantId);
        return BillingAccountResponse.from(account, ledger.balances(tenantId), ledger.recent(tenantId));
    }
}
