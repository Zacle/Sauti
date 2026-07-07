package com.sauti.analytics;

import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record MetricDelta(double value, double previousValue, double percentChange) {
    }

    public record AnalyticsSummaryResponse(
            long totalCalls,
            long attemptedCalls,
            long connectedCalls,
            long completedCalls,
            long faqAnsweredCalls,
            long transferredCalls,
            long voicemailCalls,
            long bookingCalls,
            long totalDurationSeconds,
            double connectRate,
            double averageDurationSeconds,
            double avgTurnsPerCall,
            double avgSttLatencyMs,
            double avgLlmLatencyMs,
            double avgTtsLatencyMs,
            MetricDelta totalCallsDelta,
            MetricDelta connectRateDelta,
            MetricDelta totalDurationSecondsDelta,
            MetricDelta averageDurationSecondsDelta,
            MetricDelta bookingCallsDelta,
            MetricDelta transferredCallsDelta
    ) {
    }

    public record LanguageBreakdownEntry(String language, long callCount) {
    }

    public record AgentSummaryEntry(
            UUID agentId,
            String agentName,
            long totalCalls,
            long connectedCalls,
            long bookingCalls,
            double connectRate,
            double avgDurationSeconds
    ) {
    }

    public record DailyVolumeEntry(String date, long callCount) {
    }

    public record OutcomeByDayEntry(
            String date,
            long completed,
            long transferred,
            long voicemail,
            long noAnswer,
            long busy,
            long failed,
            long afterHours
    ) {
    }

    public record ConnectRateByDayEntry(String date, long attempts, long connected, double rate) {
    }

    public record FunnelResponse(long attempted, long connected, long completed) {
    }

    public record ChannelBreakdownEntry(
            String channel,
            long totalCalls,
            long connectedCalls,
            long completedCalls,
            long bookingCalls,
            double connectRate
    ) {
    }

    public record TopIntentEntry(String intent, long callCount) {
    }

    public record SentimentByDayEntry(
            String date,
            long analysedCalls,
            double averageScore,
            long positive,
            long neutral,
            long negative,
            long mixed
    ) {
    }

    public record AfterHoursResponse(long totalCalls, long connectedCalls, long completedCalls, List<AfterHoursBehaviorEntry> behaviors) {
    }

    public record AfterHoursBehaviorEntry(String behavior, long callCount) {
    }

    public record IntegrationEventsEntry(String provider, long attempted, long delivered, long failed, long retrying) {
    }
}
