package com.sauti.billing;

import java.util.Optional;
import java.util.Collection;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BillingProviderEvidenceRepository extends JpaRepository<BillingProviderEvidence, UUID> {
    Optional<BillingProviderEvidence> findBySourceEventId(UUID sourceEventId);
    Optional<BillingProviderEvidence> findFirstByProviderAndRecordTypeAndProviderResourceIdOrderByOccurredAtDesc(
            String provider, String recordType, String providerResourceId);
    Optional<BillingProviderEvidence> findFirstByProviderAndTestModeAndProviderPlanIdAndEventNameOrderByOccurredAtDesc(
            String provider, boolean testMode, String providerPlanId, String eventName);
    Optional<BillingProviderEvidence> findFirstByProviderAndTestModeAndProviderPlanIdAndEventNameInOrderByOccurredAtDesc(
            String provider, boolean testMode, String providerPlanId, Collection<String> eventNames);
    long countByProviderAndTestMode(String provider, boolean testMode);
    Optional<BillingProviderEvidence> findFirstByProviderAndTestModeOrderByOccurredAtDesc(
            String provider, boolean testMode);
}
