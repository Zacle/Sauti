package com.sauti.billing;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingUsageAlertRepository extends JpaRepository<BillingUsageAlert, UUID> {
    boolean existsByTenantIdAndKindAndCycleStartsAtAndIncludedAmountAndThresholdPercent(
            UUID tenantId, String kind, OffsetDateTime cycleStartsAt, int includedAmount, int thresholdPercent);

    List<BillingUsageAlert> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            Collection<String> statuses, OffsetDateTime now);

    List<BillingUsageAlert> findAllByTenantIdAndKindOrderByThresholdPercent(UUID tenantId, String kind);
}
