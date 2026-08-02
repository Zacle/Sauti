package com.sauti.billing;

import com.sauti.billing.BillingDtos.BillingUsageResponse;
import com.sauti.billing.BillingDtos.BillingAccountResponse;
import com.sauti.billing.BillingDtos.CostTotalResponse;
import com.sauti.billing.BillingDtos.ReconciliationHealthResponse;
import com.sauti.billing.BillingDtos.UnpricedUsageResponse;
import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    private final TenantRepository tenantRepository;
    private final BillingLedgerService ledger;
    private final ProviderCostReconciliationRepository reconciliationJobs;

    public BillingService(TenantRepository tenantRepository, BillingLedgerService ledger,
                          ProviderCostReconciliationRepository reconciliationJobs) {
        this.tenantRepository = tenantRepository;
        this.ledger = ledger;
        this.reconciliationJobs = reconciliationJobs;
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
        var entries = ledger.currentCycle(tenantId);
        return BillingAccountResponse.from(
                account,
                ledger.balances(tenantId),
                costTotals(entries),
                unpricedUsage(entries),
                reconciliationHealth(tenantId),
                ledger.recent(tenantId)
        );
    }

    private java.util.List<CostTotalResponse> costTotals(java.util.List<CommunicationLedgerEntry> entries) {
        var totals = new LinkedHashMap<String, BigDecimal>();
        entries.stream()
                .filter(entry -> entry.getAmount() != null && entry.getCurrency() != null)
                .forEach(entry -> {
                    var key = entry.getCostBasis() + "|" + entry.getCurrency();
                    var signed = "credit".equals(entry.getDirection())
                            ? entry.getAmount().negate() : entry.getAmount();
                    totals.merge(key, signed, BigDecimal::add);
                });
        return totals.entrySet().stream()
                .map(entry -> {
                    var parts = entry.getKey().split("\\|", 2);
                    return new CostTotalResponse(parts[0], parts[1], entry.getValue());
                })
                .sorted(Comparator.comparing(CostTotalResponse::costBasis)
                        .thenComparing(CostTotalResponse::currency))
                .toList();
    }

    private java.util.List<UnpricedUsageResponse> unpricedUsage(java.util.List<CommunicationLedgerEntry> entries) {
        var totals = new LinkedHashMap<String, BigDecimal>();
        entries.stream()
                .filter(entry -> "unpriced".equals(entry.getCostBasis()))
                .forEach(entry -> {
                    var key = entry.getCategory() + "|" + entry.getUnit();
                    var signed = "credit".equals(entry.getDirection())
                            ? entry.getQuantity().negate() : entry.getQuantity();
                    totals.merge(key, signed, BigDecimal::add);
                });
        return totals.entrySet().stream()
                .filter(entry -> entry.getValue().signum() != 0)
                .map(entry -> {
                    var parts = entry.getKey().split("\\|", 2);
                    return new UnpricedUsageResponse(parts[0], parts[1], entry.getValue());
                })
                .sorted(Comparator.comparing(UnpricedUsageResponse::category)
                        .thenComparing(UnpricedUsageResponse::unit))
                .toList();
    }

    private ReconciliationHealthResponse reconciliationHealth(UUID tenantId) {
        Map<String, Long> counts = reconciliationJobs.findAllByTenantId(tenantId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ProviderCostReconciliationJob::getStatus,
                        java.util.stream.Collectors.counting()
                ));
        return new ReconciliationHealthResponse(
                counts.getOrDefault("pending", 0L),
                counts.getOrDefault("retrying", 0L),
                counts.getOrDefault("reconciled", 0L),
                counts.getOrDefault("estimated", 0L),
                counts.getOrDefault("unavailable", 0L)
        );
    }
}
