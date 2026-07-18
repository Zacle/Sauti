package com.sauti.calendar;

import com.sauti.agent.Agent;
import com.sauti.agent.OperatingHoursSchedule;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.calendar.provider", havingValue = "local", matchIfMissing = true)
public class LocalCalendarProvider implements CalendarProvider {
    @Override
    public List<CalendarAvailabilitySlot> availability(Agent agent, LocalDate date, int durationMinutes, ZoneId timezone) {
        var slots = new ArrayList<CalendarAvailabilitySlot>();
        for (var range : OperatingHoursSchedule.rangesFor(OperatingHoursSchedule.effective(agent), date, timezone)) {
            for (var cursor = range.start(); !cursor.plusMinutes(durationMinutes).isAfter(range.end()); cursor = cursor.plusMinutes(30)) {
                slots.add(new CalendarAvailabilitySlot(
                        cursor,
                        cursor.plusMinutes(durationMinutes),
                        cursor.toLocalTime().toString()
                ));
            }
        }
        return List.copyOf(slots);
    }

    @Override
    public CalendarSyncResult createEvent(Booking booking) {
        return new CalendarSyncResult("local-" + booking.getId());
    }
}
