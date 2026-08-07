package com.sauti.provisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PilotReadinessReviewRepository extends JpaRepository<PilotReadinessReview, UUID> {
    Optional<PilotReadinessReview> findByTenantId(UUID tenantId);
}

