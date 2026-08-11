package com.sauti.api;

import com.sauti.billing.BillingReadinessService;
import com.sauti.billing.BillingReadinessService.Readiness;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing")
public class AdminBillingController {
    private final BillingReadinessService readiness;

    public AdminBillingController(BillingReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/readiness")
    Readiness readiness() {
        return readiness.readiness();
    }
}
