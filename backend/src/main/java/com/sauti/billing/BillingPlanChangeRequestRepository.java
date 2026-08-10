package com.sauti.billing;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPlanChangeRequestRepository extends JpaRepository<BillingPlanChangeRequest, UUID> {
    Optional<BillingPlanChangeRequest> findByTenantId(UUID tenantId);
}
