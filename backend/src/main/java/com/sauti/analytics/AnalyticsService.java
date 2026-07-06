package com.sauti.analytics;

import com.sauti.analytics.AnalyticsDtos.AgentSummaryEntry;
import com.sauti.analytics.AnalyticsDtos.AnalyticsSummaryResponse;
import com.sauti.analytics.AnalyticsDtos.DailyVolumeEntry;
import com.sauti.analytics.AnalyticsDtos.LanguageBreakdownEntry;
import com.sauti.call.CallRepository;
import com.sauti.call.CallTurnRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private final CallRepository callRepository;
    private final CallTurnRepository callTurnRepository;

    public AnalyticsService(CallRepository callRepository, CallTurnRepository callTurnRepository) {
        this.callRepository = callRepository;
        this.callTurnRepository = callTurnRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(UUID tenantId, OffsetDateTime from, OffsetDateTime to) {
        long total, faq, transferred, voicemail, booking;
        double avgDuration;

        if (from != null && to != null) {
            total       = callRepository.countByTenantIdAndStartedAtBetween(tenantId, from, to);
            faq         = callRepository.countByTenantIdAndOutcomeAndStartedAtBetween(tenantId, "faq_answered", from, to);
            transferred = callRepository.countByTenantIdAndOutcomeAndStartedAtBetween(tenantId, "transferred", from, to);
            voicemail   = callRepository.countByTenantIdAndOutcomeAndStartedAtBetween(tenantId, "voicemail", from, to);
            booking     = callRepository.countByTenantIdAndOutcomeAndStartedAtBetween(tenantId, "booking_made", from, to);
            avgDuration = callRepository.avgDurationBetween(tenantId, from, to);
        } else {
            total       = callRepository.countByTenantId(tenantId);
            faq         = callRepository.countByTenantIdAndOutcome(tenantId, "faq_answered");
            transferred = callRepository.countByTenantIdAndOutcome(tenantId, "transferred");
            voicemail   = callRepository.countByTenantIdAndOutcome(tenantId, "voicemail");
            booking     = callRepository.countByTenantIdAndOutcome(tenantId, "booking_made");
            avgDuration = callRepository.averageDurationSeconds(tenantId);
        }

        long turnCount = callTurnRepository.countByTenant_Id(tenantId);
        double avgTurns = total > 0 ? (double) turnCount / total : 0.0;

        var latency = callTurnRepository.avgLatencies(tenantId);
        double avgStt = latency != null ? coalesce(latency.getAvgSttMs()) : 0.0;
        double avgLlm = latency != null ? coalesce(latency.getAvgLlmMs()) : 0.0;
        double avgTts = latency != null ? coalesce(latency.getAvgTtsMs()) : 0.0;

        return new AnalyticsSummaryResponse(total, faq, transferred, voicemail, booking,
                avgDuration, avgTurns, avgStt, avgLlm, avgTts);
    }

    @Transactional(readOnly = true)
    public List<LanguageBreakdownEntry> languageBreakdown(UUID tenantId) {
        return callRepository.languageDistribution(tenantId).stream()
                .map(s -> new LanguageBreakdownEntry(s.getLanguage(), s.getCallCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentSummaryEntry> agentSummary(UUID tenantId) {
        return callRepository.agentSummary(tenantId).stream()
                .map(s -> new AgentSummaryEntry(s.getAgentId(), s.getAgentName(), s.getTotalCalls(), s.getAvgDuration()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyVolumeEntry> dailyVolume(UUID tenantId, int days) {
        var since = OffsetDateTime.now().minusDays(days);
        Map<LocalDate, Long> counts = callRepository
                .findAllByTenantIdAndStartedAtAfterOrderByStartedAtAsc(tenantId, since)
                .stream()
                .collect(Collectors.groupingBy(c -> c.getStartedAt().toLocalDate(), Collectors.counting()));

        var result = new ArrayList<DailyVolumeEntry>(days);
        for (int i = days - 1; i >= 0; i--) {
            var date = LocalDate.now().minusDays(i);
            result.add(new DailyVolumeEntry(date.toString(), counts.getOrDefault(date, 0L)));
        }
        return result;
    }

    private double coalesce(Double value) {
        return value != null ? value : 0.0;
    }
}
