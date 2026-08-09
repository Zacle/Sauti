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

@Service
public class WhopCheckoutService implements BillingCheckoutGateway {
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
        this.tenantReferences = new WhopTenantReference(tenantReferenceSecret);
    }

    @Override public String provider() { return "whop"; }

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
                throw new IllegalStateException("Whop could not create a checkout");
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

    private void requireConfigured() {
        if (apiKey.isBlank() || companyId.isBlank() || apiVersionDate.isBlank() || redirectUrl.isBlank()) {
            throw new IllegalStateException("Whop checkout is not configured");
        }
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
