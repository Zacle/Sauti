package com.sauti.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingPlanChangeService {
    private static final Set<String> PLANS = Set.of("launch", "growth", "scale");
    private static final Set<String> INTERVALS = Set.of("monthly", "annual");
    private final TenantRepository tenants;
    private final BillingSubscriptionRepository subscriptions;
    private final BillingPlanChangeRequestRepository requests;
    private final WhopPlanCatalog plans;
    private final WhopPlanChangeGateway whop;
    private final BillingLedgerService ledger;

    public BillingPlanChangeService(TenantRepository tenants, BillingSubscriptionRepository subscriptions,
                                    BillingPlanChangeRequestRepository requests, WhopPlanCatalog plans,
                                    WhopPlanChangeGateway whop, BillingLedgerService ledger) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.requests = requests;
        this.plans = plans;
        this.whop = whop;
        this.ledger = ledger;
    }

    @Transactional
    public PlanChangeResponse request(UUID tenantId, PlanChangeCommand command) {
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        var subscription = subscriptions.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("Choose a base plan before requesting a plan change"));
        if (!"whop".equals(subscription.getProvider())) {
            throw new IllegalStateException("This workspace subscription belongs to a different billing provider");
        }
        var targetPlan = normalized(command.plan());
        var targetInterval = normalized(command.interval());
        if (!PLANS.contains(targetPlan) || !INTERVALS.contains(targetInterval)) {
            throw new IllegalArgumentException("Choose a supported plan and billing interval");
        }
        if (targetPlan.equals(subscription.getPlan()) && targetInterval.equals(subscription.getBillingInterval())) {
            throw new IllegalArgumentException("This workspace is already on the selected plan");
        }
        var selection = plans.checkoutSelection(targetPlan, targetInterval);
        var request = requests.findByTenantId(tenantId)
                .orElseGet(() -> new BillingPlanChangeRequest(tenantId,
                        subscription.getProviderSubscriptionId(), subscription.getPlan(),
                        targetPlan, targetInterval, subscription.getRenewsAt()));
        var replacedInvoiceId = "scheduled".equals(request.getStatus())
                ? request.getProviderInvoiceId() : null;
        request.retarget(subscription.getProviderSubscriptionId(), subscription.getPlan(),
                targetPlan, targetInterval, subscription.getRenewsAt());
        requests.save(request);
        var transition = whop.prepare(subscription, selection, subscription.getRenewsAt(), replacedInvoiceId);
        if ("adopt".equals(transition.kind())) {
            adopt(tenant, subscription, request, selection, transition.membership());
        } else {
            request.schedule(transition.invoiceId(), selection.planId(), transition.generatedPlanId());
            requests.save(request);
        }
        return PlanChangeResponse.from(request);
    }

    @Transactional(readOnly = true)
    public BillingPlanChangeRequest pending(UUID tenantId) {
        return requests.findByTenantId(tenantId)
                .filter(item -> List.of("requested", "scheduled").contains(item.getStatus())).orElse(null);
    }

    public record PlanChangeCommand(String plan, String interval) { }

    public record PlanChangeResponse(UUID id, String status, String currentPlan, String targetPlan,
                                     String targetInterval, java.time.OffsetDateTime effectiveAt) {
        static PlanChangeResponse from(BillingPlanChangeRequest request) {
            return new PlanChangeResponse(request.getId(), request.getStatus(), request.getCurrentPlan(),
                    request.getTargetPlan(), request.getTargetInterval(), request.getEffectiveAt());
        }
    }

    private void adopt(Tenant tenant, BillingSubscription subscription,
                       BillingPlanChangeRequest request, WhopPlanCatalog.Plan selection,
                       JsonNode membership) {
        var membershipId = required(membership.path("id"), "membership id");
        var status = membershipStatus(membership);
        var renewsAt = timestamp(membership.path("renewal_period_end"));
        subscription.replaceProviderSubscription(membershipId);
        subscription.synchronize(required(membership.path("user").path("id"), "customer id"), membershipId,
                required(membership.path("product").path("id"), "product id"), selection.planId(),
                selection.plan(), selection.interval(), status,
                subscription.isTestMode(), renewsAt, null, null, timestamp(membership.path("updated_at")), "", "",
                membership.path("manage_url").asText(""));
        subscriptions.save(subscription);
        request.complete();
        requests.save(request);
        tenant.applyBillingSubscription(selection.plan(), selection.monthlyMinutes(), renewsAt,
                subscription.getProviderCustomerId());
        tenants.save(tenant);
        var account = ledger.account(tenant.getId());
        account.configure("active", "observe", account.getBillingCurrency(),
                account.getMonthlySpendingLimit(), account.getLowBalanceThreshold());
    }

    private static String membershipStatus(JsonNode membership) {
        var status = required(membership.path("status"), "membership status");
        return membership.path("cancel_at_period_end").asBoolean(false)
                && List.of("active", "trialing").contains(status) ? "canceling" : status;
    }

    private static String required(JsonNode node, String label) {
        var value = node.asText("").trim();
        if (value.isBlank()) throw new IllegalStateException("Whop " + label + " is missing");
        return value;
    }

    private static OffsetDateTime timestamp(JsonNode node) {
        var value = node.asText("").trim();
        return value.isBlank() ? null : OffsetDateTime.parse(value);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
