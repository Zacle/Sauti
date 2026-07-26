package com.sauti.outbound;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledCallRepository extends JpaRepository<ScheduledCall, UUID> {
    boolean existsByAgent_Id(UUID agentId);

    List<ScheduledCall> findTop25ByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(String status, OffsetDateTime dueAt);

    @Modifying
    @Query("""
            delete from ScheduledCall scheduledCall
            where scheduledCall.booking.id = :bookingId
              and scheduledCall.tenant.id = :tenantId
            """)
    int deleteAllForBooking(
            @Param("tenantId") UUID tenantId,
            @Param("bookingId") UUID bookingId
    );
}
