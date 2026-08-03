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

class LemonSqueezyCheckoutServiceTest {
    @Test
    void createsServerMappedCheckoutWithTenantCustomData() throws Exception {
        var captured = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/checkouts", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"data\":{\"attributes\":{\"url\":\"https://store.lemonsqueezy.com/checkout/buy/test\"}}}";
            exchange.sendResponseHeaders(201, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var tenants = mock(TenantRepository.class);
            var tenant = new Tenant("Clinic", "owner@example.com", "GB");
            when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            var service = new LemonSqueezyCheckoutService(
                    tenants,
                    new LemonSqueezyPlanCatalog("101", "", "", "", "", ""),
                    new ObjectMapper(), HttpClient.newHttpClient(), "api-key", "store-1",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "https://sauti.uk/billing?checkout=success");

            var response = service.create(tenant.getId(),
                    new BillingCheckoutGateway.CheckoutRequest("launch", "monthly"));

            assertThat(response.url()).startsWith("https://store.lemonsqueezy.com/");
            var body = new ObjectMapper().readTree(captured.get());
            assertThat(body.at("/data/relationships/variant/data/id").asText()).isEqualTo("101");
            assertThat(body.at("/data/attributes/checkout_data/custom/tenant_id").asText())
                    .isEqualTo(tenant.getId().toString());
            assertThat(body.at("/data/attributes/checkout_data/custom/plan").isMissingNode()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusesCheckoutWithoutServerCredentials() {
        var service = new LemonSqueezyCheckoutService(
                mock(TenantRepository.class), new LemonSqueezyPlanCatalog("101", "", "", "", "", ""),
                new ObjectMapper(), HttpClient.newHttpClient(), "", "", "http://localhost", "https://sauti.uk");

        assertThatThrownBy(() -> service.create(java.util.UUID.randomUUID(),
                new BillingCheckoutGateway.CheckoutRequest("launch", "monthly")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }
}
