package com.sauti.api;

import com.sauti.analytics.AnalyticsDtos.AgentSummaryEntry;
import com.sauti.analytics.AnalyticsDtos.AnalyticsSummaryResponse;
import com.sauti.analytics.AnalyticsDtos.AfterHoursResponse;
import com.sauti.analytics.AnalyticsDtos.ChannelBreakdownEntry;
import com.sauti.analytics.AnalyticsDtos.ConnectRateByDayEntry;
import com.sauti.analytics.AnalyticsDtos.DailyVolumeEntry;
import com.sauti.analytics.AnalyticsDtos.FunnelResponse;
import com.sauti.analytics.AnalyticsDtos.IntegrationEventsEntry;
import com.sauti.analytics.AnalyticsDtos.LanguageBreakdownEntry;
import com.sauti.analytics.AnalyticsDtos.OutcomeByDayEntry;
import com.sauti.analytics.AnalyticsDtos.SentimentByDayEntry;
import com.sauti.analytics.AnalyticsDtos.TopIntentEntry;
import com.sauti.analytics.AnalyticsService;
import com.sauti.auth.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.summary(user.tenantId(), from, to, agentId);
    }

    @GetMapping("/by-language")
    List<LanguageBreakdownEntry> byLanguage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.languageBreakdown(user.tenantId(), from, to, agentId);
    }

    @GetMapping("/by-agent")
    List<AgentSummaryEntry> byAgent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return analyticsService.agentSummary(user.tenantId(), from, to);
    }

    /** Returns one entry per calendar day for the last {@code days} days (max 90). */
    @GetMapping("/daily")
    List<DailyVolumeEntry> daily(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "30") int days
    ) {
        return analyticsService.dailyVolume(user.tenantId(), Math.min(Math.max(days, 1), 90));
    }

    @GetMapping("/outcomes-by-day")
    List<OutcomeByDayEntry> outcomesByDay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.outcomesByDay(user.tenantId(), from, to, agentId);
    }

    @GetMapping("/connect-rate-by-day")
    List<ConnectRateByDayEntry> connectRateByDay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.connectRateByDay(user.tenantId(), from, to, agentId);
    }

    @GetMapping("/funnel")
    FunnelResponse funnel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.funnel(user.tenantId(), from, to, agentId);
    }

    @GetMapping("/by-channel")
    List<ChannelBreakdownEntry> byChannel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.byChannel(user.tenantId(), from, to, agentId);
    }

    @GetMapping("/top-intents")
    List<TopIntentEntry> topIntents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return analyticsService.topIntents(user.tenantId(), from, to, agentId, limit);
    }

    @GetMapping("/sentiment-by-day")
    List<SentimentByDayEntry> sentimentByDay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.sentimentByDay(user.tenantId(), from, to, agentId);
    }

    @GetMapping("/after-hours")
    AfterHoursResponse afterHours(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.afterHours(user.tenantId(), from, to, agentId);
    }

    @GetMapping("/integration-events")
    List<IntegrationEventsEntry> integrationEvents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID agentId
    ) {
        return analyticsService.integrationEvents(user.tenantId(), from, to, agentId);
    }
}
