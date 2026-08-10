package com.sauti.billing;

import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import java.net.URI;
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

    public BillingPlanChangeService(TenantRepository tenants, BillingSubscriptionRepository subscriptions,
                                    BillingPlanChangeRequestRepository requests) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.requests = requests;
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
        var request = requests.findByTenantId(tenantId)
                .orElseGet(() -> new BillingPlanChangeRequest(tenantId,
                        subscription.getProviderSubscriptionId(), subscription.getPlan(),
                        targetPlan, targetInterval, subscription.getRenewsAt()));
        request.retarget(subscription.getProviderSubscriptionId(), subscription.getPlan(),
                targetPlan, targetInterval, subscription.getRenewsAt());
        var saved = requests.save(request);
        return PlanChangeResponse.from(saved, trustedWhopUrl(subscription.getUpdatePaymentMethodUrl()));
    }

    @Transactional(readOnly = true)
    public BillingPlanChangeRequest pending(UUID tenantId) {
        return requests.findByTenantId(tenantId).filter(item -> "requested".equals(item.getStatus())).orElse(null);
    }

    public record PlanChangeCommand(String plan, String interval) { }

    public record PlanChangeResponse(UUID id, String status, String currentPlan, String targetPlan,
                                     String targetInterval, java.time.OffsetDateTime effectiveAt,
                                     String authorizationUrl) {
        static PlanChangeResponse from(BillingPlanChangeRequest request, String authorizationUrl) {
            return new PlanChangeResponse(request.getId(), request.getStatus(), request.getCurrentPlan(),
                    request.getTargetPlan(), request.getTargetInterval(), request.getEffectiveAt(), authorizationUrl);
        }
    }

    private static String trustedWhopUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Whop plan-change authorization is unavailable for this subscription");
        }
        var uri = URI.create(value);
        var host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !("whop.com".equalsIgnoreCase(host) || host.toLowerCase(Locale.ROOT).endsWith(".whop.com"))) {
            throw new IllegalStateException("Whop returned an invalid plan-change authorization URL");
        }
        return uri.toString();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
