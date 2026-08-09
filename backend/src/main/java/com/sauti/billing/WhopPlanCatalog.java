package com.sauti.billing;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhopPlanCatalog {
    private static final Map<String, Integer> MINUTES = Map.of(
            "launch", 100, "growth", 750, "scale", 2500);
    private final Map<String, Plan> plans;

    public WhopPlanCatalog(
            @Value("${sauti.billing.whop.plans.launch-monthly:}") String launchMonthly,
            @Value("${sauti.billing.whop.plans.launch-annual:}") String launchAnnual,
            @Value("${sauti.billing.whop.plans.growth-monthly:}") String growthMonthly,
            @Value("${sauti.billing.whop.plans.growth-annual:}") String growthAnnual,
            @Value("${sauti.billing.whop.plans.scale-monthly:}") String scaleMonthly,
            @Value("${sauti.billing.whop.plans.scale-annual:}") String scaleAnnual) {
        var configured = new LinkedHashMap<String, Plan>();
        add(configured, "launch", "monthly", launchMonthly);
        add(configured, "launch", "annual", launchAnnual);
        add(configured, "growth", "monthly", growthMonthly);
        add(configured, "growth", "annual", growthAnnual);
        add(configured, "scale", "monthly", scaleMonthly);
        add(configured, "scale", "annual", scaleAnnual);
        plans = Map.copyOf(configured);
    }

    public Plan checkoutSelection(String plan, String interval) {
        var key = normalize(plan) + ":" + normalize(interval);
        var selected = plans.get(key);
        if (selected == null) throw new IllegalArgumentException("Unsupported billing plan or interval");
        if (selected.planId().isBlank()) throw new IllegalStateException("This billing plan is not configured for Whop");
        return selected;
    }

    public Optional<Plan> byPlanId(String planId) {
        var id = clean(planId);
        return plans.values().stream().filter(plan -> !plan.planId().isBlank() && plan.planId().equals(id)).findFirst();
    }

    public boolean fullyConfigured() {
        return plans.size() == 6 && plans.values().stream().noneMatch(plan -> plan.planId().isBlank());
    }

    private static void add(Map<String, Plan> target, String plan, String interval, String planId) {
        target.put(plan + ":" + interval, new Plan(plan, interval, clean(planId), MINUTES.get(plan)));
    }

    private static String normalize(String value) { return clean(value).toLowerCase(Locale.ROOT); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public record Plan(String plan, String interval, String planId, int monthlyMinutes) { }
}
