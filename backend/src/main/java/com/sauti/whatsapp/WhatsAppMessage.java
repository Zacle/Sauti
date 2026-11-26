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
    /** customer, ai or staff; unknown for outbound messages sent before authors were kept. */
    @Column(length = 20) private String author;
    /** The team member who sent a staff reply, and their email as it was then. */
    private UUID sentByUserId;
    @Column(length = 320) private String sentBy;
    /** The approved template a staff message was sent from. */
    @Column(length = 512) private String templateName;

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
        this.author = "inbound".equals(direction) ? "customer" : null;
    }

    /** An outbound message written by the agent or by a member of the team. */
    public static WhatsAppMessage outbound(WhatsAppConversation conversation, String author, String messageType,
                                           String body, String mediaMimeType) {
        var message = new WhatsAppMessage(conversation, null, "outbound", messageType, body, null, mediaMimeType, "sending");
        message.author = author;
        return message;
    }

    /** A reply written by a member of the team, or sent from one of the business's approved templates. */
    public static WhatsAppMessage staff(WhatsAppConversation conversation, String messageType, String body,
                                        StaffSender sender, String templateName) {
        var message = outbound(conversation, "staff", messageType, body, null);
        if (sender != null) {
            message.sentByUserId = sender.userId();
            message.sentBy = blankToNull(sender.label());
        }
        message.templateName = blankToNull(templateName);
        return message;
    }

    /** A voice note recorded before it was transcribed gains its words. */
    public void transcribed(String transcript) {
        if (transcript != null && !transcript.isBlank()) this.body = transcript.trim();
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
    public String getAuthor() { return author; }
    public UUID getSentByUserId() { return sentByUserId; }
    public String getSentBy() { return sentBy; }
    public String getTemplateName() { return templateName; }

    /** Who on the team is sending: a signed-in person, or an API key. */
    public record StaffSender(UUID userId, String label) { }
}
