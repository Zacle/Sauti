package com.sauti.calendar;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    boolean existsByAgent_Id(UUID agentId);

    List<Booking> findAllByTenantIdOrderByAppointmentAtDesc(UUID tenantId);

    Optional<Booking> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Booking> findByBookingReferenceIgnoreCaseAndTenantId(String bookingReference, UUID tenantId);

    Optional<Booking> findFirstByCall_Id(UUID callId);
}
