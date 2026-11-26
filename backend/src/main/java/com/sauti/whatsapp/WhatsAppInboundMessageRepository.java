package com.sauti.whatsapp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppInboundMessageRepository extends JpaRepository<WhatsAppInboundMessage, UUID> {
    boolean existsByProviderMessageId(String providerMessageId);

    Optional<WhatsAppInboundMessage> findByProviderMessageId(String providerMessageId);

    /**
     * Messages to process again: those left received, queued or processing by a restart, and failures with tries
     * left. Oldest first.
     */
    @Query("""
            select m from WhatsAppInboundMessage m
            where m.attempts < :maxAttempts
              and ((m.status in ('received', 'queued', 'processing') and m.updatedAt < :stuckBefore)
                or (m.status = 'failed' and m.updatedAt < :failedBefore))
            order by m.createdAt asc
            """)
    List<WhatsAppInboundMessage> findRetryable(@Param("stuckBefore") OffsetDateTime stuckBefore,
                                               @Param("failedBefore") OffsetDateTime failedBefore,
                                               @Param("maxAttempts") int maxAttempts,
                                               Pageable page);
}
