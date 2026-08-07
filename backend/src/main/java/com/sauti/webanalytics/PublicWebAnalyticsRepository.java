package com.sauti.webanalytics;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicWebAnalyticsRepository extends JpaRepository<PublicWebAnalyticsEvent, UUID> {
    List<PublicWebAnalyticsEvent> findAllByOccurredAtBetweenOrderByOccurredAtAsc(OffsetDateTime from, OffsetDateTime to);
    long deleteByOccurredAtBefore(OffsetDateTime cutoff);
}

