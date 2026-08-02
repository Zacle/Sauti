package com.sauti.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingLedgerService {
    private final BillingAccountRepository accounts;
    private final CommunicationLedgerRepository ledger;
    private final TenantRepository tenants;
    private final ObjectMapper objectMapper;

    public BillingLedgerService(BillingAccountRepository accounts,
                                CommunicationLedgerRepository ledger,
                                TenantRepository tenants,
                                ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.tenants = tenants;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BillingAccount account(UUID tenantId) {
        tenants.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        return accounts.findByTenantId(tenantId).orElseGet(() -> createAccount(tenantId));
    }

    @Transactional
    public void authorizePaidResource(UUID tenantId, BigDecimal estimatedAmount, String currency) {
        var account = lockedAccount(tenantId);
        if (List.of("suspended", "cancelled").contains(account.getStatus())) {
            throw new IllegalStateException("Paid communication resources are disabled for this workspace");
        }
        if (!account.isEnforced()) return;
        if (!("active".equals(account.getStatus()) || "trialing".equals(account.getStatus()))) {
            throw new IllegalStateException("An active billing account is required for paid communication resources");
        }
        var amount = estimatedAmount == null ? BigDecimal.ZERO : estimatedAmount;
        if (amount.signum() <= 0) return;
        var normalizedCurrency = currency(currency);
        var balance = balances(tenantId).getOrDefault(normalizedCurrency, BigDecimal.ZERO);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient communication balance for this provider purchase");
        }
        var limit = account.getMonthlySpendingLimit();
        if (limit != null) {
            var start = OffsetDateTime.now(ZoneOffset.UTC)
                    .with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
            var spent = monetaryTotal(ledger.findAllByTenantIdAndCreatedAtGreaterThanEqual(tenantId, start),
                    normalizedCurrency, "debit");
            if (spent.add(amount).compareTo(limit) > 0) {
                throw new IllegalStateException("This purchase exceeds the workspace monthly spending limit");
            }
        }
    }

    @Transactional
    public CommunicationLedgerEntry recordDebit(UUID tenantId, String category, BigDecimal quantity, String unit,
                                                BigDecimal amount, String currency, String idempotencyKey,
                                                String externalReference, String description,
                                                Map<String, Object> metadata) {
        return record(tenantId, "debit", category, quantity, unit, amount, currency, idempotencyKey,
                externalReference, description, amount == null ? "unpriced" : "provider_quote", metadata);
    }

    @Transactional
    public CommunicationLedgerEntry recordCredit(UUID tenantId, String category, BigDecimal quantity, String unit,
                                                 BigDecimal amount, String currency, String idempotencyKey,
                                                 String externalReference, String description,
                                                 Map<String, Object> metadata) {
        return record(tenantId, "credit", category, quantity, unit, amount, currency, idempotencyKey,
                externalReference, description, "credit", metadata);
    }

    @Transactional
    public CommunicationLedgerEntry recordProviderCost(UUID tenantId, String category, BigDecimal amount,
                                                       String currency, String idempotencyKey,
                                                       String externalReference, String description,
                                                       Map<String, Object> metadata) {
        return record(tenantId, "debit", category, BigDecimal.ONE, "provider_charge", amount, currency,
                idempotencyKey, externalReference, description, "provider_confirmed", metadata);
    }

    @Transactional
    public CommunicationLedgerEntry recordProviderCostCredit(UUID tenantId, String category, BigDecimal amount,
                                                             String currency, String idempotencyKey,
                                                             String externalReference, String description,
                                                             Map<String, Object> metadata) {
        return record(tenantId, "credit", category, BigDecimal.ONE, "provider_charge", amount, currency,
                idempotencyKey, externalReference, description, "provider_confirmed", metadata);
    }

    @Transactional
    public CommunicationLedgerEntry recordUnpricedCredit(UUID tenantId, String category, BigDecimal quantity,
                                                         String unit, String idempotencyKey,
                                                         String externalReference, String description,
                                                         Map<String, Object> metadata) {
        return record(tenantId, "credit", category, quantity, unit, null, null,
                idempotencyKey, externalReference, description, "unpriced", metadata);
    }

    @Transactional
    public CommunicationLedgerEntry recordRateCardCost(UUID tenantId, String category, BigDecimal quantity,
                                                       String unit, BigDecimal amount, String currency,
                                                       String idempotencyKey, String externalReference,
                                                       String description, Map<String, Object> metadata) {
        return record(tenantId, "debit", category, quantity, unit, amount, currency, idempotencyKey,
                externalReference, description, "rate_card", metadata);
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> balances(UUID tenantId) {
        var result = new LinkedHashMap<String, BigDecimal>();
        for (var entry : ledger.findAllByTenantId(tenantId)) {
            if (entry.getAmount() == null || entry.getCurrency() == null) continue;
            var signed = "credit".equals(entry.getDirection()) ? entry.getAmount() : entry.getAmount().negate();
            result.merge(entry.getCurrency(), signed, BigDecimal::add);
        }
        return Map.copyOf(result);
    }

    @Transactional(readOnly = true)
    public List<CommunicationLedgerEntry> recent(UUID tenantId) {
        return ledger.findTop50ByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<CommunicationLedgerEntry> currentCycle(UUID tenantId) {
        var start = OffsetDateTime.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        return ledger.findAllByTenantIdAndCreatedAtGreaterThanEqual(tenantId, start);
    }

    @Transactional(readOnly = true)
    public BigDecimal quantityTotal(UUID tenantId, String category, String externalReference) {
        return ledger.netQuantity(tenantId, category, externalReference);
    }

    @Transactional(readOnly = true)
    public BigDecimal amountTotal(UUID tenantId, String category, String externalReference, String currency) {
        return ledger.netAmount(tenantId, category, externalReference, currency(currency));
    }

    @Transactional(readOnly = true)
    CommunicationLedgerEntry latestPhoneNumberPurchase(UUID tenantId, String phoneNumber) {
        var exact = ledger.findFirstByTenantIdAndCategoryAndExternalReferenceOrderByCreatedAtDesc(
                tenantId, "phone_number_purchase", phoneNumber);
        if (exact.isPresent()) return exact.get();
        return ledger.findTop20ByTenantIdAndCategoryOrderByCreatedAtDesc(tenantId, "phone_number_purchase").stream()
                .filter(entry -> metadataValue(entry.getMetadataJson(), "phoneNumber").equals(phoneNumber))
                .findFirst().orElse(null);
    }

    private CommunicationLedgerEntry record(UUID tenantId, String direction, String category,
                                             BigDecimal quantity, String unit, BigDecimal amount, String currency,
                                             String idempotencyKey, String externalReference, String description,
                                             String costBasis, Map<String, Object> metadata) {
        var existing = ledger.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) return existing.get();
        var account = lockedAccount(tenantId);
        var entry = new CommunicationLedgerEntry(
                tenantId, account.getId(), direction, category, quantity, unit, amount,
                amount == null ? null : currency(currency), idempotencyKey, externalReference, description,
                costBasis, json(metadata)
        );
        try {
            return ledger.saveAndFlush(entry);
        } catch (DataIntegrityViolationException duplicate) {
            return ledger.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey).orElseThrow(() -> duplicate);
        }
    }

    private BillingAccount lockedAccount(UUID tenantId) {
        return accounts.findByTenantIdForUpdate(tenantId).orElseGet(() -> account(tenantId));
    }

    private BillingAccount createAccount(UUID tenantId) {
        try {
            return accounts.saveAndFlush(new BillingAccount(tenantId));
        } catch (DataIntegrityViolationException duplicate) {
            return accounts.findByTenantId(tenantId).orElseThrow(() -> duplicate);
        }
    }

    private BigDecimal monetaryTotal(List<CommunicationLedgerEntry> entries, String currency, String direction) {
        return entries.stream()
                .filter(entry -> direction.equals(entry.getDirection()))
                .filter(entry -> currency.equals(entry.getCurrency()))
                .map(CommunicationLedgerEntry::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String json(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Billing metadata could not be serialized", exception);
        }
    }

    private String metadataValue(String metadataJson, String field) {
        try {
            return objectMapper.readTree(metadataJson == null ? "{}" : metadataJson).path(field).asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String currency(String value) {
        var normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter ISO code");
        return normalized;
    }
}
