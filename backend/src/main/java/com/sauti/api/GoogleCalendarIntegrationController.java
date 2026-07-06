package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.calendar.GoogleCalendarIntegrationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/v1/integrations/google-calendar")
public class GoogleCalendarIntegrationController {
    private final GoogleCalendarIntegrationService service;
    private final String dashboardBaseUrl;

    public GoogleCalendarIntegrationController(
            GoogleCalendarIntegrationService service,
            @Value("${sauti.dashboard.base-url}") String dashboardBaseUrl
    ) {
        this.service = service;
        this.dashboardBaseUrl = dashboardBaseUrl;
    }

    @GetMapping("/authorize")
    Map<String, String> authorize(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam UUID agentId
    ) {
        return Map.of("authorizationUrl", service.authorizationUrl(user.tenantId(), agentId));
    }

    @GetMapping("/status")
    GoogleCalendarIntegrationService.Status status(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam UUID agentId
    ) {
        return service.status(user.tenantId(), agentId);
    }

    @GetMapping("/callback")
    RedirectView callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        if (error != null || code == null || state == null) {
            return new RedirectView(dashboardBaseUrl + "/dashboard/integrations?calendar=cancelled");
        }
        var agentId = service.complete(code, state);
        return new RedirectView(
                dashboardBaseUrl + "/dashboard/integrations?agentId=" + agentId + "&calendar=connected"
        );
    }
}
