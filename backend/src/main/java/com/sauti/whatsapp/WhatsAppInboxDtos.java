package com.sauti.whatsapp;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class WhatsAppInboxDtos {
    private WhatsAppInboxDtos() { }

    public record ConversationResponse(UUID id, UUID agentId, String customerNumber, String customerName,
                                       String mode, String status, int unreadCount,
                                       String lastMessagePreview, OffsetDateTime lastMessageAt) {
        static ConversationResponse from(WhatsAppConversation value) {
            return new ConversationResponse(value.getId(), value.getAgentId(), value.getCustomerNumber(),
                    value.getCustomerName(), value.getMode(), value.getStatus(), value.getUnreadCount(),
                    value.getLastMessagePreview(), value.getLastMessageAt());
        }
    }

    public record MessageResponse(UUID id, String providerMessageId, String direction, String type,
                                  String body, String mediaId, String mediaMimeType,
                                  String status, String failureReason, OffsetDateTime createdAt) {
        static MessageResponse from(WhatsAppMessage value) {
            return new MessageResponse(value.getId(), value.getProviderMessageId(), value.getDirection(),
                    value.getMessageType(), value.getBody(), value.getMediaId(), value.getMediaMimeType(),
                    value.getStatus(), value.getFailureReason(), value.getCreatedAt());
        }
    }

    public record SendMessageRequest(String text) { }
    public record AssignmentRequest(String mode) { }
}
