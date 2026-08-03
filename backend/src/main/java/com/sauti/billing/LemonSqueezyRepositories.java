package com.sauti.billing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BillingSubscriptionRepository extends JpaRepository<BillingSubscription, UUID> {
    Optional<BillingSubscription> findByTenantId(UUID tenantId);
    Optional<BillingSubscription> findByProviderAndProviderSubscriptionId(
            String provider, String providerSubscriptionId);
}

interface BillingProviderEventRepository extends JpaRepository<BillingProviderEvent, UUID> {
    Optional<BillingProviderEvent> findByProviderAndPayloadHash(String provider, String payloadHash);
    List<BillingProviderEvent> findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            String provider, List<String> statuses, OffsetDateTime dueAt);
}
