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
        var whop = mock(BillingCheckoutGateway.class);
        var other = mock(BillingCheckoutGateway.class);
        var tenantId = UUID.randomUUID();
        var request = new BillingCheckoutGateway.CheckoutRequest("growth", "annual");
        var expected = new BillingCheckoutGateway.CheckoutResponse(
                "https://whop.com/checkout/test", "growth", "annual", "whop");
        when(whop.provider()).thenReturn("whop");
        when(other.provider()).thenReturn("other");
        when(whop.create(tenantId, request)).thenReturn(expected);
        var service = new BillingCheckoutService(List.of(other, whop), "whop", false);

        assertThat(service.create(tenantId, request)).isEqualTo(expected);
        verify(whop).create(tenantId, request);
    }

    @Test
    void exposesWhopSandboxWithoutChangingCheckoutRouting() {
        var whop = mock(BillingCheckoutGateway.class);
        when(whop.provider()).thenReturn("whop");
        when(whop.configured()).thenReturn(true);
        var service = new BillingCheckoutService(List.of(whop), "whop", true);

        assertThat(service.status().provider()).isEqualTo("whop");
        assertThat(service.status().environment()).isEqualTo("sandbox");
        assertThat(service.status().configured()).isTrue();
    }
}
