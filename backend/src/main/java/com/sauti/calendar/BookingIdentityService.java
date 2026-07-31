package com.sauti.calendar;

import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Language-neutral, tenant-and-agent-scoped identity verification for existing bookings.
 *
 * <p>Caller names are deliberately not identity factors: speech recognition and
 * transliteration make them unreliable across languages. No booking data should
 * be disclosed until this service returns {@link Status#VERIFIED}.</p>
 */
@Service
public class BookingIdentityService {
    private final BookingService bookings;

    public BookingIdentityService(BookingService bookings) {
        this.bookings = bookings;
    }

    public Result verify(Request request) {
        Objects.requireNonNull(request, "Booking identity request is required");
        if (blank(request.callerPhone())) return Result.mismatch();
        if (!blank(request.bookingReference())) return verifyReference(request);
        if (!blank(request.bookingReferenceSuffix())) return verifyReferenceSuffix(request);
        if (request.appointmentDate() == null || request.timezone() == null) {
            return Result.mismatch();
        }

        var candidates = bookings.findOnAppointmentDateForAgent(
                        request.tenantId(), request.agentId(), request.appointmentDate(), request.timezone()
                ).stream()
                .filter(booking -> samePhone(booking.getCallerPhone(), request.callerPhone()))
                .toList();
        if (candidates.isEmpty()) return Result.mismatch();
        // Time is always a caller-supplied challenge, even when only one record
        // matches. This prevents phone + date from becoming a booking disclosure.
        if (request.appointmentTime() == null) return Result.timeRequired();

        var suppliedTime = request.appointmentTime().truncatedTo(ChronoUnit.MINUTES);
        var timed = candidates.stream()
                .filter(booking -> booking.getAppointmentAt()
                        .atZoneSameInstant(request.timezone())
                        .toLocalTime()
                        .truncatedTo(ChronoUnit.MINUTES)
                        .equals(suppliedTime))
                .toList();
        if (timed.isEmpty()) return Result.mismatch();
        if (timed.size() > 1) return Result.referenceRequired();
        return Result.verified(timed.get(0));
    }

    private Result verifyReference(Request request) {
        final Booking booking;
        try {
            booking = bookings.resolveForAgent(
                    request.tenantId(), request.agentId(), request.bookingReference()
            );
        } catch (EntityNotFoundException | IllegalArgumentException exception) {
            return Result.mismatch();
        }
        return samePhone(booking.getCallerPhone(), request.callerPhone())
                ? Result.verified(booking)
                : Result.mismatch();
    }

    private Result verifyReferenceSuffix(Request request) {
        var suffix = request.bookingReferenceSuffix().replaceAll("[^A-Z0-9]", "");
        if (suffix.length() != 4) return Result.mismatch();
        var matches = bookings.findByReferenceSuffixForAgent(
                        request.tenantId(), request.agentId(), suffix
                ).stream()
                .filter(booking -> samePhone(booking.getCallerPhone(), request.callerPhone()))
                .toList();
        if (matches.isEmpty()) return Result.mismatch();
        if (matches.size() > 1) return Result.referenceSuffixAmbiguous();
        return Result.verified(matches.get(0));
    }

    private boolean samePhone(String expected, String supplied) {
        var left = normalizePhone(expected);
        var right = normalizePhone(supplied);
        return !left.isBlank() && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String normalizePhone(String value) {
        var digits = value == null ? "" : value.replaceAll("\\D", "");
        return digits.startsWith("00") ? digits.substring(2) : digits;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record Request(
            UUID tenantId,
            UUID agentId,
            String callerPhone,
            String bookingReference,
            String bookingReferenceSuffix,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            ZoneId timezone
    ) {
        public Request {
            Objects.requireNonNull(tenantId, "Tenant is required");
            Objects.requireNonNull(agentId, "Agent is required");
            bookingReference = bookingReference == null
                    ? "" : bookingReference.trim().toUpperCase(Locale.ROOT);
            bookingReferenceSuffix = bookingReferenceSuffix == null
                    ? "" : bookingReferenceSuffix.trim().toUpperCase(Locale.ROOT);
        }
    }

    public record Result(Status status, Booking booking) {
        static Result verified(Booking booking) {
            return new Result(Status.VERIFIED, Objects.requireNonNull(booking));
        }

        static Result mismatch() {
            return new Result(Status.MISMATCH, null);
        }

        static Result timeRequired() {
            return new Result(Status.TIME_REQUIRED, null);
        }

        static Result referenceRequired() {
            return new Result(Status.REFERENCE_REQUIRED, null);
        }

        static Result referenceSuffixAmbiguous() {
            return new Result(Status.REFERENCE_SUFFIX_AMBIGUOUS, null);
        }
    }

    public enum Status {
        VERIFIED,
        TIME_REQUIRED,
        REFERENCE_REQUIRED,
        REFERENCE_SUFFIX_AMBIGUOUS,
        MISMATCH
    }
}
