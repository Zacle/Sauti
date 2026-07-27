package com.sauti.calendar;

import com.sauti.tool.CalendarProviderFactory;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synchronizes already-committed Sauti bookings to optional external calendars.
 *
 * The booking row itself is the durable job record. This keeps Sauti as the
 * source of truth and makes retries independent from the live call request.
 */
@Service
public class BookingCalendarSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookingCalendarSyncService.class);
    private final BookingRepository bookingRepository;
    private final BookingCalendarSyncProcessor processor;

    public BookingCalendarSyncService(
            BookingRepository bookingRepository,
            BookingCalendarSyncProcessor processor
    ) {
        this.bookingRepository = bookingRepository;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${sauti.calendar.sync-delay-ms:5000}")
    @Transactional(readOnly = true)
    public void synchronizeDueBookings() {
        bookingRepository
                .findTop20ByCalendarSyncStatusAndCalendarSyncNextAttemptAtLessThanEqualOrderByCreatedAt(
                        "pending",
                        OffsetDateTime.now()
                )
                .stream()
                .map(Booking::getId)
                .forEach(processor::synchronize);
    }

    @Service
    public static class BookingCalendarSyncProcessor {
        private final BookingRepository bookingRepository;
        private final CalendarProviderFactory calendarProviderFactory;

        public BookingCalendarSyncProcessor(
                BookingRepository bookingRepository,
                CalendarProviderFactory calendarProviderFactory
        ) {
            this.bookingRepository = bookingRepository;
            this.calendarProviderFactory = calendarProviderFactory;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void synchronize(UUID bookingId) {
            var booking = bookingRepository.findById(bookingId).orElse(null);
            if (booking == null || !"pending".equals(booking.getCalendarSyncStatus())) return;
            try {
                var provider = "Google Calendar".equalsIgnoreCase(booking.getAgent().getCalendarProvider())
                        ? calendarProviderFactory.connectedForAgent(booking.getAgent().getId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "The selected calendar integration is not connected"
                            ))
                        : calendarProviderFactory.forAgent(booking.getAgent().getId());
                var result = provider.createEvent(booking);
                if (result == null || result.externalEventId() == null || result.externalEventId().isBlank()) {
                    throw new IllegalStateException("Calendar provider did not return an event identifier");
                }
                booking.markSynced(result.externalEventId());
            } catch (RuntimeException exception) {
                booking.markSyncFailed(safeSyncError(exception));
                LOGGER.warn(
                        "Booking calendar synchronization failed bookingId={} attempt={} status={} error={}",
                        booking.getId(),
                        booking.getCalendarSyncAttempts(),
                        booking.getCalendarSyncStatus(),
                        exception.getClass().getSimpleName()
                );
            }
        }

        private String safeSyncError(RuntimeException exception) {
            var message = exception.getMessage() == null
                    ? ""
                    : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (message.contains("not connected")
                    || message.contains("credential")
                    || message.contains("authoriz")) {
                return "Calendar connection is missing or no longer authorized";
            }
            if (message.contains("event identifier")) {
                return "The calendar did not confirm the event creation";
            }
            return "External calendar synchronization failed";
        }
    }
}
