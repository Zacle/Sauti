package com.sauti.webanalytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "public_web_analytics_events")
public class PublicWebAnalyticsEvent {
    @Id private UUID id;
    @Column(nullable = false, length = 40) private String eventType;
    @Column(nullable = false, length = 300) private String path;
    @Column(nullable = false, length = 64) private String visitorHash;
    @Column(length = 160) private String referrerHost;
    @Column(length = 100) private String utmSource;
    @Column(length = 100) private String utmMedium;
    @Column(length = 160) private String utmCampaign;
    @Column(nullable = false) private OffsetDateTime occurredAt;

    protected PublicWebAnalyticsEvent() { }

    public PublicWebAnalyticsEvent(String eventType, String path, String visitorHash, String referrerHost,
                                   String utmSource, String utmMedium, String utmCampaign,
                                   OffsetDateTime occurredAt) {
        this.id = UUID.randomUUID(); this.eventType = eventType; this.path = path;
        this.visitorHash = visitorHash; this.referrerHost = referrerHost;
        this.utmSource = utmSource; this.utmMedium = utmMedium; this.utmCampaign = utmCampaign;
        this.occurredAt = occurredAt;
    }

    public String getEventType() { return eventType; }
    public String getPath() { return path; }
    public String getVisitorHash() { return visitorHash; }
    public String getReferrerHost() { return referrerHost; }
    public String getUtmSource() { return utmSource; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
}

