package com.sauti.calendar;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;

public final class BookingDtos {
    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull UUID agentId,
            UUID callId,
            @NotBlank String callerName,
            @NotBlank String callerPhone,
            String callerEmail,
            @NotBlank String serviceType,
            @Future @NotNull OffsetDateTime appointmentAt,
            @Min(5) @Max(480) Integer durationMinutes,
            Map<String, Object> capturedData
    ) {
    }

    public record RescheduleBookingRequest(
            @Future @NotNull OffsetDateTime appointmentAt,
            @Min(5) @Max(480) Integer durationMinutes
    ) {}

    public record UpdateBookingRequest(
            @NotBlank String callerName,
            @NotBlank String callerPhone,
            String callerEmail,
            @NotBlank String serviceType,
            @Future @NotNull OffsetDateTime appointmentAt,
            @Min(5) @Max(480) Integer durationMinutes,
            Map<String, Object> capturedData
    ) {}

    public record BookingResponse(
            UUID id,
            UUID agentId,
            UUID callId,
            String bookingReference,
            String callerName,
            String callerPhone,
            String callerEmail,
            String serviceType,
            OffsetDateTime bookedAt,
            OffsetDateTime appointmentAt,
            int durationMinutes,
            Map<String, Object> capturedData,
            String externalEventId,
            String calendarSyncStatus,
            String calendarSyncError,
            String status,
            boolean confirmationSent
    ) {
        public static BookingResponse from(Booking booking) {
            return new BookingResponse(
                    booking.getId(),
                    booking.getAgent().getId(),
                    booking.getCall() == null ? null : booking.getCall().getId(),
                    booking.getBookingReference(),
                    booking.getCallerName(),
                    booking.getCallerPhone(),
                    booking.getCallerEmail(),
                    booking.getServiceType(),
                    booking.getBookedAt(),
                    booking.getAppointmentAt(),
                    booking.getDurationMinutes(),
                    parseCapturedData(booking.getCapturedData()),
                    booking.getExternalEventId(),
                    booking.getCalendarSyncStatus(),
                    booking.getCalendarSyncError(),
                    booking.getStatus(),
                    booking.isConfirmationSent()
            );
        }

        private static Map<String, Object> parseCapturedData(String value) {
            if (value == null || value.isBlank()) return Map.of();
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        value,
                        new com.fasterxml.jackson.core.type.TypeReference<>() { }
                );
            } catch (Exception ignored) {
                return Map.of();
            }
        }
    }
}
