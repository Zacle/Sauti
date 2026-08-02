package com.sauti.billing;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "communication_ledger_entries")
public class CommunicationLedgerEntry extends Auditable {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID billingAccountId;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, length = 30)
    private String unit;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false, length = 24)
    private String costBasis;

    @Column(nullable = false, length = 160)
    private String idempotencyKey;

    @Column(length = 160)
    private String externalReference;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String metadataJson = "{}";

    protected CommunicationLedgerEntry() { }

    public CommunicationLedgerEntry(UUID tenantId, UUID billingAccountId, String direction, String category,
                                    BigDecimal quantity, String unit, BigDecimal amount, String currency,
                                    String idempotencyKey, String externalReference, String description,
                                    String costBasis, String metadataJson) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.billingAccountId = billingAccountId;
        this.direction = required(direction).toLowerCase(Locale.ROOT);
        this.category = required(category).toLowerCase(Locale.ROOT);
        this.quantity = positive(quantity, "Ledger quantity");
        this.unit = required(unit).toLowerCase(Locale.ROOT);
        this.amount = amount == null ? null : nonNegative(amount, "Ledger amount");
        this.currency = currency == null || currency.isBlank() ? null : currency.trim().toUpperCase(Locale.ROOT);
        this.costBasis = required(costBasis).toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("unpriced", "rate_card", "provider_quote", "provider_confirmed", "credit")
                .contains(this.costBasis)) {
            throw new IllegalArgumentException("Unsupported ledger cost basis");
        }
        if (this.amount != null && (this.currency == null || !this.currency.matches("[A-Z]{3}"))) {
            throw new IllegalArgumentException("A three-letter currency is required for monetary ledger entries");
        }
        if (!("credit".equals(this.direction) || "debit".equals(this.direction))) {
            throw new IllegalArgumentException("Ledger direction must be credit or debit");
        }
        this.idempotencyKey = required(idempotencyKey);
        this.externalReference = externalReference == null || externalReference.isBlank() ? null : externalReference.trim();
        this.description = required(description);
        this.metadataJson = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getBillingAccountId() { return billingAccountId; }
    public String getDirection() { return direction; }
    public String getCategory() { return category; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCostBasis() { return costBasis; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getExternalReference() { return externalReference; }
    public String getDescription() { return description; }
    public String getMetadataJson() { return metadataJson; }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Ledger value is required");
        return value.trim();
    }
    private static BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }
    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value.signum() < 0) throw new IllegalArgumentException(field + " cannot be negative");
        return value;
    }
}
