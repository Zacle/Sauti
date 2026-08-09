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

    public BillingCheckoutService(List<BillingCheckoutGateway> gateways,
                                  @Value("${sauti.billing.provider:whop}") String activeProvider) {
        this.gateways = gateways.stream().collect(Collectors.toUnmodifiableMap(
                gateway -> normalize(gateway.provider()), Function.identity()));
        this.activeProvider = normalize(activeProvider);
    }

    public BillingCheckoutGateway.CheckoutResponse create(
            UUID tenantId, BillingCheckoutGateway.CheckoutRequest request) {
        var gateway = gateways.get(activeProvider);
        if (gateway == null) throw new IllegalStateException("Configured billing provider is not available");
        return gateway.create(tenantId, request);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
