package com.sauti.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.TenantRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhopSubscriptionProcessor {
    private static final String PROVIDER = "whop";
    private final BillingProviderEventRepository events;
    private final BillingSubscriptionRepository subscriptions;
    private final TenantRepository tenants;
    private final BillingLedgerService ledger;
    private final WhopPlanCatalog plans;
    private final ObjectMapper objectMapper;
    private final WhopTenantReference tenantReferences;
    private final boolean sandbox;

    public WhopSubscriptionProcessor(
            BillingProviderEventRepository events, BillingSubscriptionRepository subscriptions,
            TenantRepository tenants, BillingLedgerService ledger, WhopPlanCatalog plans,
            ObjectMapper objectMapper,
            @Value("${sauti.billing.whop.tenant-reference-secret:}") String tenantReferenceSecret,
            @Value("${sauti.billing.whop.sandbox:false}") boolean sandbox) {
        this.events = events;
        this.subscriptions = subscriptions;
        this.tenants = tenants;
        this.ledger = ledger;
        this.plans = plans;
        this.objectMapper = objectMapper;
        this.tenantReferences = new WhopTenantReference(tenantReferenceSecret);
        this.sandbox = sandbox;
    }

    @Scheduled(fixedDelayString = "${sauti.billing.whop.worker-delay-ms:5000}")
    @Transactional
    public void processDue() {
        var due = events.findTop20ByProviderAndStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                PROVIDER, List.of("pending", "retrying"), OffsetDateTime.now());
        for (var event : due) {
            try {
                if (event.getEventName().startsWith("membership.")) {
                    process(event);
                    event.processed();
                } else {
                    // Retain signed payment/refund/dispute events for the normalized
                    // financial-evidence slice without claiming they were reconciled.
                    event.deferred();
                }
            } catch (Exception exception) {
                event.retry(exception.getMessage());
            }
            events.save(event);
        }
    }

    private void process(BillingProviderEvent event) throws Exception {
        var root = objectMapper.readTree(event.getPayloadJson());
        var data = root.path("data");
        var membershipId = required(data.path("id"), "membership id");
        var planId = required(data.path("plan").path("id"), "plan id");
        var selection = plans.byPlanId(planId)
                .orElseThrow(() -> new IllegalArgumentException("Whop plan is not configured"));
        var existingByProvider = subscriptions
                .findByProviderAndProviderSubscriptionId(PROVIDER, membershipId).orElse(null);
        var tenantId = tenantId(data, existingByProvider);
        if (existingByProvider != null && !existingByProvider.getTenantId().equals(tenantId)) {
            throw new SecurityException("Whop membership workspace does not match existing ownership");
        }
        var tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Whop membership workspace was not found"));
        var subscription = existingByProvider != null ? existingByProvider
                : subscriptions.findByTenantId(tenantId)
                        .orElseGet(() -> new BillingSubscription(tenantId, PROVIDER, membershipId));
        if (!PROVIDER.equals(subscription.getProvider())) {
            throw new IllegalArgumentException("Workspace subscription belongs to a different billing provider");
        }
        if (!membershipId.equals(subscription.getProviderSubscriptionId())) {
            throw new IllegalArgumentException("Workspace already has a different subscription");
        }
        var providerUpdatedAt = timestamp(data.path("updated_at"));
        if (!subscription.isNewerThan(providerUpdatedAt)) return;
        var status = required(data.path("status"), "membership status");
        var customerId = first(data.path("user").path("id"), data.path("member").path("id"), membershipId);
        var productId = required(data.path("product").path("id"), "product id");
        var renewsAt = timestamp(data.path("renewal_period_end"));
        subscription.synchronize(customerId, membershipId, productId, planId,
                selection.plan(), selection.interval(), status, sandbox,
                renewsAt, endDate(status, renewsAt), null, providerUpdatedAt,
                "", "", data.path("manage_url").asText(""));
        subscriptions.save(subscription);
        tenant.applyBillingSubscription(selection.plan(), selection.monthlyMinutes(),
                renewsAt, customerId);
        tenants.save(tenant);
        var account = ledger.account(tenantId);
        account.configure(accountStatus(status), "observe", account.getBillingCurrency(),
                account.getMonthlySpendingLimit(), account.getLowBalanceThreshold());
    }

    private UUID tenantId(JsonNode data, BillingSubscription existing) {
        var reference = data.path("metadata").path("sauti_tenant_reference").asText("");
        if (!reference.isBlank()) return tenantReferences.verify(reference);
        if (existing != null) return existing.getTenantId();
        throw new IllegalArgumentException("Whop workspace reference is missing");
    }

    private static String accountStatus(String status) {
        return switch (status) {
            case "trialing" -> "trialing";
            case "active", "completed", "canceling", "canceled" -> "active";
            case "past_due", "expired", "unresolved" -> "past_due";
            default -> throw new IllegalArgumentException("Unsupported Whop membership status");
        };
    }

    private static OffsetDateTime endDate(String status, OffsetDateTime renewsAt) {
        return "canceled".equals(status) || "expired".equals(status) || "completed".equals(status)
                ? renewsAt : null;
    }

    private static String first(JsonNode first, JsonNode second, String fallback) {
        var value = first.asText("").trim();
        if (!value.isBlank()) return value;
        value = second.asText("").trim();
        return value.isBlank() ? fallback : value;
    }

    private static String required(JsonNode node, String label) {
        var value = node.asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException("Whop " + label + " is required");
        return value;
    }

    private static OffsetDateTime timestamp(JsonNode node) {
        var value = node.asText("").trim();
        return value.isBlank() ? null : OffsetDateTime.parse(value);
    }
}
