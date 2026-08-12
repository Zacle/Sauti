package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.dashboard.DashboardDtos.DashboardHealthResponse;
import com.sauti.dashboard.DashboardHealthService;
import com.sauti.dashboard.DashboardSocketTicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardHealthService dashboardHealthService;
    private final DashboardSocketTicketService socketTickets;

    public DashboardController(
            DashboardHealthService dashboardHealthService,
            DashboardSocketTicketService socketTickets
    ) {
        this.dashboardHealthService = dashboardHealthService;
        this.socketTickets = socketTickets;
    }

    @GetMapping("/health")
    DashboardHealthResponse health(@AuthenticationPrincipal AuthenticatedUser user) {
        return dashboardHealthService.health(user.tenantId());
    }

    @PostMapping("/socket-ticket")
    DashboardSocketTicketService.Ticket socketTicket(@AuthenticationPrincipal AuthenticatedUser user) {
        return socketTickets.issue(user);
    }
}
