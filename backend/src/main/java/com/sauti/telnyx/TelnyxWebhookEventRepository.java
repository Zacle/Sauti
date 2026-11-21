package com.sauti.telnyx;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelnyxWebhookEventRepository extends JpaRepository<TelnyxWebhookEvent, UUID> {
    boolean existsByProviderEventId(String providerEventId);
    Optional<TelnyxWebhookEvent> findByProviderEventId(String providerEventId);

    /** Unfinished events whose time to be looked at again has come, oldest first. */
    List<TelnyxWebhookEvent> findTop100ByNextAttemptAtBeforeOrderByNextAttemptAtAsc(OffsetDateTime now);
}
