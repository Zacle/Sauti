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
        touch(preview, at);
    }

    public void sent(String preview, OffsetDateTime at) { touch(preview, at); }
    public void markRead() { this.unreadCount = 0; }
    public void assign(String nextMode) {
        if (!"ai".equals(nextMode) && !"human".equals(nextMode)) {
            throw new IllegalArgumentException("WhatsApp mode must be ai or human");
        }
        this.mode = nextMode;
    }
    public void close() { this.status = "closed"; this.unreadCount = 0; }
    public void reopen() { this.status = "open"; }

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
}
