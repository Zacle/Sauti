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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LemonSqueezyCheckoutService {
    private final TenantRepository tenants;
    private final LemonSqueezyPlanCatalog plans;
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final String apiKey;
    private final String storeId;
    private final String apiBaseUrl;
    private final String redirectUrl;

    @Autowired
    public LemonSqueezyCheckoutService(
            TenantRepository tenants,
            LemonSqueezyPlanCatalog plans,
            ObjectMapper objectMapper,
            @Value("${sauti.billing.lemon-squeezy.api-key:}") String apiKey,
            @Value("${sauti.billing.lemon-squeezy.store-id:}") String storeId,
            @Value("${sauti.billing.lemon-squeezy.api-base-url:https://api.lemonsqueezy.com/v1}") String apiBaseUrl,
            @Value("${sauti.billing.lemon-squeezy.checkout-redirect-url:http://localhost:8088/billing?checkout=success}") String redirectUrl) {
        this(tenants, plans, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                apiKey, storeId, apiBaseUrl, redirectUrl);
    }

    LemonSqueezyCheckoutService(TenantRepository tenants, LemonSqueezyPlanCatalog plans,
                                ObjectMapper objectMapper, HttpClient http, String apiKey,
                                String storeId, String apiBaseUrl, String redirectUrl) {
        this.tenants = tenants;
        this.plans = plans;
        this.objectMapper = objectMapper;
        this.http = http;
        this.apiKey = clean(apiKey);
        this.storeId = clean(storeId);
        this.apiBaseUrl = clean(apiBaseUrl).replaceAll("/+$", "");
        this.redirectUrl = clean(redirectUrl);
    }

    public CheckoutResponse create(UUID tenantId, CheckoutRequest request) {
        if (apiKey.isBlank() || storeId.isBlank()) {
            throw new IllegalStateException("Lemon Squeezy checkout is not configured");
        }
        var tenant = tenants.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        var selection = plans.checkoutSelection(request.plan(), request.interval());
        var payload = Map.of("data", Map.of(
                "type", "checkouts",
                "attributes", Map.of(
                        "product_options", Map.of("redirect_url", redirectUrl),
                        "checkout_data", Map.of(
                                "email", tenant.getEmail(),
                                "billing_address", Map.of("country", tenant.getCountryCode()),
                                "custom", Map.of("tenant_id", tenantId.toString())
                        )
                ),
                "relationships", Map.of(
                        "store", Map.of("data", Map.of("type", "stores", "id", storeId)),
                        "variant", Map.of("data", Map.of("type", "variants", "id", selection.variantId()))
                )
        ));
        try {
            var httpRequest = HttpRequest.newBuilder(URI.create(apiBaseUrl + "/checkouts"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/vnd.api+json")
                    .header("Content-Type", "application/vnd.api+json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            var response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Lemon Squeezy could not create a checkout");
            }
            var url = responseUrl(response.body());
            return new CheckoutResponse(url, selection.plan(), selection.interval());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Checkout creation was interrupted", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Lemon Squeezy checkout is temporarily unavailable", exception);
        }
    }

    private String responseUrl(String body) throws Exception {
        var url = objectMapper.readTree(body).path("data").path("attributes").path("url").asText("");
        var uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("Lemon Squeezy returned an invalid checkout URL");
        }
        return uri.toString();
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public record CheckoutRequest(String plan, String interval) { }
    public record CheckoutResponse(String url, String plan, String interval) { }
}
