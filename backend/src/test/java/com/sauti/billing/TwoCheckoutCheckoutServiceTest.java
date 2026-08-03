package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TwoCheckoutCheckoutServiceTest {
    @Test
    void createsHostedCheckoutWithSignedWorkspaceReference() {
        var tenants = mock(TenantRepository.class);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        var service = new TwoCheckoutCheckoutService(tenants, plans(),
                new TwoCheckoutTenantReference("test-secret"));

        var response = service.create(tenant.getId(),
                new BillingCheckoutGateway.CheckoutRequest("launch", "monthly"));

        assertThat(response.provider()).isEqualTo("2checkout");
        assertThat(response.url()).startsWith("https://secure.2checkout.com/order/checkout.php?PRODS=101&");
        assertThat(response.url()).contains("CUSTOMERID=sauti_").contains("REF=sauti_")
                .contains("EMAIL=owner%40example.com");
    }

    @Test
    void refusesCheckoutWhenProviderSecretIsMissing() {
        var tenants = mock(TenantRepository.class);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        var service = new TwoCheckoutCheckoutService(tenants, plans(),
                new TwoCheckoutTenantReference(""));

        assertThatThrownBy(() -> service.create(tenant.getId(),
                new BillingCheckoutGateway.CheckoutRequest("launch", "monthly")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    private TwoCheckoutPlanCatalog plans() {
        return new TwoCheckoutPlanCatalog(
                "launch-monthly", "https://secure.2checkout.com/order/checkout.php?PRODS=101",
                "", "", "", "", "", "", "", "", "", "");
    }
}
