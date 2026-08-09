package com.sauti.reliability;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoiceStartupMeasurementRepository extends JpaRepository<VoiceStartupMeasurement, UUID> {
    boolean existsBySourceKey(String sourceKey);

    @Query("""
            select count(m) as sampleSize, coalesce(avg(m.latencyMs), 0) as averageLatencyMs
            from VoiceStartupMeasurement m
            where m.measuredAt >= :from and m.channel in :channels
            """)
    StartupAggregate aggregateSince(
            @Param("from") OffsetDateTime from,
            @Param("channels") Collection<String> channels
    );

    interface StartupAggregate {
        Long getSampleSize();
        Double getAverageLatencyMs();
    }
}
