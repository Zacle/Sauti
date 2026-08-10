package com.sauti.billing;

import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingPlanChangeService {
    private static final Set<String> PLANS = Set.of("launch", "growth", "scale");
    private static final Set<String> INTERVALS = Set.of("monthly", "annual");
    private final TenantRepository tenants;
    private final BillingSubscriptionRepository subscriptions;
    private final BillingPlanChangeRequestRepository requests;
    private final ApplicationEventPublisher events;

    public BillingPlanChangeService(TenantRepository tenants, BillingSubscriptionRepository subscriptions,
                                    BillingPlanChangeRequestRepository requests, ApplicationEventPublisher events) {
        this.tenants = tenants;
        this.subscriptions = subscriptions;
        this.requests = requests;
        this.events = events;
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
        events.publishEvent(new BillingPlanChangeRequested(saved, tenant.getBusinessName(), tenant.getEmail()));
        return PlanChangeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BillingPlanChangeRequest pending(UUID tenantId) {
        return requests.findByTenantId(tenantId).filter(item -> "requested".equals(item.getStatus())).orElse(null);
    }

    public record PlanChangeCommand(String plan, String interval) { }

    public record PlanChangeResponse(UUID id, String status, String currentPlan, String targetPlan,
                                     String targetInterval, java.time.OffsetDateTime effectiveAt) {
        static PlanChangeResponse from(BillingPlanChangeRequest request) {
            return new PlanChangeResponse(request.getId(), request.getStatus(), request.getCurrentPlan(),
                    request.getTargetPlan(), request.getTargetInterval(), request.getEffectiveAt());
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
