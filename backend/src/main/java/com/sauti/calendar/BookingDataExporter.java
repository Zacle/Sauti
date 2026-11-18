package com.sauti.calendar;

import com.sauti.tenant.application.port.WorkspaceDataExporter;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bookings in a workspace data export. */
@Component
class BookingDataExporter implements WorkspaceDataExporter {
    private final BookingRepository bookings;

    BookingDataExporter(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Override
    public String section() {
        return "bookings";
    }

    @Override
    @Transactional(readOnly = true)
    public Object export(UUID workspaceId) {
        return bookings.findAllByTenantIdOrderByAppointmentAtDesc(workspaceId).stream().map(booking -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", booking.getId());
            row.put("reference", booking.getBookingReference());
            row.put("status", booking.getStatus());
            row.put("service", booking.getServiceType());
            row.put("appointmentAt", booking.getAppointmentAt());
            row.put("durationMinutes", booking.getDurationMinutes());
            row.put("bookedAt", booking.getBookedAt());
            row.put("callerName", booking.getCallerName());
            row.put("callerPhone", booking.getCallerPhone());
            row.put("callerEmail", booking.getCallerEmail());
            row.put("agentId", booking.getAgent() == null ? null : booking.getAgent().getId());
            row.put("calendarSyncStatus", booking.getCalendarSyncStatus());
            return row;
        }).toList();
    }
}
