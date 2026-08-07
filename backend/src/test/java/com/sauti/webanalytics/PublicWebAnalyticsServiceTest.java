package com.sauti.webanalytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.webanalytics.PublicWebAnalyticsDtos.TrackEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import com.sauti.shared.RedisRateLimiter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublicWebAnalyticsServiceTest {
    private final PublicWebAnalyticsRepository repository = mock(PublicWebAnalyticsRepository.class);
    private final RedisRateLimiter rateLimiter = mock(RedisRateLimiter.class);
    private final PublicWebAnalyticsService service = service();

    private PublicWebAnalyticsService service() {
        when(rateLimiter.tryAcquire(any(), any(), anyInt(), any())).thenReturn(true);
        return new PublicWebAnalyticsService(repository, rateLimiter, "test-secret");
    }

    @Test
    void storesOnlySanitizedAcquisitionDataAndProtectedDailyIdentity() {
        service.track(new TrackEvent("page_view", "/pricing?private=value",
                        "https://search.example/path?q=private", "campaign", "paid", "launch"),
                "203.0.113.25", "Mozilla/5.0 Test Browser");

        var event = ArgumentCaptor.forClass(PublicWebAnalyticsEvent.class);
        verify(repository).save(event.capture());
        assertThat(event.getValue().getPath()).isEqualTo("/pricing");
        assertThat(event.getValue().getReferrerHost()).isEqualTo("search.example");
        assertThat(event.getValue().getVisitorHash()).hasSize(64)
                .doesNotContain("203.0.113.25").doesNotContain("Mozilla");
    }

    @Test
    void ignoresBotsAndBuildsTheAcquisitionFunnel() {
        service.track(new TrackEvent("page_view", "/", null, null, null, null),
                "203.0.113.25", "ExampleBot/1.0");
        verify(repository, never()).save(any());

        var from = OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        var to = from.plusDays(2);
        when(repository.findAllByOccurredAtBetweenOrderByOccurredAtAsc(from, to)).thenReturn(List.of(
                event("page_view", "/", "visitor-a", from.plusHours(1)),
                event("page_view", "/pricing", "visitor-a", from.plusHours(2)),
                event("voice_demo_started", "/", "visitor-a", from.plusHours(3)),
                event("voice_demo_completed", "/", "visitor-a", from.plusHours(4)),
                event("demo_request_submitted", "/request-demo", "visitor-a", from.plusHours(5))));

        var snapshot = service.snapshot(from, to);
        assertThat(snapshot.pageViews()).isEqualTo(2);
        assertThat(snapshot.uniqueVisitors()).isEqualTo(1);
        assertThat(snapshot.voiceDemoCompletions()).isEqualTo(1);
        assertThat(snapshot.demoRequests()).isEqualTo(1);
        assertThat(snapshot.visitorToRequestPercent()).isEqualTo(100);
    }

    private PublicWebAnalyticsEvent event(String type, String path, String visitor, OffsetDateTime occurredAt) {
        return new PublicWebAnalyticsEvent(type, path, visitor, null, null, null, null, occurredAt);
    }
}
