package com.sauti.billing;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TwoCheckoutPlanCatalog {
    private static final Map<String, Integer> MINUTES = Map.of(
            "launch", 100,
            "growth", 750,
            "scale", 2500
    );
    private final Map<String, Product> products;

    public TwoCheckoutPlanCatalog(
            @Value("${sauti.billing.2checkout.products.launch-monthly.code:}") String launchMonthlyCode,
            @Value("${sauti.billing.2checkout.products.launch-monthly.buy-link:}") String launchMonthlyLink,
            @Value("${sauti.billing.2checkout.products.launch-annual.code:}") String launchAnnualCode,
            @Value("${sauti.billing.2checkout.products.launch-annual.buy-link:}") String launchAnnualLink,
            @Value("${sauti.billing.2checkout.products.growth-monthly.code:}") String growthMonthlyCode,
            @Value("${sauti.billing.2checkout.products.growth-monthly.buy-link:}") String growthMonthlyLink,
            @Value("${sauti.billing.2checkout.products.growth-annual.code:}") String growthAnnualCode,
            @Value("${sauti.billing.2checkout.products.growth-annual.buy-link:}") String growthAnnualLink,
            @Value("${sauti.billing.2checkout.products.scale-monthly.code:}") String scaleMonthlyCode,
            @Value("${sauti.billing.2checkout.products.scale-monthly.buy-link:}") String scaleMonthlyLink,
            @Value("${sauti.billing.2checkout.products.scale-annual.code:}") String scaleAnnualCode,
            @Value("${sauti.billing.2checkout.products.scale-annual.buy-link:}") String scaleAnnualLink) {
        var configured = new LinkedHashMap<String, Product>();
        add(configured, "launch", "monthly", launchMonthlyCode, launchMonthlyLink);
        add(configured, "launch", "annual", launchAnnualCode, launchAnnualLink);
        add(configured, "growth", "monthly", growthMonthlyCode, growthMonthlyLink);
        add(configured, "growth", "annual", growthAnnualCode, growthAnnualLink);
        add(configured, "scale", "monthly", scaleMonthlyCode, scaleMonthlyLink);
        add(configured, "scale", "annual", scaleAnnualCode, scaleAnnualLink);
        this.products = Map.copyOf(configured);
    }

    public Product checkoutSelection(String plan, String interval) {
        var normalizedPlan = normalize(plan);
        var normalizedInterval = normalize(interval);
        if (!MINUTES.containsKey(normalizedPlan)) throw new IllegalArgumentException("Unsupported billing plan");
        if (!("monthly".equals(normalizedInterval) || "annual".equals(normalizedInterval))) {
            throw new IllegalArgumentException("Billing interval must be monthly or annual");
        }
        var product = products.get(normalizedPlan + ":" + normalizedInterval);
        if (product == null || product.code().isBlank() || product.buyLink().isBlank()) {
            throw new IllegalStateException("This billing plan is not configured for 2Checkout");
        }
        return product;
    }

    public Optional<Product> byCode(String code) {
        var normalized = clean(code);
        return products.values().stream()
                .filter(product -> !product.code().isBlank() && product.code().equals(normalized))
                .findFirst();
    }

    private static void add(Map<String, Product> target, String plan, String interval,
                            String code, String buyLink) {
        target.put(plan + ":" + interval,
                new Product(plan, interval, clean(code), clean(buyLink), MINUTES.get(plan)));
    }

    private static String normalize(String value) { return clean(value).toLowerCase(Locale.ROOT); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public record Product(String plan, String interval, String code, String buyLink, int monthlyMinutes) { }
}
