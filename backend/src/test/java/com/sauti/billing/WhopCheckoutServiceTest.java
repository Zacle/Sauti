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
    void cancelsOnlyTheWorkspaceMembershipAtTheEndOfItsPaidPeriod() throws Exception {
        var capturedBody = new AtomicReference<String>();
        var capturedPath = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/memberships/mem_keep/cancel", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"id\":\"mem_keep\",\"status\":\"canceling\"}";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var tenants = mock(TenantRepository.class);
            var subscriptions = mock(BillingSubscriptionRepository.class);
            var tenant = new Tenant("Clinic", "owner@example.com", "GB");
            var subscription = new BillingSubscription(tenant.getId(), "whop", "mem_keep");
            subscription.synchronize("user_1", "mem_keep", "prod_sauti", "plan_growth_monthly",
                    "growth", "monthly", "active", true, null, null, null,
                    java.time.OffsetDateTime.parse("2026-08-10T10:00:00Z"), "", "",
                    "https://whop.com/billing/manage/mem_keep");
            when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription));
            var service = new WhopCheckoutService(tenants, plans(), addOns(), subscriptions,
                    mock(BillingAddOnSubscriptionRepository.class), new ObjectMapper(), HttpClient.newHttpClient(),
                    "api-key", "biz_sauti", "reference-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                    "2026-07-20", "https://sauti.uk/billing?checkout=success");

            var response = service.cancel(tenant.getId());

            assertThat(response.status()).isEqualTo("canceling");
            assertThat(capturedPath.get()).isEqualTo("/api/v1/memberships/mem_keep/cancel");
            assertThat(new ObjectMapper().readTree(capturedBody.get()).path("cancellation_mode").asText())
                    .isEqualTo("at_period_end");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resumesRenewalForTheWorkspaceMembership() throws Exception {
        var capturedBody = new AtomicReference<String>();
        var capturedPath = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/memberships/mem_keep/resume", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"id\":\"mem_keep\",\"status\":\"active\",\"cancel_at_period_end\":false}";
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var tenants = mock(TenantRepository.class);
            var subscriptions = mock(BillingSubscriptionRepository.class);
            var tenant = new Tenant("Clinic", "owner@example.com", "GB");
            var subscription = new BillingSubscription(tenant.getId(), "whop", "mem_keep");
            subscription.synchronize("user_1", "mem_keep", "prod_sauti", "plan_growth_monthly",
                    "growth", "monthly", "canceling", true, null, null, null,
                    java.time.OffsetDateTime.parse("2026-09-09T10:00:00Z"), "", "",
                    "https://whop.com/billing/manage/mem_keep");
            when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription));
            var service = new WhopCheckoutService(tenants, plans(), addOns(), subscriptions,
                    mock(BillingAddOnSubscriptionRepository.class), new ObjectMapper(), HttpClient.newHttpClient(),
                    "api-key", "biz_sauti", "reference-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                    "2026-07-20", "https://sauti.uk/billing?checkout=success");

            var response = service.resume(tenant.getId());

            assertThat(response.status()).isEqualTo("active");
            assertThat(capturedPath.get()).isEqualTo("/api/v1/memberships/mem_keep/resume");
            assertThat(new ObjectMapper().readTree(capturedBody.get()).isObject()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void refusesASecondBaseCheckoutForAnExistingWorkspaceSubscription() {
        var tenants = mock(TenantRepository.class);
        var subscriptions = mock(BillingSubscriptionRepository.class);
        var tenant = new Tenant("Clinic", "owner@example.com", "GB");
        var subscription = new BillingSubscription(tenant.getId(), "whop", "mem_growth");
        subscription.synchronize("user_1", "mem_growth", "prod_sauti", "plan_growth_monthly",
                "growth", "monthly", "active", true, null, null, null,
                java.time.OffsetDateTime.parse("2026-08-10T10:00:00Z"), "", "",
                "https://whop.com/billing/manage/mem_growth");
        when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription));
        var service = new WhopCheckoutService(tenants, plans(), addOns(), subscriptions,
                mock(BillingAddOnSubscriptionRepository.class), new ObjectMapper(), HttpClient.newHttpClient(),
                "api-key", "biz_sauti", "reference-secret", "http://127.0.0.1:1/api/v1",
                "2026-07-20", "https://sauti.uk/billing?checkout=success");

        assertThatThrownBy(() -> service.create(tenant.getId(),
                new BillingCheckoutGateway.CheckoutRequest("scale", "monthly")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sauti plan change request");
    }

    @Test
    void createsAReactivationCheckoutAfterPaidAccessHasEnded() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/checkout_configurations", exchange -> {
            var response = "{\"id\":\"ch_reactivate\",\"purchase_url\":\"https://whop.com/checkout/plan_launch?session=ch_reactivate\"}";
            exchange.sendResponseHeaders(201, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var tenants = mock(TenantRepository.class);
            var subscriptions = mock(BillingSubscriptionRepository.class);
            var tenant = new Tenant("Clinic", "owner@example.com", "GB");
            var ended = new BillingSubscription(tenant.getId(), "whop", "mem_ended");
            ended.synchronize("user_1", "mem_ended", "prod_sauti", "plan_growth_monthly",
                    "growth", "monthly", "expired", true,
                    java.time.OffsetDateTime.parse("2026-08-01T10:00:00Z"),
                    java.time.OffsetDateTime.parse("2026-08-01T10:00:00Z"), null,
                    java.time.OffsetDateTime.parse("2026-08-01T10:00:00Z"), "", "", "");
            when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(ended));
            var service = new WhopCheckoutService(tenants, plans(), addOns(), subscriptions,
                    mock(BillingAddOnSubscriptionRepository.class), new ObjectMapper(), HttpClient.newHttpClient(),
                    "api-key", "biz_sauti", "reference-secret",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                    "2026-07-20", "https://sauti.uk/billing?checkout=success");

            var response = service.create(tenant.getId(),
                    new BillingCheckoutGateway.CheckoutRequest("launch", "monthly"));

            assertThat(response.url()).contains("ch_reactivate");
            assertThat(response.plan()).isEqualTo("launch");
        } finally {
            server.stop(0);
        }
    }

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
            var service = service(tenants,
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
        var service = service(mock(TenantRepository.class),
                HttpClient.newHttpClient(), "", "", "", "http://localhost", "2026-07-20", "https://sauti.uk");
        assertThatThrownBy(() -> service.create(java.util.UUID.randomUUID(),
                new BillingCheckoutGateway.CheckoutRequest("launch", "monthly")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
    }

    @Test
    void createsIndependentAddOnCheckoutWithTrustedMetadata() throws Exception {
        var captured = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/checkout_configurations", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"purchase_url\":\"https://whop.com/checkout/plan_agent?session=ch_addon\"}";
            exchange.sendResponseHeaders(201, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var tenants = mock(TenantRepository.class);
            var subscriptions = mock(BillingSubscriptionRepository.class);
            var addOnSubscriptions = mock(BillingAddOnSubscriptionRepository.class);
            var tenant = new Tenant("Clinic", "owner@example.com", "GB");
            var subscription = new BillingSubscription(tenant.getId(), "whop", "mem_growth");
            subscription.synchronize("user_1", "mem_growth", "prod_sauti", "plan_growth_monthly",
                    "growth", "monthly", "active", true, null, null, null,
                    java.time.OffsetDateTime.parse("2026-08-10T10:00:00Z"), "", "",
                    "https://whop.com/billing/manage/mem_growth");
            when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            when(subscriptions.findByTenantId(tenant.getId())).thenReturn(Optional.of(subscription));
            when(addOnSubscriptions.findAllByTenantId(tenant.getId())).thenReturn(java.util.List.of());
            var service = new WhopCheckoutService(tenants, plans(), addOns(), subscriptions,
                    addOnSubscriptions, new ObjectMapper(), HttpClient.newHttpClient(), "api-key", "biz_sauti",
                    "reference-secret", "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                    "2026-07-20", "https://sauti.uk/billing?checkout=success");

            var response = service.createAddOn(tenant.getId(),
                    new BillingCheckoutGateway.AddOnCheckoutRequest("agent"));

            assertThat(response.addOn()).isEqualTo("agent");
            var body = new ObjectMapper().readTree(captured.get());
            assertThat(body.path("plan_id").asText()).isEqualTo("plan_agent");
            assertThat(body.path("metadata").path("sauti_purchase_type").asText()).isEqualTo("add_on");
            assertThat(body.path("metadata").path("sauti_add_on").asText()).isEqualTo("agent");
            assertThat(new WhopTenantReference("reference-secret").verify(
                    body.path("metadata").path("sauti_tenant_reference").asText())).isEqualTo(tenant.getId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void explainsAuthenticationFailureWithoutReturningProviderPayload() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/checkout_configurations", exchange -> {
            var response = "{\"error\":{\"type\":\"unauthorized\",\"message\":\"Authentication failed\"}}";
            exchange.sendResponseHeaders(401, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var tenants = mock(TenantRepository.class);
            var tenant = new Tenant("Clinic", "owner@example.com", "GB");
            when(tenants.findById(tenant.getId())).thenReturn(Optional.of(tenant));
            var service = service(tenants,
                    HttpClient.newHttpClient(), "invalid-key", "biz_sauti", "reference-secret",
                    "https://sandbox-api.whop.com/api/v1".replace("https://sandbox-api.whop.com",
                            "http://127.0.0.1:" + server.getAddress().getPort()),
                    "2026-07-20", "https://sauti.uk/billing?checkout=success");

            assertThatThrownBy(() -> service.create(tenant.getId(),
                    new BillingCheckoutGateway.CheckoutRequest("launch", "monthly")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Whop rejected the API key")
                    .satisfies(exception -> assertThat(exception.getMessage())
                            .doesNotContain("Authentication failed"));
        } finally {
            server.stop(0);
        }
    }

    private WhopPlanCatalog plans() {
        return new WhopPlanCatalog(
                "plan_launch_monthly", "plan_launch_annual",
                "plan_growth_monthly", "plan_growth_annual",
                "plan_scale_monthly", "plan_scale_annual");
    }

    private WhopCheckoutService service(TenantRepository tenants, HttpClient http, String apiKey,
                                        String companyId, String referenceSecret, String apiBase,
                                        String version, String redirect) {
        var subscriptions = mock(BillingSubscriptionRepository.class);
        when(subscriptions.findByTenantId(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        return new WhopCheckoutService(tenants, plans(), addOns(), subscriptions,
                mock(BillingAddOnSubscriptionRepository.class), new ObjectMapper(), http, apiKey,
                companyId, referenceSecret, apiBase, version, redirect);
    }

    private WhopAddOnCatalog addOns() {
        return new WhopAddOnCatalog("plan_agent", "plan_line", "plan_number", "plan_voice", "plan_messaging");
    }
}
