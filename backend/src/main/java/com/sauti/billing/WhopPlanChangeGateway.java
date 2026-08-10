package com.sauti.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhopPlanChangeGateway {
    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final String apiKey;
    private final String companyId;
    private final String apiBaseUrl;
    private final String apiVersionDate;

    @Autowired
    public WhopPlanChangeGateway(ObjectMapper objectMapper,
            @Value("${sauti.billing.whop.api-key:}") String apiKey,
            @Value("${sauti.billing.whop.company-id:}") String companyId,
            @Value("${sauti.billing.whop.api-base-url:https://api.whop.com/api/v1}") String apiBaseUrl,
            @Value("${sauti.billing.whop.api-version-date:2026-07-20}") String apiVersionDate) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                apiKey, companyId, apiBaseUrl, apiVersionDate);
    }

    WhopPlanChangeGateway(ObjectMapper objectMapper, HttpClient http, String apiKey,
                          String companyId, String apiBaseUrl, String apiVersionDate) {
        this.objectMapper = objectMapper;
        this.http = http;
        this.apiKey = clean(apiKey);
        this.companyId = clean(companyId);
        this.apiBaseUrl = clean(apiBaseUrl).replaceAll("/+$", "");
        this.apiVersionDate = clean(apiVersionDate);
    }

    PlanTransition prepare(BillingSubscription subscription, WhopPlanCatalog.Plan target,
                           OffsetDateTime effectiveAt) {
        requireConfigured();
        if (effectiveAt == null || !effectiveAt.isAfter(OffsetDateTime.now())) {
            throw new IllegalStateException("Whop did not provide a future renewal date for this subscription");
        }
        var current = get("/memberships/" + safeId(subscription.getProviderSubscriptionId()));
        verifyCurrent(subscription, current);
        var replacements = matchingMemberships(subscription.getProviderCustomerId(), target.planId()).stream()
                .filter(item -> !subscription.getProviderSubscriptionId().equals(item.path("id").asText()))
                .toList();
        if (replacements.size() > 1) {
            throw new IllegalStateException("More than one active " + target.plan()
                    + " membership already exists in Whop. Cancel the duplicate test memberships before changing plans.");
        }
        if (replacements.size() == 1) {
            verifyReplacement(subscription, replacements.get(0), target.planId());
            cancelAtPeriodEndIfNeeded(current);
            return PlanTransition.adopt(replacements.get(0));
        }

        var memberId = required(current.path("member").path("id"), "membership member");
        var paymentMethodId = latestPaymentMethod(memberId);
        var targetPlan = get("/plans/" + safeId(target.planId()));
        var invoice = createInvoice(targetPlan, memberId, paymentMethodId, effectiveAt);
        try {
            cancelAtPeriodEndIfNeeded(current);
        } catch (RuntimeException exception) {
            voidInvoice(invoice.path("id").asText(""));
            throw exception;
        }
        return PlanTransition.scheduled(required(invoice.path("id"), "invoice id"),
                required(invoice.path("current_plan").path("id"), "invoice plan id"));
    }

    private List<JsonNode> matchingMemberships(String userId, String planId) {
        var query = "/memberships?company_id=" + encoded(companyId)
                + "&first=10&user_ids[]=" + encoded(userId)
                + "&plan_ids[]=" + encoded(planId)
                + "&statuses[]=active&statuses[]=trialing";
        var data = get(query).path("data");
        if (!data.isArray()) throw new IllegalStateException("Whop returned an invalid membership list");
        return java.util.stream.StreamSupport.stream(data.spliterator(), false).toList();
    }

    private String latestPaymentMethod(String memberId) {
        var data = get("/payment_methods?member_id=" + encoded(memberId) + "&first=10&direction=desc").path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("No saved Whop payment method is available for this plan change");
        }
        return required(data.get(0).path("id"), "payment method");
    }

    private JsonNode createInvoice(JsonNode plan, String memberId, String paymentMethodId,
                                   OffsetDateTime effectiveAt) {
        var productId = required(plan.path("product").path("id"), "plan product");
        var planInput = new LinkedHashMap<String, Object>();
        planInput.put("initial_price", 0);
        planInput.put("renewal_price", decimal(plan.path("renewal_price"), "renewal price"));
        planInput.put("billing_period", integer(plan.path("billing_period"), "billing period"));
        planInput.put("description", plan.path("description").asText("Sauti subscription"));
        planInput.put("unlimited_stock", true);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("company_id", companyId);
        payload.put("product_id", productId);
        payload.put("plan", planInput);
        payload.put("collection_method", "charge_automatically");
        payload.put("member_id", memberId);
        payload.put("payment_method_id", paymentMethodId);
        payload.put("automatically_finalizes_at", effectiveAt.toString());
        payload.put("subscription_billing_anchor_at", effectiveAt.toString());
        payload.put("charge_buyer_fee", false);
        return post("/invoices", payload);
    }

    private void cancelAtPeriodEndIfNeeded(JsonNode membership) {
        if (membership.path("cancel_at_period_end").asBoolean(false)
                || "canceling".equals(membership.path("status").asText())) return;
        post("/memberships/" + safeId(required(membership.path("id"), "membership id")) + "/cancel",
                Map.of("cancellation_mode", "at_period_end"));
    }

    private void voidInvoice(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) return;
        try { post("/invoices/" + safeId(invoiceId) + "/void", Map.of()); }
        catch (RuntimeException ignored) { /* best-effort compensation; Whop retains the audit trail */ }
    }

    private void verifyCurrent(BillingSubscription subscription, JsonNode membership) {
        if (!subscription.getProviderCustomerId().equals(membership.path("user").path("id").asText())
                || !companyId.equals(membership.path("company").path("id").asText())
                || !subscription.getProviderProductId().equals(membership.path("product").path("id").asText())) {
            throw new SecurityException("Whop membership does not match the synchronized workspace subscription");
        }
    }

    private void verifyReplacement(BillingSubscription subscription, JsonNode membership, String targetPlanId) {
        if (!subscription.getProviderCustomerId().equals(membership.path("user").path("id").asText())
                || !companyId.equals(membership.path("company").path("id").asText())
                || !subscription.getProviderProductId().equals(membership.path("product").path("id").asText())
                || !targetPlanId.equals(membership.path("plan").path("id").asText())) {
            throw new SecurityException("Whop target membership does not match this workspace and plan");
        }
    }

    private JsonNode get(String path) { return send(path, "GET", null); }
    private JsonNode post(String path, Object body) { return send(path, "POST", body); }

    private JsonNode send(String path, String method, Object body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(apiBaseUrl + path))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Api-Version-Date", apiVersionDate)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", UUID.randomUUID().toString());
            var request = "POST".equals(method)
                    ? builder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build()
                    : builder.GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(providerMessage(response.statusCode(), response.body()));
            }
            return response.body() == null || response.body().isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Whop plan change was interrupted", exception);
        } catch (IllegalStateException | SecurityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Whop plan changes are temporarily unavailable", exception);
        }
    }

    private String providerMessage(int status, String body) {
        var detail = "";
        try { detail = objectMapper.readTree(body).path("error").path("message").asText(""); }
        catch (Exception ignored) { }
        return switch (status) {
            case 401 -> "Whop rejected the billing API key";
            case 403 -> "The Whop API key needs membership, plan, payment-method, and invoice permissions";
            case 404 -> "Whop could not find the synchronized membership or selected plan";
            case 400, 422 -> detail.isBlank() ? "Whop rejected the scheduled plan change" : "Whop rejected the plan change: " + detail;
            default -> "Whop plan changes are temporarily unavailable";
        };
    }

    private void requireConfigured() {
        if (apiKey.isBlank() || companyId.isBlank() || apiVersionDate.isBlank()) {
            throw new IllegalStateException("Whop plan changes are not configured");
        }
    }

    private static String safeId(String value) {
        var id = clean(value);
        if (!id.matches("[A-Za-z0-9_=-]+")) throw new IllegalStateException("Whop reference is invalid");
        return id;
    }
    private static String required(JsonNode node, String label) {
        var value = node.asText("").trim();
        if (value.isBlank()) throw new IllegalStateException("Whop " + label + " is missing");
        return value;
    }
    private static Number decimal(JsonNode node, String label) {
        if (!node.isNumber()) throw new IllegalStateException("Whop " + label + " is missing");
        return node.decimalValue();
    }
    private static int integer(JsonNode node, String label) {
        if (!node.canConvertToInt() || node.asInt() <= 0) throw new IllegalStateException("Whop " + label + " is invalid");
        return node.asInt();
    }
    private static String encoded(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public record PlanTransition(String kind, String invoiceId, String generatedPlanId,
                                 JsonNode membership) {
        static PlanTransition scheduled(String invoiceId, String generatedPlanId) {
            return new PlanTransition("scheduled", invoiceId, generatedPlanId, null);
        }
        static PlanTransition adopt(JsonNode membership) {
            return new PlanTransition("adopt", null, null, membership);
        }
    }
}
