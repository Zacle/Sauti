package com.sauti.whatsapp;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WhatsAppConversationRepository extends JpaRepository<WhatsAppConversation, UUID> {
    Optional<WhatsAppConversation> findByAgentIdAndCustomerNumber(UUID agentId, String customerNumber);
    Optional<WhatsAppConversation> findByIdAndTenantId(UUID id, UUID tenantId);
    List<WhatsAppConversation> findAllByTenantIdOrderByLastMessageAtDesc(UUID tenantId);
}

interface WhatsAppMessageRepository extends JpaRepository<WhatsAppMessage, UUID> {
    Optional<WhatsAppMessage> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByProviderMessageId(String providerMessageId);
    Optional<WhatsAppMessage> findByProviderMessageId(String providerMessageId);
    List<WhatsAppMessage> findAllByConversation_IdAndTenantIdOrderByCreatedAtAsc(UUID conversationId, UUID tenantId);
    Optional<WhatsAppMessage> findFirstByConversation_IdAndDirectionOrderByCreatedAtDesc(UUID conversationId, String direction);
}
