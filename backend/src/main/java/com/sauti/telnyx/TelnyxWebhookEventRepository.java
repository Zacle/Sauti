package com.sauti.telnyx;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelnyxWebhookEventRepository extends JpaRepository<TelnyxWebhookEvent, UUID> {
    boolean existsByProviderEventId(String providerEventId);
    Optional<TelnyxWebhookEvent> findByProviderEventId(String providerEventId);
}
