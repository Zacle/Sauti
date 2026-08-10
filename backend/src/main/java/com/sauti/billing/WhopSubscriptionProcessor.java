package com.sauti.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.TenantRepository;
import java.math.BigDecimal;
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
    private final BillingAddOnSubscriptionRepository addOnSubscriptions;
    private final BillingPaymentNotificationRepository paymentNotifications;
    private final BillingPlanChangeRequestRepository planChangeRequests;
    private final TenantRepository tenants;
    private final BillingLedgerService ledger;
    private final WhopPlanCatalog plans;
    private final WhopAddOnCatalog addOns;
    private final ObjectMapper objectMapper;
    private final WhopTenantReference tenantReferences;
    private final boolean sandbox;

    public WhopSubscriptionProcessor(
            BillingProviderEventRepository events, BillingSubscriptionRepository subscriptions,
            BillingAddOnSubscriptionRepository addOnSubscriptions,
            BillingPaymentNotificationRepository paymentNotifications,
            BillingPlanChangeRequestRepository planChangeRequests,
            TenantRepository tenants, BillingLedgerService ledger, WhopPlanCatalog plans,
            WhopAddOnCatalog addOns, ObjectMapper objectMapper,
            @Value("${sauti.billing.whop.tenant-reference-secret:}") String tenantReferenceSecret,
            @Value("${sauti.billing.whop.sandbox:false}") boolean sandbox) {
        this.events = events;
        this.subscriptions = subscriptions;
        this.addOnSubscriptions = addOnSubscriptions;
        this.paymentNotifications = paymentNotifications;
        this.planChangeRequests = planChangeRequests;
        this.tenants = tenants;
        this.ledger = ledger;
        this.plans = plans;
        this.addOns = addOns;
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
                } else if ("payment.succeeded".equals(event.getEventName())) {
                    enqueuePaymentConfirmation(event);
                    // Financial reconciliation remains deferred; only the verified
                    // customer confirmation is queued by this slice.
                    event.deferred();
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

    private void enqueuePaymentConfirmation(BillingProviderEvent event) throws Exception {
        var data = objectMapper.readTree(event.getPayloadJson()).path("data");
        var paymentId = required(data.path("id"), "payment id");
        if (paymentNotifications.findByProviderAndProviderPaymentId(PROVIDER, paymentId).isPresent()) return;
        var membershipId = required(data.path("membership").path("id"), "payment membership id");
        var planId = required(data.path("plan").path("id"), "payment plan id");

        var base = subscriptions.findByProviderAndProviderSubscriptionId(PROVIDER, membershipId).orElse(null);
        var addOn = addOnSubscriptions.findByProviderAndProviderSubscriptionId(PROVIDER, membershipId).orElse(null);
        if (base == null && addOn == null) {
            // Whop does not guarantee webhook ordering. Retry until the verified
            // membership event establishes workspace ownership.
            throw new IllegalStateException("Whop payment membership has not been synchronized yet");
        }
        var tenantId = base != null ? base.getTenantId() : addOn.getTenantId();
        var tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Whop payment workspace was not found"));
        var description = plans.byPlanId(planId)
                .map(plan -> title(plan.plan()) + " plan (" + plan.interval() + ")")
                .orElseGet(() -> addOns.byPlanId(planId)
                        .map(item -> addOnTitle(item.id()))
                        .orElseThrow(() -> new IllegalArgumentException("Whop payment plan is not configured")));
        var amount = decimal(data.path("total"), "payment total");
        var currency = required(data.path("currency"), "payment currency");
        paymentNotifications.save(new BillingPaymentNotification(
                tenantId, PROVIDER, paymentId, tenant.getEmail(), tenant.getBusinessName(),
                description, amount, currency, timestamp(data.path("paid_at")),
                data.path("card_last4").asText(""), sandbox));
    }

    private void process(BillingProviderEvent event) throws Exception {
        var root = objectMapper.readTree(event.getPayloadJson());
        var data = root.path("data");
        var membershipId = required(data.path("id"), "membership id");
        var planId = required(data.path("plan").path("id"), "plan id");
        var purchaseType = data.path("metadata").path("sauti_purchase_type").asText("");
        if ("add_on".equals(purchaseType)) {
            processAddOn(data, membershipId, planId);
            return;
        }
        var selection = plans.byPlanId(planId)
                .orElseThrow(() -> new IllegalArgumentException("Whop plan is not configured"));
        var existingByProvider = subscriptions
                .findByProviderAndProviderSubscriptionId(PROVIDER, membershipId).orElse(null);
        var tenantId = tenantId(data, existingByProvider, selection);
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
            var request = planChangeRequests.findByTenantId(tenantId).orElse(null);
            if (request != null && "completed".equals(request.getStatus())
                    && membershipId.equals(request.getProviderSubscriptionId())) {
                return;
            }
            requireReplacement(request, subscription, membershipId, selection, data);
            subscription.replaceProviderSubscription(membershipId);
            request.complete();
            planChangeRequests.save(request);
        }
        var providerUpdatedAt = timestamp(data.path("updated_at"));
        if (!subscription.isNewerThan(providerUpdatedAt)) return;
        var status = membershipStatus(data);
        var customerId = first(data.path("user").path("id"), data.path("member").path("id"), membershipId);
        var productId = required(data.path("product").path("id"), "product id");
        var renewsAt = timestamp(data.path("renewal_period_end"));
        subscription.synchronize(customerId, membershipId, productId, planId,
                selection.plan(), selection.interval(), status, sandbox,
                renewsAt, endDate(status, renewsAt), null, providerUpdatedAt,
                "", "", data.path("manage_url").asText(""));
        subscriptions.save(subscription);
        completeInPlacePlanChange(tenantId, membershipId, selection);
        tenant.applyBillingSubscription(selection.plan(), selection.monthlyMinutes(),
                renewsAt, customerId);
        tenants.save(tenant);
        var account = ledger.account(tenantId);
        account.configure(accountStatus(status), "observe", account.getBillingCurrency(),
                account.getMonthlySpendingLimit(), account.getLowBalanceThreshold());
    }

    private void processAddOn(JsonNode data, String membershipId, String planId) {
        var selection = addOns.byPlanId(planId)
                .orElseThrow(() -> new IllegalArgumentException("Whop add-on plan is not configured"));
        var metadataAddOn = required(data.path("metadata").path("sauti_add_on"), "add-on identifier");
        if (!selection.id().equals(metadataAddOn)) {
            throw new SecurityException("Whop add-on metadata does not match its configured plan");
        }
        var existing = addOnSubscriptions
                .findByProviderAndProviderSubscriptionId(PROVIDER, membershipId).orElse(null);
        var reference = data.path("metadata").path("sauti_tenant_reference").asText("");
        var tenantId = !reference.isBlank() ? tenantReferences.verify(reference)
                : existing != null ? existing.getTenantId()
                : throwMissingAddOnReference();
        tenants.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Whop add-on workspace was not found"));
        if (existing != null && !existing.getTenantId().equals(tenantId)) {
            throw new SecurityException("Whop add-on workspace does not match existing ownership");
        }
        if (existing != null && !existing.isNewerThan(timestamp(data.path("updated_at")))) return;
        var subscription = existing != null ? existing
                : new BillingAddOnSubscription(tenantId, PROVIDER, membershipId);
        var status = membershipStatus(data);
        var renewsAt = timestamp(data.path("renewal_period_end"));
        subscription.synchronize(planId, selection.id(), status, sandbox, renewsAt,
                endDate(status, renewsAt), timestamp(data.path("updated_at")),
                data.path("manage_url").asText(""));
        addOnSubscriptions.save(subscription);
    }

    private static UUID throwMissingAddOnReference() {
        throw new IllegalArgumentException("Whop workspace reference is missing");
    }

    private UUID tenantId(JsonNode data, BillingSubscription existing, WhopPlanCatalog.Plan selection) {
        var reference = data.path("metadata").path("sauti_tenant_reference").asText("");
        if (!reference.isBlank()) return tenantReferences.verify(reference);
        if (existing != null) return existing.getTenantId();
        var customerIds = customerIds(data);
        if (customerIds.isEmpty()) throw new IllegalArgumentException("Whop workspace reference is missing");
        var candidates = customerIds.stream()
                .flatMap(customerId -> subscriptions
                        .findAllByProviderAndProviderCustomerId(PROVIDER, customerId).stream())
                .distinct()
                .filter(subscription -> planChangeRequests.findByTenantId(subscription.getTenantId())
                        .filter(request -> "requested".equals(request.getStatus()))
                        .filter(request -> request.getTargetPlan().equals(selection.plan()))
                        .filter(request -> request.getTargetInterval().equals(selection.interval()))
                        .isPresent())
                .toList();
        if (candidates.size() != 1) {
            throw new SecurityException("Whop replacement membership does not identify one workspace");
        }
        return candidates.get(0).getTenantId();
    }

    private void requireReplacement(BillingPlanChangeRequest request, BillingSubscription subscription,
                                    String membershipId, WhopPlanCatalog.Plan selection, JsonNode data) {
        if (request == null || !"requested".equals(request.getStatus())
                || !request.getProviderSubscriptionId().equals(subscription.getProviderSubscriptionId())
                || !request.getTargetPlan().equals(selection.plan())
                || !request.getTargetInterval().equals(selection.interval())) {
            throw new SecurityException("Whop replacement membership does not match the requested plan change");
        }
        var status = required(data.path("status"), "membership status");
        if (!List.of("active", "trialing").contains(status)) {
            throw new IllegalArgumentException("Whop replacement membership is not active");
        }
        if (!customerIds(data).contains(subscription.getProviderCustomerId())) {
            throw new SecurityException("Whop replacement membership customer does not match the workspace");
        }
        if (membershipId.equals(subscription.getProviderSubscriptionId())) {
            throw new IllegalArgumentException("Whop replacement membership must be different");
        }
    }

    private void completeInPlacePlanChange(UUID tenantId, String membershipId,
                                           WhopPlanCatalog.Plan selection) {
        planChangeRequests.findByTenantId(tenantId)
                .filter(request -> "requested".equals(request.getStatus()))
                .filter(request -> membershipId.equals(request.getProviderSubscriptionId()))
                .filter(request -> selection.plan().equals(request.getTargetPlan()))
                .filter(request -> selection.interval().equals(request.getTargetInterval()))
                .ifPresent(request -> {
                    request.complete();
                    planChangeRequests.save(request);
                });
    }

    private static List<String> customerIds(JsonNode data) {
        var userId = data.path("user").path("id").asText("").trim();
        var memberId = data.path("member").path("id").asText("").trim();
        return java.util.stream.Stream.of(userId, memberId)
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    private static String membershipStatus(JsonNode data) {
        var status = required(data.path("status"), "membership status");
        return data.path("cancel_at_period_end").asBoolean(false)
                && List.of("active", "trialing").contains(status) ? "canceling" : status;
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

    private static BigDecimal decimal(JsonNode node, String label) {
        if (!node.isNumber()) throw new IllegalArgumentException("Whop " + label + " is required");
        var value = node.decimalValue();
        if (value.signum() < 0) throw new IllegalArgumentException("Whop " + label + " is invalid");
        return value;
    }

    private static String title(String value) {
        var clean = value == null ? "" : value.trim();
        return clean.isBlank() ? "Sauti" : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private static String addOnTitle(String id) {
        return switch (id) {
            case "agent" -> "Additional agent add-on";
            case "line" -> "Concurrent call line add-on";
            case "number" -> "Business phone number add-on";
            case "voice" -> "Premium voice add-on";
            case "messaging" -> "SMS / WhatsApp messaging add-on";
            default -> throw new IllegalArgumentException("Unsupported Whop add-on");
        };
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
