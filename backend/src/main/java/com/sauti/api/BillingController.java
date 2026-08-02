package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.billing.BillingDtos.BillingUsageResponse;
import com.sauti.billing.BillingDtos.BillingAccountResponse;
import com.sauti.billing.BillingService;
import com.sauti.billing.LemonSqueezyCheckoutService;
import com.sauti.billing.LemonSqueezyCheckoutService.CheckoutRequest;
import com.sauti.billing.LemonSqueezyCheckoutService.CheckoutResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingService billingService;
    private final LemonSqueezyCheckoutService checkoutService;

    public BillingController(BillingService billingService, LemonSqueezyCheckoutService checkoutService) {
        this.billingService = billingService;
        this.checkoutService = checkoutService;
    }

    @GetMapping("/usage")
    BillingUsageResponse usage(@AuthenticationPrincipal AuthenticatedUser user) {
        return billingService.usage(user.tenantId());
    }

    @GetMapping("/account")
    BillingAccountResponse account(@AuthenticationPrincipal AuthenticatedUser user) {
        return billingService.account(user.tenantId());
    }

    @PostMapping("/checkout")
    CheckoutResponse checkout(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody CheckoutRequest request) {
        try {
            return checkoutService.create(user.tenantId(), request);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        }
    }
}
