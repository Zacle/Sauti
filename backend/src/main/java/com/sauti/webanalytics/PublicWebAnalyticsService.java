package com.sauti.webanalytics;

import com.sauti.webanalytics.PublicWebAnalyticsDtos.DailyWebActivity;
import com.sauti.webanalytics.PublicWebAnalyticsDtos.RankedValue;
import com.sauti.webanalytics.PublicWebAnalyticsDtos.TrackEvent;
import com.sauti.webanalytics.PublicWebAnalyticsDtos.WebAnalyticsSnapshot;
import com.sauti.shared.RedisRateLimiter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicWebAnalyticsService {
    private static final Set<String> EVENTS = Set.of("page_view", "voice_demo_started",
            "voice_demo_completed", "voice_demo_failed", "demo_request_submitted");
    private static final Set<String> BOT_MARKERS = Set.of("bot", "crawler", "spider", "slurp", "headless", "preview");
    private final PublicWebAnalyticsRepository events;
    private final String hashSecret;
    private final RedisRateLimiter rateLimiter;

    public PublicWebAnalyticsService(PublicWebAnalyticsRepository events, RedisRateLimiter rateLimiter,
            @Value("${sauti.analytics.hash-secret:${sauti.jwt.secret}}") String hashSecret) {
        this.events = events; this.rateLimiter = rateLimiter; this.hashSecret = hashSecret;
    }

    @Transactional
    public void track(TrackEvent request, String clientAddress, String userAgent) {
        var type = clean(request.eventType(), 40).toLowerCase(Locale.ROOT);
        if (!EVENTS.contains(type) || bot(userAgent)) return;
        var path = safePath(request.path());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var visitorHash = hash(clientAddress + "|" + (userAgent == null ? "" : userAgent) + "|" + now.toLocalDate());
        try {
            if (!rateLimiter.tryAcquire("public-web-analytics", visitorHash, 120, Duration.ofHours(1))) return;
        } catch (RuntimeException unavailable) {
            return; // Measurement must never become a dependency of the public website.
        }
        events.save(new PublicWebAnalyticsEvent(type, path, visitorHash,
                referrerHost(request.referrer()), cleanOptional(request.utmSource(), 100),
                cleanOptional(request.utmMedium(), 100), cleanOptional(request.utmCampaign(), 160), now));
    }

    @Transactional(readOnly = true)
    public WebAnalyticsSnapshot snapshot(OffsetDateTime from, OffsetDateTime to) {
        var rows = events.findAllByOccurredAtBetweenOrderByOccurredAtAsc(from, to);
        var pageViews = rows.stream().filter(row -> "page_view".equals(row.getEventType())).toList();
        long visitors = pageViews.stream().map(PublicWebAnalyticsEvent::getVisitorHash).distinct().count();
        long starts = count(rows, "voice_demo_started");
        long completions = count(rows, "voice_demo_completed");
        long requests = count(rows, "demo_request_submitted");
        var acquisitionRows = new ArrayList<>(pageViews.stream().collect(Collectors.toMap(
                PublicWebAnalyticsEvent::getVisitorHash, row -> row, (first, ignored) -> first,
                java.util.LinkedHashMap::new)).values());
        var daily = new ArrayList<DailyWebActivity>();
        for (var date = from.toLocalDate(); date.isBefore(to.toLocalDate()); date = date.plusDays(1)) {
            var day = date;
            var dayRows = rows.stream().filter(row -> row.getOccurredAt().toLocalDate().equals(day)).toList();
            var dayViews = dayRows.stream().filter(row -> "page_view".equals(row.getEventType())).toList();
            daily.add(new DailyWebActivity(day.toString(), dayViews.size(),
                    dayViews.stream().map(PublicWebAnalyticsEvent::getVisitorHash).distinct().count(),
                    count(dayRows, "voice_demo_started"), count(dayRows, "voice_demo_completed"),
                    count(dayRows, "demo_request_submitted")));
        }
        return new WebAnalyticsSnapshot(pageViews.size(), visitors, starts, completions, requests,
                visitors == 0 ? 0 : Math.round(requests * 10000.0 / visitors) / 100.0,
                daily, ranked(pageViews, PublicWebAnalyticsEvent::getPath),
                ranked(acquisitionRows, row -> row.getUtmSource() != null ? row.getUtmSource()
                        : row.getReferrerHost() != null ? row.getReferrerHost() : "Direct"));
    }

    @Scheduled(cron = "${sauti.analytics.retention-cron:0 41 3 * * *}", zone = "UTC")
    @Transactional
    public void purgeExpired() {
        events.deleteByOccurredAtBefore(OffsetDateTime.now(ZoneOffset.UTC).minusDays(90));
    }

    private long count(java.util.List<PublicWebAnalyticsEvent> rows, String type) {
        return rows.stream().filter(row -> type.equals(row.getEventType())).count();
    }
    private List<RankedValue> ranked(java.util.List<PublicWebAnalyticsEvent> rows,
                                     java.util.function.Function<PublicWebAnalyticsEvent, String> value) {
        return rows.stream().collect(Collectors.groupingBy(value, Collectors.counting())).entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())).limit(8)
                .map(item -> new RankedValue(item.getKey(), item.getValue())).toList();
    }
    private boolean bot(String userAgent) {
        var normalized = userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        return normalized.isBlank() || BOT_MARKERS.stream().anyMatch(normalized::contains);
    }
    private String safePath(String value) {
        var clean = clean(value, 300);
        if (!clean.startsWith("/") || clean.startsWith("//")) return "/";
        var query = clean.indexOf('?');
        return query < 0 ? clean : clean.substring(0, query);
    }
    private String referrerHost(String value) {
        if (value == null || value.isBlank()) return null;
        try { return cleanOptional(URI.create(value.trim()).getHost(), 160); }
        catch (IllegalArgumentException exception) { return null; }
    }
    private String clean(String value, int max) {
        var normalized = value == null ? "" : value.trim().replaceAll("[\\p{Cntrl}]", "");
        return normalized.substring(0, Math.min(max, normalized.length()));
    }
    private String cleanOptional(String value, int max) {
        var normalized = clean(value, max); return normalized.isBlank() ? null : normalized;
    }
    private String hash(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("Analytics identifier could not be protected", exception); }
    }
}
