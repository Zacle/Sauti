package com.sauti.whatsapp;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A customer message claimed from Meta's webhook, with everything needed to process it again. It moves
 * from received to processing to completed; a failure is tried again until {@link #MAX_ATTEMPTS}.
 */
@Entity
@Table(name = "whatsapp_inbound_messages")
public class WhatsAppInboundMessage extends Auditable {
    /** Tries before the message is left for staff. */
    public static final int MAX_ATTEMPTS = 3;

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

    private String customerName;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String mediaId;

    private String mediaMimeType;

    @Column(nullable = false)
    private int attempts;

    protected WhatsAppInboundMessage() {
    }

    public WhatsAppInboundMessage(
            String providerMessageId,
            String phoneNumberId,
            String customerNumber,
            String messageType
    ) {
        this(providerMessageId, phoneNumberId, customerNumber, messageType, null, null, null, null);
    }

    public WhatsAppInboundMessage(String providerMessageId, String phoneNumberId, String customerNumber,
                                  String messageType, String customerName, String body,
                                  String mediaId, String mediaMimeType) {
        this.id = UUID.randomUUID();
        this.providerMessageId = providerMessageId;
        this.phoneNumberId = phoneNumberId;
        this.customerNumber = customerNumber;
        this.messageType = messageType;
        this.customerName = blankToNull(customerName);
        this.body = blankToNull(body);
        this.mediaId = blankToNull(mediaId);
        this.mediaMimeType = blankToNull(mediaMimeType);
        this.status = "received";
    }

    public void markProcessing() {
        this.status = "processing";
        this.attempts++;
    }

    /** Queued to be processed again; not picked up a second time while it waits. */
    public void queuedAgain() {
        this.status = "queued";
    }

    public void markCompleted() {
        this.status = "completed";
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = "failed";
        this.failureReason = reason == null ? "Unknown WhatsApp processing failure" : reason.substring(0, Math.min(1000, reason.length()));
    }

    /** Whether a failure will be tried again, or the message is now left for staff. */
    public boolean canRetry() {
        return attempts < MAX_ATTEMPTS;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public UUID getId() { return id; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getPhoneNumberId() { return phoneNumberId; }
    public String getCustomerNumber() { return customerNumber; }
    public String getMessageType() { return messageType; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getCustomerName() { return customerName == null ? "" : customerName; }
    public String getBody() { return body == null ? "" : body; }
    public String getMediaId() { return mediaId == null ? "" : mediaId; }
    public String getMediaMimeType() { return mediaMimeType; }
    public int getAttempts() { return attempts; }
}
