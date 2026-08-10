package com.sauti.billing;

import java.util.UUID;

public interface BillingCheckoutGateway {
    String provider();

    default boolean configured() { return true; }

    default boolean addOnsConfigured() { return false; }

    CheckoutResponse create(UUID tenantId, CheckoutRequest request);

    default AddOnCheckoutResponse createAddOn(UUID tenantId, AddOnCheckoutRequest request) {
        throw new IllegalStateException("Add-on checkout is not available for this billing provider");
    }

    default CancellationResponse cancel(UUID tenantId) {
        throw new IllegalStateException("Subscription cancellation is not available for this billing provider");
    }

    default CancellationResponse resume(UUID tenantId) {
        throw new IllegalStateException("Subscription renewal cannot be resumed for this billing provider");
    }

    record CheckoutRequest(String plan, String interval) { }

    record CheckoutResponse(String url, String plan, String interval, String provider) { }

    record AddOnCheckoutRequest(String addOn) { }

    record AddOnCheckoutResponse(String url, String addOn, String provider) { }

    record CancellationResponse(String provider, String status) { }
}
