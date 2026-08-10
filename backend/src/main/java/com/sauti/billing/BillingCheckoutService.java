package com.sauti.billing;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BillingCheckoutService {
    private final Map<String, BillingCheckoutGateway> gateways;
    private final String activeProvider;
    private final boolean whopSandbox;

    public BillingCheckoutService(List<BillingCheckoutGateway> gateways,
                                  @Value("${sauti.billing.provider:whop}") String activeProvider,
                                  @Value("${sauti.billing.whop.sandbox:false}") boolean whopSandbox) {
        this.gateways = gateways.stream().collect(Collectors.toUnmodifiableMap(
                gateway -> normalize(gateway.provider()), Function.identity()));
        this.activeProvider = normalize(activeProvider);
        this.whopSandbox = whopSandbox;
    }

    public BillingCheckoutGateway.CheckoutResponse create(
            UUID tenantId, BillingCheckoutGateway.CheckoutRequest request) {
        var gateway = gateways.get(activeProvider);
        if (gateway == null) throw new IllegalStateException("Configured billing provider is not available");
        return gateway.create(tenantId, request);
    }

    public BillingCheckoutGateway.AddOnCheckoutResponse createAddOn(
            UUID tenantId, BillingCheckoutGateway.AddOnCheckoutRequest request) {
        var gateway = gateways.get(activeProvider);
        if (gateway == null) throw new IllegalStateException("Configured billing provider is not available");
        return gateway.createAddOn(tenantId, request);
    }

    public BillingCheckoutGateway.CancellationResponse cancel(UUID tenantId) {
        var gateway = gateways.get(activeProvider);
        if (gateway == null) throw new IllegalStateException("Configured billing provider is not available");
        return gateway.cancel(tenantId);
    }

    public BillingCheckoutGateway.CancellationResponse resume(UUID tenantId) {
        var gateway = gateways.get(activeProvider);
        if (gateway == null) throw new IllegalStateException("Configured billing provider is not available");
        return gateway.resume(tenantId);
    }

    public CheckoutStatus status() {
        var gateway = gateways.get(activeProvider);
        var environment = "whop".equals(activeProvider) && whopSandbox ? "sandbox" : "live";
        return new CheckoutStatus(activeProvider, environment, gateway != null && gateway.configured(),
                gateway != null && gateway.addOnsConfigured());
    }

    public record CheckoutStatus(String provider, String environment, boolean configured,
                                 boolean addOnsConfigured) { }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
