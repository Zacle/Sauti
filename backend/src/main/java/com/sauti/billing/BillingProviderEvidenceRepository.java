package com.sauti.billing;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BillingProviderEvidenceRepository extends JpaRepository<BillingProviderEvidence, UUID> {
    Optional<BillingProviderEvidence> findBySourceEventId(UUID sourceEventId);
    Optional<BillingProviderEvidence> findFirstByProviderAndRecordTypeAndProviderResourceIdOrderByOccurredAtDesc(
            String provider, String recordType, String providerResourceId);
    List<BillingProviderEvidence> findAllByProviderAndTestModeOrderByOccurredAtAsc(
            String provider, boolean testMode);
}
