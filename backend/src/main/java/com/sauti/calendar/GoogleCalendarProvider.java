package com.sauti.calendar;

import com.sauti.agent.Agent;
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
        var dayStart = date.atTime(9, 0).atZone(timezone).toOffsetDateTime();
        var dayEnd = date.atTime(17, 0).atZone(timezone).toOffsetDateTime();
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
}
