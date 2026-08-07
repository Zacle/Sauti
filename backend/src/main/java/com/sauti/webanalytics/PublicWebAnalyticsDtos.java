package com.sauti.webanalytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PublicWebAnalyticsDtos {
    private PublicWebAnalyticsDtos() { }
    public record TrackEvent(@NotBlank @Size(max = 40) String eventType,
                             @NotBlank @Size(max = 300) String path,
                             @Size(max = 500) String referrer,
                             @Size(max = 100) String utmSource,
                             @Size(max = 100) String utmMedium,
                             @Size(max = 160) String utmCampaign) { }
    public record DailyWebActivity(String date, long pageViews, long visitors,
                                   long voiceDemoStarts, long voiceDemoCompletions,
                                   long demoRequests) { }
    public record RankedValue(String value, long count) { }
    public record WebAnalyticsSnapshot(long pageViews, long uniqueVisitors,
                                       long voiceDemoStarts, long voiceDemoCompletions,
                                       long demoRequests, double visitorToRequestPercent,
                                       List<DailyWebActivity> daily,
                                       List<RankedValue> topPages, List<RankedValue> topSources) { }
}

