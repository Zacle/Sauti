package com.sauti.whatsapp;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppInboundMessageRepository extends JpaRepository<WhatsAppInboundMessage, UUID> {
    boolean existsByProviderMessageId(String providerMessageId);

    Optional<WhatsAppInboundMessage> findByProviderMessageId(String providerMessageId);
}
