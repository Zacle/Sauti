package com.sauti.calendar;

import com.sauti.agent.Agent;
import com.sauti.agent.OperatingHoursSchedule;
import com.sauti.tool.CalendarCredential;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class GoogleCalendarProvider implements CalendarProvider {
    private final CalendarCredential credential;
    private final GoogleCalendarApiClient client;

    public GoogleCalendarProvider(CalendarCredential credential, GoogleCalendarApiClient client) {
        this.credential = credential;
        this.client = client;
    }

    @Override
    public List<CalendarAvailabilitySlot> availability(Agent agent, LocalDate date, int durationMinutes, ZoneId timezone) {
        var ranges = OperatingHoursSchedule.rangesFor(agent.getOperatingHours(), date, timezone);
        if (ranges.isEmpty()) return List.of();
        var dayStart = ranges.get(0).start();
        var dayEnd = ranges.get(ranges.size() - 1).end();
        var busy = client.busy(credential, dayStart, dayEnd, timezone.toString());
        var slots = new ArrayList<CalendarAvailabilitySlot>();
        for (var cursor = dayStart; !cursor.plusMinutes(durationMinutes).isAfter(dayEnd); cursor = cursor.plusMinutes(30)) {
            var slotStart = cursor;
            var slotEnd = slotStart.plusMinutes(durationMinutes);
            boolean overlaps = busy.stream().anyMatch(period ->
                    slotStart.isBefore(period.end()) && slotEnd.isAfter(period.start())
            );
            if (!overlaps) {
                slots.add(new CalendarAvailabilitySlot(slotStart, slotEnd, slotStart.toLocalTime().toString()));
            }
        }
        return List.copyOf(slots);
    }

    @Override
    public CalendarSyncResult createEvent(Booking booking) {
        return new CalendarSyncResult(client.createEvent(credential, booking));
    }

    @Override
    public CalendarSyncResult updateEvent(Booking booking) {
        client.updateEvent(credential, booking);
        return new CalendarSyncResult(booking.getExternalEventId());
    }

    @Override
    public void deleteEvent(Booking booking) {
        client.deleteEvent(credential, booking.getExternalEventId());
    }
}
