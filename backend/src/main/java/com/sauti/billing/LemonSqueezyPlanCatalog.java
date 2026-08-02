package com.sauti.billing;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LemonSqueezyPlanCatalog {
    private static final Map<String, Integer> MINUTES = Map.of(
            "launch", 100,
            "growth", 750,
            "scale", 2500
    );
    private final Map<String, String> variants;

    public LemonSqueezyPlanCatalog(
            @Value("${sauti.billing.lemon-squeezy.variants.launch-monthly:}") String launchMonthly,
            @Value("${sauti.billing.lemon-squeezy.variants.launch-annual:}") String launchAnnual,
            @Value("${sauti.billing.lemon-squeezy.variants.growth-monthly:}") String growthMonthly,
            @Value("${sauti.billing.lemon-squeezy.variants.growth-annual:}") String growthAnnual,
            @Value("${sauti.billing.lemon-squeezy.variants.scale-monthly:}") String scaleMonthly,
            @Value("${sauti.billing.lemon-squeezy.variants.scale-annual:}") String scaleAnnual) {
        var configured = new LinkedHashMap<String, String>();
        configured.put("launch:monthly", clean(launchMonthly));
        configured.put("launch:annual", clean(launchAnnual));
        configured.put("growth:monthly", clean(growthMonthly));
        configured.put("growth:annual", clean(growthAnnual));
        configured.put("scale:monthly", clean(scaleMonthly));
        configured.put("scale:annual", clean(scaleAnnual));
        this.variants = Map.copyOf(configured);
    }

    public PlanSelection checkoutSelection(String plan, String interval) {
        var normalizedPlan = normalize(plan);
        var normalizedInterval = normalize(interval);
        if (!MINUTES.containsKey(normalizedPlan)) throw new IllegalArgumentException("Unsupported billing plan");
        if (!("monthly".equals(normalizedInterval) || "annual".equals(normalizedInterval))) {
            throw new IllegalArgumentException("Billing interval must be monthly or annual");
        }
        var variantId = variants.getOrDefault(normalizedPlan + ":" + normalizedInterval, "");
        if (variantId.isBlank()) throw new IllegalStateException("This billing plan is not configured for checkout");
        return new PlanSelection(normalizedPlan, normalizedInterval, variantId, MINUTES.get(normalizedPlan));
    }

    public Optional<PlanSelection> byVariant(String variantId) {
        var normalized = clean(variantId);
        return variants.entrySet().stream()
                .filter(entry -> !entry.getValue().isBlank() && entry.getValue().equals(normalized))
                .map(entry -> {
                    var parts = entry.getKey().split(":", 2);
                    return new PlanSelection(parts[0], parts[1], normalized, MINUTES.get(parts[0]));
                })
                .findFirst();
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public record PlanSelection(String plan, String interval, String variantId, int monthlyMinutes) { }
}
