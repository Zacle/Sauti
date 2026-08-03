package com.sauti.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.TenantRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LemonSqueezySubscriptionProcessor {
    private final BillingProviderEventRepository events;
    private final BillingSubscriptionRepository subscriptions;
    private final TenantRepository tenants;
    private final BillingLedgerService ledger;
    private final LemonSqueezyPlanCatalog plans;
    private final ObjectMapper objectMapper;

    public LemonSqueezySubscriptionProcessor(BillingProviderEventRepository events,
                                             BillingSubscriptionRepository subscriptions,
                                             TenantRepository tenants,
                                             BillingLedgerService ledger,
                                             LemonSqueezyPlanCatalog plans,
                                             ObjectMapper objectMapper) {
        this.events = events;
        this.subscriptions = subscriptions;
        this.tenants = tenants;
        this.ledger = ledger;
        this.plans = plans;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${sauti.billing.lemon-squeezy.worker-delay-ms:5000}")
    @Transactional
    public void processDue() {
        var due = events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                "lemon_squeezy", List.of("pending", "retrying"), OffsetDateTime.now());
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
        if (!event.getEventName().startsWith("subscription_")) return;
        var root = objectMapper.readTree(event.getPayloadJson());
        var data = root.path("data");
        if (!"subscriptions".equals(data.path("type").asText())) {
            throw new IllegalArgumentException("Billing event is not a subscription resource");
        }
        var subscriptionId = required(data.path("id"), "subscription id");
        var attributes = data.path("attributes");
        var variantId = required(attributes.path("variant_id"), "variant id");
        var selection = plans.byVariant(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription variant is not configured"));
        var existingByProvider = subscriptions
                .findByProviderAndProviderSubscriptionId("lemon_squeezy", subscriptionId).orElse(null);
        var tenantId = tenantId(root, existingByProvider);
        if (existingByProvider != null && !existingByProvider.getTenantId().equals(tenantId)) {
            throw new SecurityException("Subscription workspace does not match existing ownership");
        }
        var tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription workspace was not found"));
        var subscription = existingByProvider != null
                ? existingByProvider
                : subscriptions.findByTenantId(tenantId)
                        .orElseGet(() -> new BillingSubscription(tenantId, "lemon_squeezy", subscriptionId));
        if (!"lemon_squeezy".equals(subscription.getProvider())) {
            throw new IllegalArgumentException("Workspace subscription belongs to a different billing provider");
        }
        if (!subscription.getProviderSubscriptionId().equals(subscriptionId)) {
            throw new IllegalArgumentException("Workspace already has a different subscription");
        }
        var providerUpdatedAt = timestamp(attributes.path("updated_at"));
        if (!subscription.isNewerThan(providerUpdatedAt)) return;

        var status = required(attributes.path("status"), "subscription status");
        var customerId = required(attributes.path("customer_id"), "customer id");
        subscription.synchronize(
                customerId,
                required(attributes.path("order_id"), "order id"),
                required(attributes.path("product_id"), "product id"),
                variantId,
                selection.plan(), selection.interval(), status,
                attributes.path("test_mode").asBoolean(false),
                timestamp(attributes.path("renews_at")),
                timestamp(attributes.path("ends_at")),
                timestamp(attributes.path("trial_ends_at")),
                providerUpdatedAt,
                attributes.path("card_brand").asText(""),
                attributes.path("card_last_four").asText(""),
                attributes.path("urls").path("update_payment_method").asText("")
        );
        subscriptions.save(subscription);
        tenant.applyBillingSubscription(selection.plan(), selection.monthlyMinutes(),
                planExpiry(status, attributes), customerId);
        tenants.save(tenant);
        var account = ledger.account(tenantId);
        account.configure(accountStatus(status), "observe", account.getBillingCurrency(),
                account.getMonthlySpendingLimit(), account.getLowBalanceThreshold());
    }

    private UUID tenantId(JsonNode root, BillingSubscription existing) {
        var raw = root.path("meta").path("custom_data").path("tenant_id").asText("");
        if (!raw.isBlank()) {
            try { return UUID.fromString(raw); }
            catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Subscription workspace id is invalid"); }
        }
        if (existing != null) return existing.getTenantId();
        throw new IllegalArgumentException("Subscription workspace id is missing");
    }

    private OffsetDateTime planExpiry(String status, JsonNode attributes) {
        return "cancelled".equals(status) || "expired".equals(status)
                ? timestamp(attributes.path("ends_at")) : timestamp(attributes.path("renews_at"));
    }

    private String accountStatus(String providerStatus) {
        return switch (providerStatus) {
            case "on_trial" -> "trialing";
            case "active", "paused", "cancelled" -> "active";
            case "past_due" -> "past_due";
            // Observe mode records payment loss without blocking paid resources.
            // A later reviewed enforcement rollout can translate these to suspended.
            case "unpaid", "expired" -> "past_due";
            default -> throw new IllegalArgumentException("Unsupported subscription status");
        };
    }

    private static String required(JsonNode node, String label) {
        var value = node.asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException("Lemon Squeezy " + label + " is required");
        return value;
    }

    private static OffsetDateTime timestamp(JsonNode node) {
        var value = node.asText("").trim();
        if (value.isBlank()) return null;
        return OffsetDateTime.parse(value);
    }
}
