package com.sauti.billing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProviderCostReconciliationRepository extends JpaRepository<ProviderCostReconciliationJob, UUID> {
    Optional<ProviderCostReconciliationJob> findByTenantIdAndProviderAndResourceTypeAndProviderResourceId(
            UUID tenantId, String provider, String resourceType, String providerResourceId);
    Optional<ProviderCostReconciliationJob> findFirstByProviderAndResourceTypeAndProviderResourceId(
            String provider, String resourceType, String providerResourceId);
    List<ProviderCostReconciliationJob> findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            List<String> statuses, OffsetDateTime dueAt);
    List<ProviderCostReconciliationJob> findAllByTenantId(UUID tenantId);
    List<ProviderCostReconciliationJob> findAllByCreatedAtGreaterThanEqual(OffsetDateTime from);
    long countByStatus(String status);
    Optional<ProviderCostReconciliationJob> findFirstByStatusInOrderByCreatedAtAsc(List<String> statuses);
}
