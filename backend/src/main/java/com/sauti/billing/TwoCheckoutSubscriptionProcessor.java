package com.sauti.billing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.TenantRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TwoCheckoutSubscriptionProcessor {
    private static final String PROVIDER = "2checkout";
    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")
    );
    private final BillingProviderEventRepository events;
    private final BillingSubscriptionRepository subscriptions;
    private final TenantRepository tenants;
    private final BillingLedgerService ledger;
    private final TwoCheckoutPlanCatalog plans;
    private final TwoCheckoutTenantReference tenantReferences;
    private final ObjectMapper objectMapper;

    public TwoCheckoutSubscriptionProcessor(
            BillingProviderEventRepository events, BillingSubscriptionRepository subscriptions,
            TenantRepository tenants, BillingLedgerService ledger, TwoCheckoutPlanCatalog plans,
            ObjectMapper objectMapper,
            @Value("${sauti.billing.2checkout.secret-key:}") String secretKey) {
        this.events = events;
        this.subscriptions = subscriptions;
        this.tenants = tenants;
        this.ledger = ledger;
        this.plans = plans;
        this.objectMapper = objectMapper;
        this.tenantReferences = new TwoCheckoutTenantReference(secretKey);
    }

    @Scheduled(fixedDelayString = "${sauti.billing.2checkout.worker-delay-ms:5000}")
    @Transactional
    public void processDue() {
        var due = events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                PROVIDER, List.of("pending", "retrying"), OffsetDateTime.now());
        for (var event : due) {
            try {
                process(event);
                event.processed();
            } catch (Exception exception) {
                event.retry(exception.getMessage());
            }
            events.save(event);
        }
    }

    private void process(BillingProviderEvent event) throws Exception {
        var values = objectMapper.readValue(event.getPayloadJson(), new TypeReference<Map<String, String>>() { });
        var subscriptionId = required(values, "LICENSE_CODE");
        var productCode = first(values, "LICENSE_PRODUCT_CODE", "PSKU", "LICENSE_PRODUCT");
        var selection = plans.byCode(productCode)
                .orElseThrow(() -> new IllegalArgumentException("2Checkout product is not configured"));
        var tenantId = tenantReferences.verify(required(values, "EXTERNAL_CUSTOMER_REFERENCE"));
        var existingByProvider = subscriptions
                .findByProviderAndProviderSubscriptionId(PROVIDER, subscriptionId).orElse(null);
        if (existingByProvider != null && !existingByProvider.getTenantId().equals(tenantId)) {
            throw new SecurityException("Subscription workspace does not match existing ownership");
        }
        var tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription workspace was not found"));
        var subscription = existingByProvider != null ? existingByProvider
                : subscriptions.findByTenantId(tenantId)
                        .orElseGet(() -> new BillingSubscription(tenantId, PROVIDER, subscriptionId));
        if (!PROVIDER.equals(subscription.getProvider())) {
            throw new IllegalArgumentException("Workspace subscription belongs to a different billing provider");
        }
        if (!subscriptionId.equals(subscription.getProviderSubscriptionId())) {
            throw new IllegalArgumentException("Workspace already has a different subscription");
        }
        var providerUpdatedAt = timestamp(values.get("DATE_UPDATED"));
        if (!subscription.isNewerThan(providerUpdatedAt)) return;

        var status = required(values, "STATUS").toLowerCase(Locale.ROOT).replace("pastdue", "past_due");
        var customerId = firstOr(values, required(values, "EXTERNAL_CUSTOMER_REFERENCE"),
                "AVANGATE_CUSTOMER_REFERENCE");
        var orderId = firstOr(values, subscriptionId, "LAST_ORDER_REFERENCE", "ORIGINAL_ORDER_REFERENCE");
        var renewsAt = timestamp(values.get("NEXT_RENEWAL_DATE"));
        var endsAt = timestamp(firstOr(values, null, "EXPIRATION_DATE_TIME", "EXPIRATION_DATE"));
        subscription.synchronize(customerId, orderId, productCode, productCode,
                selection.plan(), selection.interval(), status,
                "1".equals(values.get("TEST")), renewsAt, endsAt, null, providerUpdatedAt,
                values.get("NEXT_RENEWAL_CARD_TYPE"), values.get("NEXT_RENEWAL_CARD_LAST_DIGITS"), "");
        subscriptions.save(subscription);
        tenant.applyBillingSubscription(selection.plan(), selection.monthlyMinutes(),
                planExpiry(status, renewsAt, endsAt), customerId);
        tenants.save(tenant);
        var account = ledger.account(tenantId);
        account.configure(accountStatus(status), "observe", account.getBillingCurrency(),
                account.getMonthlySpendingLimit(), account.getLowBalanceThreshold());
    }

    private static OffsetDateTime planExpiry(String status, OffsetDateTime renewsAt, OffsetDateTime endsAt) {
        return "canceled".equals(status) || "expired".equals(status) ? endsAt : renewsAt;
    }

    private static String accountStatus(String status) {
        return switch (status) {
            case "pending_activation" -> "trialing";
            case "active", "paused", "canceled" -> "active";
            case "past_due", "expired" -> "past_due";
            default -> throw new IllegalArgumentException("Unsupported 2Checkout subscription status");
        };
    }

    private static String required(Map<String, String> values, String name) {
        var value = values.getOrDefault(name, "").trim();
        if (value.isBlank()) throw new IllegalArgumentException("2Checkout " + name + " is required");
        return value;
    }

    private static String first(Map<String, String> values, String... names) {
        var value = firstOr(values, null, names);
        if (value == null) throw new IllegalArgumentException("2Checkout product code is required");
        return value;
    }

    private static String firstOr(Map<String, String> values, String fallback, String... names) {
        for (var name : names) {
            var value = values.get(name);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return fallback;
    }

    private static OffsetDateTime timestamp(String value) {
        if (value == null || value.isBlank() || value.startsWith("9999-")) return null;
        var clean = value.trim();
        try { return OffsetDateTime.parse(clean); } catch (DateTimeParseException ignored) { }
        for (var formatter : DATE_TIMES) {
            try {
                if (formatter.toString().contains("Offset")) return OffsetDateTime.parse(clean, formatter);
                return LocalDateTime.parse(clean, formatter).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) { }
        }
        try { return LocalDate.parse(clean).atStartOfDay().atOffset(ZoneOffset.UTC); }
        catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("2Checkout date is invalid", exception);
        }
    }
}
