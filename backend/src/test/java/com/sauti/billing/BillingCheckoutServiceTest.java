package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingCheckoutServiceTest {
    @Test
    void routesCheckoutToConfiguredProviderWithoutChangingThePublicContract() {
        var twoCheckout = mock(BillingCheckoutGateway.class);
        var lemonSqueezy = mock(BillingCheckoutGateway.class);
        var tenantId = UUID.randomUUID();
        var request = new BillingCheckoutGateway.CheckoutRequest("growth", "annual");
        var expected = new BillingCheckoutGateway.CheckoutResponse(
                "https://secure.2checkout.com/test", "growth", "annual", "2checkout");
        when(twoCheckout.provider()).thenReturn("2checkout");
        when(lemonSqueezy.provider()).thenReturn("lemon_squeezy");
        when(twoCheckout.create(tenantId, request)).thenReturn(expected);
        var service = new BillingCheckoutService(List.of(lemonSqueezy, twoCheckout), "2checkout");

        assertThat(service.create(tenantId, request)).isEqualTo(expected);
        verify(twoCheckout).create(tenantId, request);
    }
}
