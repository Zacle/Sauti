package com.sauti.reliability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "voice_startup_measurements")
public class VoiceStartupMeasurement {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 180)
    private String sourceKey;

    @Column(nullable = false, length = 40)
    private String channel;

    @Column(nullable = false)
    private int latencyMs;

    @Column(nullable = false)
    private OffsetDateTime measuredAt;

    protected VoiceStartupMeasurement() { }

    VoiceStartupMeasurement(String sourceKey, String channel, int latencyMs, OffsetDateTime measuredAt) {
        this.id = UUID.randomUUID();
        this.sourceKey = sourceKey;
        this.channel = channel;
        this.latencyMs = latencyMs;
        this.measuredAt = measuredAt;
    }
}
