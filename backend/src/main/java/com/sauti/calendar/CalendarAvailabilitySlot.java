package com.sauti.calendar;

import java.time.OffsetDateTime;

public record CalendarAvailabilitySlot(
        OffsetDateTime start,
        OffsetDateTime end,
        String displayString
) {
}
