package com.sauti.calendar;

import com.sauti.reliability.QueueHealthContributor;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CalendarQueueHealthContributor implements QueueHealthContributor {
    private final BookingRepository bookings;

    public CalendarQueueHealthContributor(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Override
    public List<QueueState> snapshot() {
        var retrying = bookings.countByCalendarSyncStatusAndCalendarSyncAttemptsGreaterThan("pending", 0);
        var allPending = bookings.countByCalendarSyncStatus("pending");
        var oldest = bookings.findFirstByCalendarSyncStatusOrderByCreatedAtAsc("pending")
                .map(Booking::getCreatedAt).orElse(null);
        return List.of(new QueueState("calendar_sync", "Calendar synchronization",
                Math.max(0, allPending - retrying), retrying,
                bookings.countByCalendarSyncStatus("pending_owner_action"), oldest));
    }
}
