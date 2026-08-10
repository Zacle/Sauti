package com.sauti.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class WhopCheckoutService implements BillingCheckoutGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhopCheckoutService.class);
    private final TenantRepository tenants;
    private final WhopPlanCatalog plans;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final WhopTenantReference tenantReferences;
    private final String apiKey;
    private final String companyId;
    private final String apiBaseUrl;
    private final String apiVersionDate;
    private final String redirectUrl;
    private final boolean tenantReferenceConfigured;

    @Autowired
    public WhopCheckoutService(
            TenantRepository tenants, WhopPlanCatalog plans, ObjectMapper objectMapper,
            @Value("${sauti.billing.whop.api-key:}") String apiKey,
            @Value("${sauti.billing.whop.company-id:}") String companyId,
            @Value("${sauti.billing.whop.tenant-reference-secret:}") String tenantReferenceSecret,
            @Value("${sauti.billing.whop.api-base-url:https://api.whop.com/api/v1}") String apiBaseUrl,
            @Value("${sauti.billing.whop.api-version-date:2026-07-20}") String apiVersionDate,
            @Value("${sauti.billing.whop.checkout-redirect-url:http://localhost:8088/billing?checkout=success}") String redirectUrl) {
        this(tenants, plans, objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                apiKey, companyId, tenantReferenceSecret, apiBaseUrl, apiVersionDate, redirectUrl);
    }

    WhopCheckoutService(TenantRepository tenants, WhopPlanCatalog plans, ObjectMapper objectMapper,
                        HttpClient http, String apiKey, String companyId, String tenantReferenceSecret,
                        String apiBaseUrl, String apiVersionDate,
                        String redirectUrl) {
        this.tenants = tenants;
        this.plans = plans;
        this.objectMapper = objectMapper;
        this.http = http;
        this.apiKey = clean(apiKey);
        this.companyId = clean(companyId);
        this.apiBaseUrl = clean(apiBaseUrl).replaceAll("/+$", "");
        this.apiVersionDate = clean(apiVersionDate);
        this.redirectUrl = clean(redirectUrl);
        this.tenantReferenceConfigured = !clean(tenantReferenceSecret).isBlank();
        this.tenantReferences = new WhopTenantReference(tenantReferenceSecret);
    }

    @Override public String provider() { return "whop"; }

    @Override
    public boolean configured() {
        return !apiKey.isBlank() && !companyId.isBlank() && !apiVersionDate.isBlank()
                && !redirectUrl.isBlank() && tenantReferenceConfigured && plans.fullyConfigured();
    }

    @Override
    public CheckoutResponse create(UUID tenantId, CheckoutRequest request) {
        requireConfigured();
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        var selection = plans.checkoutSelection(request.plan(), request.interval());
        var payload = Map.of(
                "company_id", companyId,
                "plan_id", selection.planId(),
                "redirect_url", redirectUrl,
                "metadata", Map.of("sauti_tenant_reference", tenantReferences.create(tenant.getId())));
        try {
            var requestId = UUID.randomUUID().toString();
            var httpRequest = HttpRequest.newBuilder(URI.create(apiBaseUrl + "/checkout_configurations"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Api-Version-Date", apiVersionDate)
                    .header("Idempotency-Key", requestId)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            var response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Whop checkout creation failed with status {} and type {}",
                        response.statusCode(), providerErrorType(response.body()));
                throw providerFailure(response.statusCode());
            }
            var url = checkoutUrl(response.body());
            return new CheckoutResponse(url, selection.plan(), selection.interval(), provider());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Checkout creation was interrupted", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Whop checkout is temporarily unavailable", exception);
        }
    }

    private String checkoutUrl(String body) throws Exception {
        var url = objectMapper.readTree(body).path("purchase_url").asText("");
        var uri = URI.create(url);
        var host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || !("whop.com".equalsIgnoreCase(host) || host.toLowerCase().endsWith(".whop.com"))) {
            throw new IllegalStateException("Whop returned an invalid checkout URL");
        }
        return uri.toString();
    }

    private IllegalStateException providerFailure(int statusCode) {
        var environment = apiBaseUrl.contains("sandbox-api.whop.com") ? "Whop Sandbox" : "Whop";
        return switch (statusCode) {
            case 400, 422 -> new IllegalStateException(environment
                    + " rejected the checkout details. Verify the company ID, plan ID, and redirect URL.");
            case 401 -> new IllegalStateException(environment
                    + " rejected the API key. Use a key created in the same environment as the configured plans.");
            case 403 -> new IllegalStateException(environment
                    + " API key does not have permission to create checkout configurations.");
            case 404 -> new IllegalStateException(environment
                    + " could not find the selected plan. Use a plan from the configured company and environment.");
            case 429 -> new IllegalStateException(environment
                    + " is temporarily rate limiting checkout requests. Please try again shortly.");
            default -> new IllegalStateException(environment + " checkout is temporarily unavailable.");
        };
    }

    private String providerErrorType(String body) {
        try {
            return objectMapper.readTree(body).path("error").path("type").asText("unknown");
        } catch (Exception ignored) {
            return "unparseable";
        }
    }

    private void requireConfigured() {
        if (!configured()) {
            throw new IllegalStateException("Whop checkout is not configured");
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
