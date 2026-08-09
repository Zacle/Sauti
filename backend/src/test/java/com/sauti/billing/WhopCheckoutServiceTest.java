package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WhopCheckoutServiceTest {
    @Test
    void createsCheckoutConfigurationWithServerPlanAndSignedWorkspaceMetadata() throws Exception {
        var captured = new AtomicReference<String>();
        var version = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/checkout_configurations", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            version.set(exchange.getRequestHeaders().getFirst("Api-Version-Date"));
            var response = "{\"id\":\"ch_test\",\"purchase_url\":\"https://whop.com/checkout/plan_test?session=ch_test\"}";
            exchange.sendResponseHeaders(201, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var tenants = mock(TenantRepository.class);
            var tenant = new Tenant("Clinic", "owner@example.com", "GB");
            when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            var service = new WhopCheckoutService(tenants, plans(), new ObjectMapper(),
                    HttpClient.newHttpClient(), "api-key", "biz_sauti", "reference-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                    "2026-07-20", "https://sauti.uk/billing?checkout=success");

            var response = service.create(tenant.getId(),
                    new BillingCheckoutGateway.CheckoutRequest("launch", "monthly"));

            assertThat(response.provider()).isEqualTo("whop");
            assertThat(response.url()).startsWith("https://whop.com/checkout/");
            assertThat(version.get()).isEqualTo("2026-07-20");
            var body = new ObjectMapper().readTree(captured.get());
            assertThat(body.path("company_id").asText()).isEqualTo("biz_sauti");
            assertThat(body.path("plan_id").asText()).isEqualTo("plan_launch_monthly");
            var reference = body.path("metadata").path("sauti_tenant_reference").asText();
            assertThat(new WhopTenantReference("reference-secret").verify(reference)).isEqualTo(tenant.getId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusesCheckoutWithoutServerCredentials() {
        var service = new WhopCheckoutService(mock(TenantRepository.class), plans(), new ObjectMapper(),
                HttpClient.newHttpClient(), "", "", "", "http://localhost", "2026-07-20", "https://sauti.uk");
        assertThatThrownBy(() -> service.create(java.util.UUID.randomUUID(),
                new BillingCheckoutGateway.CheckoutRequest("launch", "monthly")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
    }

    private WhopPlanCatalog plans() {
        return new WhopPlanCatalog(
                "plan_launch_monthly", "plan_launch_annual",
                "plan_growth_monthly", "plan_growth_annual",
                "plan_scale_monthly", "plan_scale_annual");
    }
}
