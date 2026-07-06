package com.sauti.analytics;

import java.util.List;
import java.util.UUID;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record AnalyticsSummaryResponse(
            long totalCalls,
            long faqAnsweredCalls,
            long transferredCalls,
            long voicemailCalls,
            long bookingCalls,
            double averageDurationSeconds,
            double avgTurnsPerCall,
            double avgSttLatencyMs,
            double avgLlmLatencyMs,
            double avgTtsLatencyMs
    ) {
    }

    public record LanguageBreakdownEntry(String language, long callCount) {
    }

    public record AgentSummaryEntry(UUID agentId, String agentName, long totalCalls, double avgDurationSeconds) {
    }

    public record DailyVolumeEntry(String date, long callCount) {
    }
}
