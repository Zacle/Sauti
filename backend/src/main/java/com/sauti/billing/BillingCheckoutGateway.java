package com.sauti.billing;

import java.util.UUID;

public interface BillingCheckoutGateway {
    String provider();

    CheckoutResponse create(UUID tenantId, CheckoutRequest request);

    record CheckoutRequest(String plan, String interval) { }

    record CheckoutResponse(String url, String plan, String interval, String provider) { }
}
