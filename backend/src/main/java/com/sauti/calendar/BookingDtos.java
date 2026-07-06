package com.sauti.calendar;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class BookingDtos {
    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull UUID agentId,
            UUID callId,
            @NotBlank String callerName,
            @NotBlank String callerPhone,
            @NotBlank String serviceType,
            @Future @NotNull OffsetDateTime appointmentAt
    ) {
    }

    public record BookingResponse(
            UUID id,
            UUID agentId,
            UUID callId,
            String callerName,
            String callerPhone,
            String serviceType,
            OffsetDateTime bookedAt,
            OffsetDateTime appointmentAt,
            String externalEventId,
            String status,
            boolean confirmationSent
    ) {
        public static BookingResponse from(Booking booking) {
            return new BookingResponse(
                    booking.getId(),
                    booking.getAgent().getId(),
                    booking.getCall() == null ? null : booking.getCall().getId(),
                    booking.getCallerName(),
                    booking.getCallerPhone(),
                    booking.getServiceType(),
                    booking.getBookedAt(),
                    booking.getAppointmentAt(),
                    booking.getExternalEventId(),
                    booking.getStatus(),
                    booking.isConfirmationSent()
            );
        }
    }
}
