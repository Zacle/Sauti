package com.sauti.billing;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhopAddOnCatalog {
    private final Map<String, AddOn> addOns;

    public WhopAddOnCatalog(
            @Value("${sauti.billing.whop.add-ons.agent:}") String agent,
            @Value("${sauti.billing.whop.add-ons.line:}") String line,
            @Value("${sauti.billing.whop.add-ons.number:}") String number,
            @Value("${sauti.billing.whop.add-ons.voice:}") String voice,
            @Value("${sauti.billing.whop.add-ons.messaging:}") String messaging) {
        var configured = new LinkedHashMap<String, AddOn>();
        add(configured, "agent", agent);
        add(configured, "line", line);
        add(configured, "number", number);
        add(configured, "voice", voice);
        add(configured, "messaging", messaging);
        addOns = Map.copyOf(configured);
    }

    public AddOn checkoutSelection(String addOn) {
        var selected = addOns.get(normalize(addOn));
        if (selected == null) throw new IllegalArgumentException("Unsupported billing add-on");
        if (selected.planId().isBlank()) throw new IllegalStateException("This add-on is not configured for Whop");
        return selected;
    }

    public Optional<AddOn> byPlanId(String planId) {
        var id = clean(planId);
        return addOns.values().stream().filter(item -> !item.planId().isBlank() && item.planId().equals(id)).findFirst();
    }

    public boolean fullyConfigured() {
        return addOns.size() == 5 && addOns.values().stream().noneMatch(item -> item.planId().isBlank());
    }

    private static void add(Map<String, AddOn> target, String id, String planId) {
        target.put(id, new AddOn(id, clean(planId)));
    }

    private static String normalize(String value) { return clean(value).toLowerCase(Locale.ROOT); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public record AddOn(String id, String planId) { }
}
