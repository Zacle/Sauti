package com.sauti.api;

import com.sauti.analytics.AnalyticsDtos.AgentSummaryEntry;
import com.sauti.analytics.AnalyticsDtos.AnalyticsSummaryResponse;
import com.sauti.analytics.AnalyticsDtos.DailyVolumeEntry;
import com.sauti.analytics.AnalyticsDtos.LanguageBreakdownEntry;
import com.sauti.analytics.AnalyticsService;
import com.sauti.auth.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    AnalyticsSummaryResponse summary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return analyticsService.summary(user.tenantId(), from, to);
    }

    @GetMapping("/by-language")
    List<LanguageBreakdownEntry> byLanguage(@AuthenticationPrincipal AuthenticatedUser user) {
        return analyticsService.languageBreakdown(user.tenantId());
    }

    @GetMapping("/by-agent")
    List<AgentSummaryEntry> byAgent(@AuthenticationPrincipal AuthenticatedUser user) {
        return analyticsService.agentSummary(user.tenantId());
    }

    /** Returns one entry per calendar day for the last {@code days} days (max 90). */
    @GetMapping("/daily")
    List<DailyVolumeEntry> daily(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "30") int days
    ) {
        return analyticsService.dailyVolume(user.tenantId(), Math.min(Math.max(days, 1), 90));
    }
}
