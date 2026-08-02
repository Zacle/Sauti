package com.sauti.whatsapp;

import com.sauti.billing.CommunicationUsageMeteringService;
import com.sauti.call.Call;
import com.sauti.integration.IntegrationService;
import com.sauti.whatsapp.WhatsAppInboxDtos.ConversationResponse;
import com.sauti.whatsapp.WhatsAppInboxDtos.MessageResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppInboxService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppInboxService.class);
    private final WhatsAppConversationRepository conversations;
    private final WhatsAppMessageRepository messages;
    private final IntegrationService integrations;
    private final WhatsAppMessageSender sender;
    private final CommunicationUsageMeteringService usageMetering;

    public WhatsAppInboxService(WhatsAppConversationRepository conversations,
                                WhatsAppMessageRepository messages,
                                IntegrationService integrations,
                                WhatsAppMessageSender sender,
                                CommunicationUsageMeteringService usageMetering) {
        this.conversations = conversations;
        this.messages = messages;
        this.integrations = integrations;
        this.sender = sender;
        this.usageMetering = usageMetering;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(UUID tenantId) {
        return conversations.findAllByTenantIdOrderByLastMessageAtDesc(tenantId).stream()
                .map(ConversationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> messages(UUID tenantId, UUID conversationId) {
        requireConversation(tenantId, conversationId);
        return messages.findAllByConversation_IdAndTenantIdOrderByCreatedAtAsc(conversationId, tenantId)
                .stream().map(MessageResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MediaContent media(UUID tenantId, UUID messageId) {
        var message = messages.findByIdAndTenantId(messageId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("WhatsApp message not found"));
        if (message.getMediaId() == null) throw new IllegalArgumentException("This message has no downloadable media");
        var media = sender.downloadMedia(message.getMediaId(), token(message.getConversation()));
        return new MediaContent(media.bytes(), media.contentType(), downloadName(message));
    }

    @Transactional
    public InboundResult recordInbound(Call call, String providerMessageId, String customerName,
                                       String type, String body, String mediaId, String mediaMimeType) {
        if (messages.existsByProviderMessageId(providerMessageId)) {
            var existing = messages.findByProviderMessageId(providerMessageId).orElseThrow();
            return new InboundResult(existing.getConversation().getId(), existing.getConversation().getMode(), false);
        }
        var conversation = conversations.findByAgentIdAndCustomerNumber(
                        call.getAgent().getId(), call.getCallerNumber())
                .orElseGet(() -> new WhatsAppConversation(
                        call.getTenant().getId(), call.getAgent().getId(),
                        call.getAgent().getWhatsappPhoneNumberId(), call.getCallerNumber(), customerName));
        var preview = preview(type, body);
        conversation.receive(customerName, preview, OffsetDateTime.now());
        conversation = conversations.save(conversation);
        messages.save(new WhatsAppMessage(conversation, providerMessageId, "inbound", type,
                body, mediaId, mediaMimeType, "received"));
        return new InboundResult(conversation.getId(), conversation.getMode(), true);
    }

    public MessageResponse sendAiText(Call call, UUID conversationId, String text) {
        return send(call.getTenant().getId(), conversationId, text, "text", null, true);
    }

    public MessageResponse sendAiVoice(Call call, UUID conversationId, String text, byte[] oggOpus) {
        var conversation = requireConversation(call.getTenant().getId(), conversationId);
        var pending = messages.save(new WhatsAppMessage(conversation, null, "outbound", "audio",
                text, null, "audio/ogg", "sending"));
        try {
            var result = sender.sendVoiceNoteTracked(conversation.getPhoneNumberId(),
                    conversation.getCustomerNumber(), oggOpus, token(conversation));
            pending.providerAccepted(result.providerMessageId());
            usageMetering.meterOutboundMessage(
                    call.getTenant().getId(), call.getAgent().getId(), "whatsapp",
                    result.providerMessageId(), pending.getId().toString(), "audio");
            conversation.sent("Voice reply", OffsetDateTime.now());
            markProviderRead(conversation);
            return MessageResponse.from(messages.save(pending));
        } catch (RuntimeException exception) {
            pending.status("failed", safeFailure(exception));
            messages.save(pending);
            throw exception;
        }
    }

    public MessageResponse sendHuman(UUID tenantId, UUID conversationId, String text) {
        var conversation = requireConversation(tenantId, conversationId);
        if (!"human".equals(conversation.getMode())) {
            throw new IllegalStateException("Take over this conversation before sending a human reply");
        }
        var latestInbound = messages.findFirstByConversation_IdAndDirectionOrderByCreatedAtDesc(
                conversationId, "inbound").orElse(null);
        if (latestInbound == null || latestInbound.getCreatedAt() == null
                || latestInbound.getCreatedAt().plusHours(24).isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException(
                    "The 24-hour WhatsApp reply window has closed. Send an approved template instead.");
        }
        return send(tenantId, conversationId, text, "text", null, false);
    }

    private MessageResponse send(UUID tenantId, UUID conversationId, String text,
                                 String type, byte[] audio, boolean markRead) {
        var conversation = requireConversation(tenantId, conversationId);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Message text is required");
        var pending = messages.save(new WhatsAppMessage(conversation, null, "outbound", type,
                text.trim(), null, null, "sending"));
        try {
            var result = sender.sendTextTracked(conversation.getPhoneNumberId(),
                    conversation.getCustomerNumber(), text.trim(), token(conversation));
            pending.providerAccepted(result.providerMessageId());
            usageMetering.meterOutboundMessage(
                    tenantId, conversation.getAgentId(), "whatsapp", result.providerMessageId(),
                    pending.getId().toString(), type);
            conversation.sent(text, OffsetDateTime.now());
            if (markRead) markProviderRead(conversation);
            return MessageResponse.from(messages.save(pending));
        } catch (RuntimeException exception) {
            pending.status("failed", safeFailure(exception));
            messages.save(pending);
            throw exception;
        }
    }

    @Transactional
    public ConversationResponse assign(UUID tenantId, UUID conversationId, String mode) {
        var conversation = requireConversation(tenantId, conversationId);
        conversation.assign(mode == null ? "" : mode.trim().toLowerCase(java.util.Locale.ROOT));
        if ("human".equals(conversation.getMode())) markProviderRead(conversation);
        return ConversationResponse.from(conversations.save(conversation));
    }

    @Transactional
    public ConversationResponse markRead(UUID tenantId, UUID conversationId) {
        var conversation = requireConversation(tenantId, conversationId);
        markProviderRead(conversation);
        return ConversationResponse.from(conversations.save(conversation));
    }

    @Transactional
    public void providerStatus(String providerMessageId, String status, String failureReason) {
        if (providerMessageId == null || providerMessageId.isBlank()) return;
        messages.findByProviderMessageId(providerMessageId).ifPresent(message -> {
            message.status(normalizeStatus(status), failureReason);
            messages.save(message);
        });
    }

    private void markProviderRead(WhatsAppConversation conversation) {
        conversation.markRead();
        conversations.save(conversation);
        var latest = messages.findFirstByConversation_IdAndDirectionOrderByCreatedAtDesc(
                conversation.getId(), "inbound").orElse(null);
        if (latest != null && latest.getProviderMessageId() != null) {
            try {
                sender.markRead(conversation.getPhoneNumberId(), latest.getProviderMessageId(), token(conversation));
                latest.status("read", null);
                messages.save(latest);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not send WhatsApp read receipt conversationId={}", conversation.getId(), exception);
            }
        }
    }

    private String token(WhatsAppConversation conversation) {
        return String.valueOf(integrations.runtime(conversation.getTenantId(), conversation.getAgentId(), "whatsapp")
                .credentials().getOrDefault("accessToken", ""));
    }

    private WhatsAppConversation requireConversation(UUID tenantId, UUID id) {
        return conversations.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("WhatsApp conversation not found"));
    }

    private static String preview(String type, String body) {
        if (body != null && !body.isBlank()) return body.trim();
        return switch (type == null ? "" : type) {
            case "audio" -> "Voice note";
            case "image" -> "Image";
            case "video" -> "Video";
            case "document" -> "Document";
            case "location" -> "Location";
            case "contacts" -> "Contact";
            default -> "WhatsApp message";
        };
    }

    private static String normalizeStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase(java.util.Locale.ROOT)) {
            case "sent", "delivered", "read", "failed" -> status.toLowerCase(java.util.Locale.ROOT);
            default -> "sent";
        };
    }
    private static String safeFailure(RuntimeException exception) {
        var message = exception.getMessage() == null ? "WhatsApp delivery failed" : exception.getMessage();
        return message.substring(0, Math.min(1000, message.length()));
    }

    public record InboundResult(UUID conversationId, String mode, boolean created) { }
    public record MediaContent(byte[] bytes, String contentType, String fileName) { }

    private static String downloadName(WhatsAppMessage message) {
        var extension = switch (message.getMessageType()) {
            case "image", "sticker" -> ".jpg";
            case "video" -> ".mp4";
            case "audio" -> ".ogg";
            default -> "";
        };
        return "whatsapp-" + message.getId() + extension;
    }
}
