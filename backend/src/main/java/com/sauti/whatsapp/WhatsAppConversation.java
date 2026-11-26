package com.sauti.whatsapp;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_conversations")
public class WhatsAppConversation extends Auditable {
    /** WhatsApp lets a business send free-form messages for this long after the customer last wrote. */
    public static final java.time.Duration REPLY_WINDOW = java.time.Duration.ofHours(24);
    /** Why the last customer message is waiting on the team. */
    public static final String AGENT_FAILED = "agent_failed";
    public static final String AGENT_PAUSED = "agent_paused";
    public static final String WAITING_FOR_TEAM = "waiting_for_team";

    @Id private UUID id;
    @Column(nullable = false) private UUID tenantId;
    @Column(nullable = false) private UUID agentId;
    @Column(nullable = false, length = 100) private String phoneNumberId;
    @Column(nullable = false, length = 50) private String customerNumber;
    private String customerName;
    @Column(nullable = false, length = 20) private String mode;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private int unreadCount;
    @Column(length = 500) private String lastMessagePreview;
    private OffsetDateTime lastMessageAt;
    private OffsetDateTime lastCustomerMessageAt;
    @Column(nullable = false) private boolean needsAttention;
    @Column(length = 40) private String attentionReason;
    @Column(nullable = false) private boolean sessionOpen;

    protected WhatsAppConversation() { }

    public WhatsAppConversation(UUID tenantId, UUID agentId, String phoneNumberId,
                                String customerNumber, String customerName) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.phoneNumberId = phoneNumberId;
        this.customerNumber = customerNumber;
        this.customerName = clean(customerName);
        this.mode = "ai";
        this.status = "open";
    }

    public void receive(String customerName, String preview, OffsetDateTime at) {
        if (!clean(customerName).isBlank()) this.customerName = clean(customerName);
        this.status = "open";
        this.unreadCount++;
        this.lastCustomerMessageAt = at == null ? OffsetDateTime.now() : at;
        this.sessionOpen = true;
        touch(preview, at);
    }

    /** The last customer message is waiting on the team, for the given reason. */
    public void needsAttention(String reason) {
        this.needsAttention = true;
        this.attentionReason = reason;
    }

    /** Someone (the agent or the team) has answered. */
    public void answered() {
        this.needsAttention = false;
        this.attentionReason = null;
    }

    /** Shows a voice note's words in the list once it has been transcribed. */
    public void previewTranscript(String transcript) {
        var value = clean(transcript);
        this.lastMessagePreview = value.substring(0, Math.min(500, value.length()));
    }

    /** Its call record has been ended; the next customer message starts a new one. */
    public void sessionEnded() { this.sessionOpen = false; }

    /** When free-form replies stop being allowed, or null before the customer has written. */
    public OffsetDateTime replyWindowEndsAt() {
        return lastCustomerMessageAt == null ? null : lastCustomerMessageAt.plus(REPLY_WINDOW);
    }

    public boolean replyWindowOpen(OffsetDateTime now) {
        var endsAt = replyWindowEndsAt();
        return endsAt != null && now.isBefore(endsAt);
    }

    public void sent(String preview, OffsetDateTime at) { touch(preview, at); }
    public void markRead() { this.unreadCount = 0; }
    public void assign(String nextMode) {
        if (!"ai".equals(nextMode) && !"human".equals(nextMode)) {
            throw new IllegalArgumentException("WhatsApp mode must be ai or human");
        }
        this.mode = nextMode;
    }
    /** Staff are done with it: it leaves the open list and waits on no one. A new customer message reopens it. */
    public void close() {
        this.status = "closed";
        this.unreadCount = 0;
        answered();
    }
    public void reopen() { this.status = "open"; }
    public boolean isClosed() { return "closed".equals(status); }

    private void touch(String preview, OffsetDateTime at) {
        this.lastMessagePreview = clean(preview).substring(0, Math.min(500, clean(preview).length()));
        this.lastMessageAt = at == null ? OffsetDateTime.now() : at;
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAgentId() { return agentId; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public String getCustomerNumber() { return customerNumber; }
    public String getCustomerName() { return customerName; }
    public String getMode() { return mode; }
    public String getStatus() { return status; }
    public int getUnreadCount() { return unreadCount; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public OffsetDateTime getLastMessageAt() { return lastMessageAt; }
    public OffsetDateTime getLastCustomerMessageAt() { return lastCustomerMessageAt; }
    public boolean isNeedsAttention() { return needsAttention; }
    public String getAttentionReason() { return attentionReason; }
    public boolean isSessionOpen() { return sessionOpen; }
}
