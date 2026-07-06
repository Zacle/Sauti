package com.sauti.integration;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "mpesa_payment_requests")
public class MpesaPaymentRequest {
    @Id private UUID id;
    private UUID tenantId;
    private UUID agentId;
    private UUID callId;
    private UUID connectionId;
    private String recipientPhone;
    private BigDecimal amount;
    private String accountReference;
    private String description;
    private String merchantRequestId;
    private String checkoutRequestId;
    private String status;
    private Integer resultCode;
    private String resultDescription;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    protected MpesaPaymentRequest() {}
    public MpesaPaymentRequest(UUID tenantId, UUID agentId, UUID callId, UUID connectionId,
                               String recipientPhone, BigDecimal amount, String accountReference, String description) {
        this.id = UUID.randomUUID(); this.tenantId = tenantId; this.agentId = agentId; this.callId = callId;
        this.connectionId = connectionId; this.recipientPhone = recipientPhone; this.amount = amount;
        this.accountReference = accountReference; this.description = description;
        this.status = "pending"; this.createdAt = OffsetDateTime.now(); this.updatedAt = createdAt;
    }
    public UUID getId() { return id; }
    public UUID getCallId() { return callId; }
    public String getStatus() { return status; }
    public void submitted(String merchantId, String checkoutId) {
        merchantRequestId = merchantId; checkoutRequestId = checkoutId; status = "submitted";
        updatedAt = OffsetDateTime.now();
    }
    public void failed(String reason) { status = "failed"; resultDescription = reason; updatedAt = OffsetDateTime.now(); }
    public void callback(int code, String reason) {
        if ("completed".equals(status) || "cancelled".equals(status)) return;
        resultCode = code; resultDescription = reason;
        status = code == 0 ? "completed" : code == 1032 ? "cancelled" : "failed";
        updatedAt = OffsetDateTime.now();
    }
}
