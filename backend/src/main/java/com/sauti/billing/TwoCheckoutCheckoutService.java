package com.sauti.billing;

import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TwoCheckoutCheckoutService implements BillingCheckoutGateway {
    private final TenantRepository tenants;
    private final TwoCheckoutPlanCatalog plans;
    private final TwoCheckoutTenantReference tenantReferences;

    @Autowired
    public TwoCheckoutCheckoutService(TenantRepository tenants, TwoCheckoutPlanCatalog plans,
                                      @Value("${sauti.billing.2checkout.secret-key:}") String secretKey) {
        this.tenants = tenants;
        this.plans = plans;
        this.tenantReferences = new TwoCheckoutTenantReference(secretKey);
    }

    TwoCheckoutCheckoutService(TenantRepository tenants, TwoCheckoutPlanCatalog plans,
                               TwoCheckoutTenantReference tenantReferences) {
        this.tenants = tenants;
        this.plans = plans;
        this.tenantReferences = tenantReferences;
    }

    @Override
    public String provider() { return "2checkout"; }

    @Override
    public CheckoutResponse create(UUID tenantId, CheckoutRequest request) {
        var tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        var product = plans.checkoutSelection(request.plan(), request.interval());
        var reference = tenantReferences.create(tenantId);
        var url = append(product.buyLink(), "CUSTOMERID", reference);
        url = append(url, "REF", reference);
        if (tenant.getEmail() != null && !tenant.getEmail().isBlank()) {
            url = append(url, "EMAIL", tenant.getEmail().trim());
        }
        validate(url);
        return new CheckoutResponse(url, product.plan(), product.interval(), provider());
    }

    private static String append(String url, String name, String value) {
        var separator = url.contains("?") ? "&" : "?";
        return url + separator + encode(name) + "=" + encode(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void validate(String url) {
        var uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("2Checkout buy link must be an HTTPS URL");
        }
    }
}
