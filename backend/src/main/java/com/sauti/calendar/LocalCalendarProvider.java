package com.sauti.calendar;

import com.sauti.agent.Agent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.calendar.provider", havingValue = "local", matchIfMissing = true)
public class LocalCalendarProvider implements CalendarProvider {
    @Override
    public List<CalendarAvailabilitySlot> availability(Agent agent, LocalDate date, int durationMinutes, ZoneId timezone) {
        return List.of(
                slot(date, LocalTime.of(9, 0), durationMinutes, timezone),
                slot(date, LocalTime.of(10, 30), durationMinutes, timezone),
                slot(date, LocalTime.of(14, 0), durationMinutes, timezone),
                slot(date, LocalTime.of(15, 30), durationMinutes, timezone)
        );
    }

    @Override
    public CalendarSyncResult createEvent(Booking booking) {
        return new CalendarSyncResult("local-" + booking.getId());
    }

    private CalendarAvailabilitySlot slot(LocalDate date, LocalTime time, int durationMinutes, ZoneId timezone) {
        var start = OffsetDateTime.of(date, time, timezone.getRules().getOffset(date.atTime(time)));
        return new CalendarAvailabilitySlot(start, start.plusMinutes(durationMinutes), time.toString());
    }
}
