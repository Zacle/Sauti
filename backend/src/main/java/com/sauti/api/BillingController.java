package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.billing.BillingDtos.BillingUsageResponse;
import com.sauti.billing.BillingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/usage")
    BillingUsageResponse usage(@AuthenticationPrincipal AuthenticatedUser user) {
        return billingService.usage(user.tenantId());
    }
}
