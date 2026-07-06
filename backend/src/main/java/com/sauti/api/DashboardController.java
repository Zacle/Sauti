package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.dashboard.DashboardDtos.DashboardHealthResponse;
import com.sauti.dashboard.DashboardHealthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardHealthService dashboardHealthService;

    public DashboardController(DashboardHealthService dashboardHealthService) {
        this.dashboardHealthService = dashboardHealthService;
    }

    @GetMapping("/health")
    DashboardHealthResponse health(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardHealthService.health(user.tenantId());
    }
}
