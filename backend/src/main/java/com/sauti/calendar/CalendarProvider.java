package com.sauti.calendar;

import com.sauti.agent.Agent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public interface CalendarProvider {
    List<CalendarAvailabilitySlot> availability(Agent agent, LocalDate date, int durationMinutes, ZoneId timezone);

    CalendarSyncResult createEvent(Booking booking);

    default CalendarSyncResult updateEvent(Booking booking) {
        return new CalendarSyncResult(booking.getExternalEventId());
    }

    default void deleteEvent(Booking booking) {
    }
}
