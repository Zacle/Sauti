package com.sauti.calendar;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Query("select b from Booking b where b.tenant.id = :tenantId and b.call.id in :callIds order by b.bookedAt")
    List<Booking> findAllForCalls(@Param("tenantId") UUID tenantId, @Param("callIds") Collection<UUID> callIds);

    /** Calls whose booking matches a search: the caller's name, the service or the booking number. */
    @Query("""
            select distinct b.call.id from Booking b
            where b.tenant.id = :tenantId and b.call is not null
              and (lower(b.callerName) like :pattern escape '!'
                or lower(b.serviceType) like :pattern escape '!'
                or lower(b.bookingReference) like :pattern escape '!')
            """)
    List<UUID> findCallIdsMatching(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern, Pageable page);

    @Query("""
            select count(b) from Booking b
            where b.tenant.id = :tenantId and b.call.id is not null and b.status <> 'cancelled'
              and b.call.startedAt >= :from and b.call.startedAt < :to
            """)
    long countFromCallsStartedBetween(@Param("tenantId") UUID tenantId, @Param("from") OffsetDateTime from,
                                      @Param("to") OffsetDateTime to);
    long countByTenantId(UUID tenantId);

    boolean existsByAgent_Id(UUID agentId);

    List<Booking> findAllByTenantIdOrderByAppointmentAtDesc(UUID tenantId);

    Optional<Booking> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Booking> findByIdAndTenantIdAndAgent_Id(UUID id, UUID tenantId, UUID agentId);

    Optional<Booking> findByBookingReferenceIgnoreCaseAndTenantId(String bookingReference, UUID tenantId);

    Optional<Booking> findByBookingReferenceIgnoreCaseAndTenantIdAndAgent_Id(
            String bookingReference,
            UUID tenantId,
            UUID agentId
    );

    List<Booking> findAllByTenantIdAndAgent_IdAndStatusNotAndBookingReferenceEndingWithIgnoreCase(
            UUID tenantId,
            UUID agentId,
            String excludedStatus,
            String bookingReferenceSuffix
    );

    Optional<Booking> findFirstByCall_Id(UUID callId);

    Optional<Booking> findFirstByTenantIdAndCall_IdAndAgent_Id(UUID tenantId, UUID callId, UUID agentId);

    Optional<Booking> findFirstByTenantIdAndCall_IdAndAgent_IdOrderByCreatedAtDesc(
            UUID tenantId, UUID callId, UUID agentId);

    List<Booking> findTop20ByCalendarSyncStatusAndCalendarSyncNextAttemptAtLessThanEqualOrderByCreatedAt(
            String calendarSyncStatus,
            OffsetDateTime nextAttemptAt
    );

    long countByCalendarSyncStatus(String status);
    long countByCalendarSyncStatusAndCalendarSyncAttemptsGreaterThan(String status, int attempts);
    Optional<Booking> findFirstByCalendarSyncStatusOrderByCreatedAtAsc(String status);

    Optional<Booking> findFirstByTenantIdAndCall_IdAndAgent_IdAndStatusNotAndAppointmentAt(
            UUID tenantId,
            UUID callId,
            UUID agentId,
            String excludedStatus,
            OffsetDateTime appointmentAt
    );

    List<Booking> findAllByTenantIdAndAgent_IdAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
            UUID tenantId,
            UUID agentId,
            String excludedStatus,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd
    );

    List<Booking> findAllByTenantIdAndAgent_IdInAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
            UUID tenantId,
            Collection<UUID> agentIds,
            String excludedStatus,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd
    );

    List<Booking> findAllByTenantIdAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
            UUID tenantId,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd
    );
}
