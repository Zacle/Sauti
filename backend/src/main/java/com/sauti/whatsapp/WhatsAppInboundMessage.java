package com.sauti.whatsapp;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_inbound_messages")
public class WhatsAppInboundMessage extends Auditable {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String providerMessageId;

    @Column(nullable = false)
    private String phoneNumberId;

    @Column(nullable = false)
    private String customerNumber;

    @Column(nullable = false)
    private String messageType;

    @Column(nullable = false)
    private String status;

    private String failureReason;

    protected WhatsAppInboundMessage() {
    }

    public WhatsAppInboundMessage(
            String providerMessageId,
            String phoneNumberId,
            String customerNumber,
            String messageType
    ) {
        this.id = UUID.randomUUID();
        this.providerMessageId = providerMessageId;
        this.phoneNumberId = phoneNumberId;
        this.customerNumber = customerNumber;
        this.messageType = messageType;
        this.status = "received";
    }

    public void markProcessing() {
        this.status = "processing";
    }

    public void markCompleted() {
        this.status = "completed";
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = "failed";
        this.failureReason = reason == null ? "Unknown WhatsApp processing failure" : reason.substring(0, Math.min(1000, reason.length()));
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
