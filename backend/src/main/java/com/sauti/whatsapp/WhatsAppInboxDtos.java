package com.sauti.whatsapp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WhatsAppInboxDtos {
    private WhatsAppInboxDtos() { }

    public record ConversationResponse(UUID id, UUID agentId, String customerNumber, String customerName,
                                       String mode, String status, int unreadCount,
                                       String lastMessagePreview, OffsetDateTime lastMessageAt,
                                       OffsetDateTime lastCustomerMessageAt, OffsetDateTime replyWindowEndsAt,
                                       boolean needsAttention, String attentionReason,
                                       OffsetDateTime updatedAt) {
        static ConversationResponse from(WhatsAppConversation value) {
            return new ConversationResponse(value.getId(), value.getAgentId(), value.getCustomerNumber(),
                    value.getCustomerName(), value.getMode(), value.getStatus(), value.getUnreadCount(),
                    value.getLastMessagePreview(), value.getLastMessageAt(), value.getLastCustomerMessageAt(),
                    value.replyWindowEndsAt(), value.isNeedsAttention(), value.getAttentionReason(),
                    value.getUpdatedAt());
        }
    }

    public record MessageResponse(UUID id, String providerMessageId, String direction, String type,
                                  String body, String mediaId, String mediaMimeType,
                                  String status, String failureReason, OffsetDateTime createdAt,
                                  String author, String sentBy, String templateName,
                                  OffsetDateTime updatedAt) {
        static MessageResponse from(WhatsAppMessage value) {
            return new MessageResponse(value.getId(), value.getProviderMessageId(), value.getDirection(),
                    value.getMessageType(), value.getBody(), value.getMediaId(), value.getMediaMimeType(),
                    value.getStatus(), value.getFailureReason(), value.getCreatedAt(),
                    value.getAuthor(), value.getSentBy(), value.getTemplateName(), value.getUpdatedAt());
        }
    }

    /** One page of the inbox, newest first; pass nextCursor back to get the next page. */
    public record ConversationPage(List<ConversationResponse> items, String nextCursor) { }

    /**
     * Conversations changed since the time asked for, in any view. Ask again with {@code since} set to
     * {@code next}, which overlaps a little so a change that was still being saved is not missed.
     */
    public record ConversationChanges(List<ConversationResponse> items, OffsetDateTime next) { }

    /** An approved template the team can send once the 24-hour window has closed. */
    public record TemplateResponse(String name, String language, String category, String headerText,
                                   String bodyText, List<TemplateField> fields) { }

    /** A value the template needs, such as {{1}} or {{first_name}}, in the header or the body. */
    public record TemplateField(String key, String component, String name, String placeholder) { }

    public record SendTemplateRequest(String name, String language, Map<String, String> values) { }

    public record SendMessageRequest(String text) { }
    public record AssignmentRequest(String mode) { }
}
