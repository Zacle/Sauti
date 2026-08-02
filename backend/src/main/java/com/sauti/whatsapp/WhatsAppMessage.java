package com.sauti.whatsapp;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_messages")
public class WhatsAppMessage extends Auditable {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id") private WhatsAppConversation conversation;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID agentId;
    @Column(unique = true) private String providerMessageId;
    @Column(nullable = false, length = 20) private String direction;
    @Column(nullable = false, length = 30) private String messageType;
    @Column(columnDefinition = "TEXT") private String body;
    private String mediaId;
    private String mediaMimeType;
    @Column(nullable = false, length = 30) private String status;
    private String failureReason;

    protected WhatsAppMessage() { }

    public WhatsAppMessage(WhatsAppConversation conversation, String providerMessageId,
                           String direction, String messageType, String body,
                           String mediaId, String mediaMimeType, String status) {
        this.id = UUID.randomUUID();
        this.conversation = conversation;
        this.tenantId = conversation.getTenantId();
        this.agentId = conversation.getAgentId();
        this.providerMessageId = blankToNull(providerMessageId);
        this.direction = direction;
        this.messageType = messageType;
        this.body = blankToNull(body);
        this.mediaId = blankToNull(mediaId);
        this.mediaMimeType = blankToNull(mediaMimeType);
        this.status = status;
    }

    public void providerAccepted(String providerMessageId) {
        this.providerMessageId = blankToNull(providerMessageId);
        this.status = "sent";
        this.failureReason = null;
    }
    public void status(String nextStatus, String failureReason) {
        if (nextStatus != null && !nextStatus.isBlank()) this.status = nextStatus;
        this.failureReason = blankToNull(failureReason);
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public WhatsAppConversation getConversation() { return conversation; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getDirection() { return direction; }
    public String getMessageType() { return messageType; }
    public String getBody() { return body; }
    public String getMediaId() { return mediaId; }
    public String getMediaMimeType() { return mediaMimeType; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}
