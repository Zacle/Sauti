package com.sauti.whatsapp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WhatsAppConversationRepository extends JpaRepository<WhatsAppConversation, UUID> {
    Optional<WhatsAppConversation> findByAgentIdAndCustomerNumber(UUID agentId, String customerNumber);
    Optional<WhatsAppConversation> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * One page of a workspace's open or closed conversations, newest first, after the cursor (the last
     * message time and id of the previous page's last row). {@code pattern} is a lower-case LIKE pattern,
     * or "%" for everything.
     */
    @Query("""
            select c from WhatsAppConversation c
            where c.tenantId = :tenantId
              and ((:closed = true and c.status = 'closed') or (:closed = false and c.status <> 'closed'))
              and (:attentionOnly = false or c.needsAttention = true)
              and (lower(coalesce(c.customerName, '')) like :pattern escape '!'
                   or c.customerNumber like :pattern escape '!'
                   or lower(coalesce(c.lastMessagePreview, '')) like :pattern escape '!')
              and (c.lastMessageAt < :cursorAt or (c.lastMessageAt = :cursorAt and c.id < :cursorId))
            order by c.lastMessageAt desc, c.id desc
            """)
    List<WhatsAppConversation> findInboxPage(@Param("tenantId") UUID tenantId, @Param("closed") boolean closed,
                                             @Param("attentionOnly") boolean attentionOnly,
                                             @Param("pattern") String pattern,
                                             @Param("cursorAt") OffsetDateTime cursorAt,
                                             @Param("cursorId") UUID cursorId, Pageable page);

    List<WhatsAppConversation> findByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(
            UUID tenantId, OffsetDateTime since, Pageable page);
    Optional<WhatsAppConversation> findFirstByPhoneNumberIdAndCustomerNumberOrderByLastMessageAtDesc(
            String phoneNumberId, String customerNumber);

    /** Conversations whose call record is open but whose customer has been quiet since the cutoff. */
    @Query("""
            select c from WhatsAppConversation c
            where c.sessionOpen = true
              and coalesce(c.lastCustomerMessageAt, c.lastMessageAt, c.createdAt) < :cutoff
            order by c.lastMessageAt asc
            """)
    List<WhatsAppConversation> findQuietOpenSessions(@Param("cutoff") OffsetDateTime cutoff, Pageable page);
}

interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, UUID> {
    Optional<WhatsAppMessage> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByProviderMessageId(String providerMessageId);
    Optional<WhatsAppMessage> findByProviderMessageId(String providerMessageId);
    List<WhatsAppMessage> findAllByConversation_IdAndTenantIdOrderByCreatedAtAsc(UUID conversationId, UUID tenantId);
    List<WhatsAppMessage> findAllByConversation_IdAndTenantIdAndUpdatedAtAfterOrderByCreatedAtAsc(
            UUID conversationId, UUID tenantId, OffsetDateTime since);
    Optional<WhatsAppMessage> findFirstByConversation_IdAndDirectionOrderByCreatedAtDesc(UUID conversationId, String direction);
    boolean existsByConversation_IdAndDirectionAndStatusNotAndCreatedAtAfter(
            UUID conversationId, String direction, String status, OffsetDateTime after);
}
