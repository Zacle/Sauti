package com.sauti.calendar;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
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

    Optional<Booking> findFirstByCall_Id(UUID callId);

    List<Booking> findTop20ByCalendarSyncStatusAndCalendarSyncNextAttemptAtLessThanEqualOrderByCreatedAt(
            String calendarSyncStatus,
            OffsetDateTime nextAttemptAt
    );

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

    List<Booking> findAllByTenantIdAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
            UUID tenantId,
            OffsetDateTime windowStart,
            OffsetDateTime windowEnd
    );
}
