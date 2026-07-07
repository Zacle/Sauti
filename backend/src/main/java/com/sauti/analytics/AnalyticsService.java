package com.sauti.analytics;

import com.sauti.analytics.AnalyticsDtos.AfterHoursBehaviorEntry;
import com.sauti.analytics.AnalyticsDtos.AfterHoursResponse;
import com.sauti.analytics.AnalyticsDtos.AgentSummaryEntry;
import com.sauti.analytics.AnalyticsDtos.AnalyticsSummaryResponse;
import com.sauti.analytics.AnalyticsDtos.ChannelBreakdownEntry;
import com.sauti.analytics.AnalyticsDtos.ConnectRateByDayEntry;
import com.sauti.analytics.AnalyticsDtos.DailyVolumeEntry;
import com.sauti.analytics.AnalyticsDtos.FunnelResponse;
import com.sauti.analytics.AnalyticsDtos.IntegrationEventsEntry;
import com.sauti.analytics.AnalyticsDtos.LanguageBreakdownEntry;
import com.sauti.analytics.AnalyticsDtos.MetricDelta;
import com.sauti.analytics.AnalyticsDtos.OutcomeByDayEntry;
import com.sauti.analytics.AnalyticsDtos.SentimentByDayEntry;
import com.sauti.analytics.AnalyticsDtos.TopIntentEntry;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.call.CallTurnRepository;
import com.sauti.integration.IntegrationDelivery;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private static final List<String> DISCONNECTED_OUTCOMES = List.of("failed", "busy", "no_answer", "canceled");
    private static final List<String> INCOMPLETE_OUTCOMES = List.of("active", "failed", "busy", "no_answer", "canceled");

    private final CallRepository callRepository;
    private final CallTurnRepository callTurnRepository;
    private final EntityManager entityManager;

    public AnalyticsService(CallRepository callRepository, CallTurnRepository callTurnRepository, EntityManager entityManager) {
        this.callRepository = callRepository;
        this.callTurnRepository = callTurnRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        var range = range(from, to);
        var current = summaryStats(calls(tenantId, range.from(), range.to(), agentId));
        var previous = summaryStats(calls(tenantId, range.previousFrom(), range.from(), agentId));

        long turnCount = callTurnRepository.countByTenant_Id(tenantId);
        double avgTurns = current.totalCalls() > 0 ? (double) turnCount / current.totalCalls() : 0.0;

        var latency = callTurnRepository.avgLatencies(tenantId);
        double avgStt = latency != null ? coalesce(latency.getAvgSttMs()) : 0.0;
        double avgLlm = latency != null ? coalesce(latency.getAvgLlmMs()) : 0.0;
        double avgTts = latency != null ? coalesce(latency.getAvgTtsMs()) : 0.0;

        return new AnalyticsSummaryResponse(
                current.totalCalls(),
                current.totalCalls(),
                current.connectedCalls(),
                current.completedCalls(),
                current.faqAnsweredCalls(),
                current.transferredCalls(),
                current.voicemailCalls(),
                current.bookingCalls(),
                current.totalDurationSeconds(),
                current.connectRate(),
                current.averageDurationSeconds(),
                avgTurns,
                avgStt,
                avgLlm,
                avgTts,
                delta(current.totalCalls(), previous.totalCalls()),
                delta(current.connectRate(), previous.connectRate()),
                delta(current.totalDurationSeconds(), previous.totalDurationSeconds()),
                delta(current.averageDurationSeconds(), previous.averageDurationSeconds()),
                delta(current.bookingCalls(), previous.bookingCalls()),
                delta(current.transferredCalls(), previous.transferredCalls())
        );
    }

    @Transactional(readOnly = true)
    public List<LanguageBreakdownEntry> languageBreakdown(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        return calls(tenantId, range(from, to), agentId).stream()
                .map(Call::getLanguageDetected)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new LanguageBreakdownEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentSummaryEntry> agentSummary(UUID tenantId, OffsetDateTime from, OffsetDateTime to) {
        return calls(tenantId, range(from, to), null).stream()
                .collect(Collectors.groupingBy(call -> call.getAgent().getId(), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    var agentCalls = entry.getValue();
                    var first = agentCalls.get(0);
                    var stats = summaryStats(agentCalls);
                    return new AgentSummaryEntry(
                            entry.getKey(),
                            first.getAgent().getName(),
                            stats.totalCalls(),
                            stats.connectedCalls(),
                            stats.bookingCalls(),
                            stats.connectRate(),
                            stats.averageDurationSeconds()
                    );
                })
                .sorted(Comparator.comparingLong(AgentSummaryEntry::totalCalls).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyVolumeEntry> dailyVolume(UUID tenantId, int days) {
        var to = OffsetDateTime.now(ZoneOffset.UTC);
        var from = to.minusDays(days - 1L).truncatedTo(ChronoUnit.DAYS);
        var byDay = calls(tenantId, from, to, null).stream()
                .collect(Collectors.groupingBy(call -> call.getStartedAt().toLocalDate(), Collectors.counting()));

        var result = new ArrayList<DailyVolumeEntry>(days);
        for (int i = 0; i < days; i++) {
            var date = from.toLocalDate().plusDays(i);
            result.add(new DailyVolumeEntry(date.toString(), byDay.getOrDefault(date, 0L)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<OutcomeByDayEntry> outcomesByDay(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        var range = range(from, to);
        var grouped = calls(tenantId, range, agentId).stream()
                .collect(Collectors.groupingBy(call -> call.getStartedAt().toLocalDate(), Collectors.toList()));
        return days(range).stream()
                .map(date -> {
                    var dayCalls = grouped.getOrDefault(date, List.of());
                    return new OutcomeByDayEntry(
                            date.toString(),
                            count(dayCalls, call -> "completed".equals(outcomeBucket(call))),
                            count(dayCalls, call -> "transferred".equals(outcome(call))),
                            count(dayCalls, call -> "voicemail".equals(outcome(call))),
                            count(dayCalls, call -> "no_answer".equals(outcome(call))),
                            count(dayCalls, call -> "busy".equals(outcome(call))),
                            count(dayCalls, call -> "failed".equals(outcome(call))),
                            count(dayCalls, Call::isAfterHours)
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConnectRateByDayEntry> connectRateByDay(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        var range = range(from, to);
        var grouped = calls(tenantId, range, agentId).stream()
                .collect(Collectors.groupingBy(call -> call.getStartedAt().toLocalDate(), Collectors.toList()));
        return days(range).stream()
                .map(date -> {
                    var dayCalls = grouped.getOrDefault(date, List.of());
                    long attempts = dayCalls.size();
                    long connected = count(dayCalls, this::isConnected);
                    return new ConnectRateByDayEntry(date.toString(), attempts, connected, percent(connected, attempts));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public FunnelResponse funnel(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        var stats = summaryStats(calls(tenantId, range(from, to), agentId));
        return new FunnelResponse(stats.totalCalls(), stats.connectedCalls(), stats.completedCalls());
    }

    @Transactional(readOnly = true)
    public List<ChannelBreakdownEntry> byChannel(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        return calls(tenantId, range(from, to), agentId).stream()
                .collect(Collectors.groupingBy(call -> blankTo(call.getDirection(), "unknown"), Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    var stats = summaryStats(entry.getValue());
                    return new ChannelBreakdownEntry(
                            entry.getKey(),
                            stats.totalCalls(),
                            stats.connectedCalls(),
                            stats.completedCalls(),
                            stats.bookingCalls(),
                            stats.connectRate()
                    );
                })
                .sorted(Comparator.comparingLong(ChannelBreakdownEntry::totalCalls).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopIntentEntry> topIntents(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId, int limit) {
        return calls(tenantId, range(from, to), agentId).stream()
                .map(Call::getIntent)
                .map(intent -> intent == null ? "" : intent.trim())
                .filter(intent -> !intent.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(Math.max(1, Math.min(25, limit)))
                .map(entry -> new TopIntentEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SentimentByDayEntry> sentimentByDay(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        var range = range(from, to);
        var grouped = calls(tenantId, range, agentId).stream()
                .filter(call -> call.getSentiment() != null && !call.getSentiment().isBlank())
                .collect(Collectors.groupingBy(call -> call.getStartedAt().toLocalDate(), Collectors.toList()));
        return days(range).stream()
                .map(date -> {
                    var dayCalls = grouped.getOrDefault(date, List.of());
                    long analysed = dayCalls.size();
                    double score = analysed == 0 ? 0.0 : dayCalls.stream().mapToDouble(call -> sentimentScore(call.getSentiment())).average().orElse(0.0);
                    return new SentimentByDayEntry(
                            date.toString(),
                            analysed,
                            score,
                            count(dayCalls, call -> "positive".equals(normalize(call.getSentiment()))),
                            count(dayCalls, call -> "neutral".equals(normalize(call.getSentiment()))),
                            count(dayCalls, call -> "negative".equals(normalize(call.getSentiment()))),
                            count(dayCalls, call -> "mixed".equals(normalize(call.getSentiment())))
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AfterHoursResponse afterHours(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        var afterHoursCalls = calls(tenantId, range(from, to), agentId).stream()
                .filter(Call::isAfterHours)
                .toList();
        var stats = summaryStats(afterHoursCalls);
        var behaviors = afterHoursCalls.stream()
                .collect(Collectors.groupingBy(call -> blankTo(call.getAgent().getAfterHoursBehavior(), "unknown"), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new AfterHoursBehaviorEntry(entry.getKey(), entry.getValue()))
                .toList();
        return new AfterHoursResponse(stats.totalCalls(), stats.connectedCalls(), stats.completedCalls(), behaviors);
    }

    @Transactional(readOnly = true)
    public List<IntegrationEventsEntry> integrationEvents(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        var range = range(from, to);
        var query = """
                select d from IntegrationDelivery d
                join Call c on c.id = d.callId
                where d.tenantId = :tenantId
                  and d.createdAt between :from and :to
                """;
        if (agentId != null) {
            query += " and c.agent.id = :agentId";
        }
        var typedQuery = entityManager.createQuery(query, IntegrationDelivery.class)
                .setParameter("tenantId", tenantId)
                .setParameter("from", range.from())
                .setParameter("to", range.to());
        if (agentId != null) {
            typedQuery.setParameter("agentId", agentId);
        }
        return typedQuery.getResultList().stream()
                .collect(Collectors.groupingBy(delivery -> blankTo(delivery.getProvider(), "unknown"), Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    var deliveries = entry.getValue();
                    return new IntegrationEventsEntry(
                            entry.getKey(),
                            deliveries.size(),
                            countDeliveries(deliveries, "delivered"),
                            countDeliveries(deliveries, "failed"),
                            count(deliveries, delivery -> "retrying".equals(normalize(delivery.getStatus())) || "pending".equals(normalize(delivery.getStatus())))
                    );
                })
                .sorted(Comparator.comparingLong(IntegrationEventsEntry::attempted).reversed())
                .toList();
    }

    private List<Call> calls(UUID tenantId, Range range, UUID agentId) {
        return calls(tenantId, range.from(), range.to(), agentId);
    }

    private List<Call> calls(UUID tenantId, OffsetDateTime from, OffsetDateTime to, UUID agentId) {
        if (agentId != null) {
            return callRepository.findAllByTenantIdAndAgent_IdAndStartedAtBetweenOrderByStartedAtAsc(tenantId, agentId, from, to);
        }
        return callRepository.findAllByTenantIdAndStartedAtBetweenOrderByStartedAtAsc(tenantId, from, to);
    }

    private SummaryStats summaryStats(List<Call> calls) {
        long total = calls.size();
        long connected = count(calls, this::isConnected);
        long completed = count(calls, this::isCompleted);
        long faq = count(calls, call -> "faq_answered".equals(outcome(call)));
        long transferred = count(calls, call -> "transferred".equals(outcome(call)));
        long voicemail = count(calls, call -> "voicemail".equals(outcome(call)));
        long booking = count(calls, call -> "booking_made".equals(outcome(call)));
        long totalDuration = calls.stream()
                .map(Call::getDurationSeconds)
                .filter(value -> value != null && value > 0)
                .mapToLong(Integer::longValue)
                .sum();
        double averageDuration = total == 0 ? 0.0 : calls.stream()
                .map(Call::getDurationSeconds)
                .filter(value -> value != null && value > 0)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
        return new SummaryStats(total, connected, completed, faq, transferred, voicemail, booking, totalDuration, percent(connected, total), averageDuration);
    }

    private boolean isConnected(Call call) {
        return !DISCONNECTED_OUTCOMES.contains(outcome(call));
    }

    private boolean isCompleted(Call call) {
        return !INCOMPLETE_OUTCOMES.contains(outcome(call));
    }

    private String outcomeBucket(Call call) {
        var outcome = outcome(call);
        return switch (outcome) {
            case "transferred", "voicemail", "no_answer", "busy", "failed" -> outcome;
            default -> isCompleted(call) ? "completed" : outcome;
        };
    }

    private String outcome(Call call) {
        return normalize(call.getOutcome());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private double sentimentScore(String sentiment) {
        return switch (normalize(sentiment)) {
            case "positive" -> 1.0;
            case "negative" -> -1.0;
            default -> 0.0;
        };
    }

    private long countDeliveries(List<IntegrationDelivery> deliveries, String status) {
        return count(deliveries, delivery -> status.equals(normalize(delivery.getStatus())));
    }

    private <T> long count(List<T> values, java.util.function.Predicate<T> predicate) {
        return values.stream().filter(predicate).count();
    }

    private double coalesce(Double value) {
        return value != null ? value : 0.0;
    }

    private double percent(double numerator, double denominator) {
        return denominator <= 0 ? 0.0 : (numerator / denominator) * 100.0;
    }

    private MetricDelta delta(double current, double previous) {
        double percentChange = previous == 0.0 ? (current == 0.0 ? 0.0 : 100.0) : ((current - previous) / previous) * 100.0;
        return new MetricDelta(current, previous, percentChange);
    }

    private Range range(OffsetDateTime from, OffsetDateTime to) {
        var resolvedTo = to == null ? OffsetDateTime.now(ZoneOffset.UTC) : to;
        var resolvedFrom = from == null ? resolvedTo.minusDays(30) : from;
        if (!resolvedFrom.isBefore(resolvedTo)) {
            resolvedFrom = resolvedTo.minusDays(30);
        }
        long seconds = Math.max(1, ChronoUnit.SECONDS.between(resolvedFrom, resolvedTo));
        return new Range(resolvedFrom, resolvedTo, resolvedFrom.minusSeconds(seconds));
    }

    private List<LocalDate> days(Range range) {
        var start = range.from().toLocalDate();
        var end = range.to().toLocalDate();
        var days = new ArrayList<LocalDate>();
        for (var date = start; !date.isAfter(end); date = date.plusDays(1)) {
            days.add(date);
        }
        return days;
    }

    private record Range(OffsetDateTime from, OffsetDateTime to, OffsetDateTime previousFrom) {
    }

    private record SummaryStats(
            long totalCalls,
            long connectedCalls,
            long completedCalls,
            long faqAnsweredCalls,
            long transferredCalls,
            long voicemailCalls,
            long bookingCalls,
            long totalDurationSeconds,
            double connectRate,
            double averageDurationSeconds
    ) {
    }
}
