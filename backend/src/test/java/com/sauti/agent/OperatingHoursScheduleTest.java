package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class OperatingHoursScheduleTest {
    private static final String WEEKLY = """
            {
              "monday":{"enabled":true,"start":"09:00","end":"17:00"},
              "tuesday":{"enabled":false,"start":"09:00","end":"17:00"},
              "friday":{"enabled":true,"start":"20:00","end":"02:00"}
            }
            """;

    @Test
    void evaluatesScheduleInTheAgentsTimezone() {
        var mondayAtKinshasaTen = OffsetDateTime.of(2026, 7, 6, 9, 0, 0, 0, ZoneOffset.UTC);
        var mondayAtKinshasaSix = OffsetDateTime.of(2026, 7, 6, 5, 0, 0, 0, ZoneOffset.UTC);

        assertThat(OperatingHoursSchedule.isOpen(
                WEEKLY, mondayAtKinshasaTen.atZoneSameInstant(java.time.ZoneId.of("Africa/Kinshasa"))
        )).isTrue();
        assertThat(OperatingHoursSchedule.isOpen(
                WEEKLY, mondayAtKinshasaSix.atZoneSameInstant(java.time.ZoneId.of("Africa/Kinshasa"))
        )).isFalse();
    }

    @Test
    void supportsClosedDaysAndOvernightRanges() {
        var tuesdayNoon = OffsetDateTime.of(2026, 7, 7, 12, 0, 0, 0, ZoneOffset.UTC).toZonedDateTime();
        var fridayLate = OffsetDateTime.of(2026, 7, 10, 23, 0, 0, 0, ZoneOffset.UTC).toZonedDateTime();
        var saturdayEarly = OffsetDateTime.of(2026, 7, 11, 1, 0, 0, 0, ZoneOffset.UTC).toZonedDateTime();
        var fridayAfternoon = OffsetDateTime.of(2026, 7, 10, 15, 0, 0, 0, ZoneOffset.UTC).toZonedDateTime();

        assertThat(OperatingHoursSchedule.isOpen(WEEKLY, tuesdayNoon)).isFalse();
        assertThat(OperatingHoursSchedule.isOpen(WEEKLY, fridayLate)).isTrue();
        assertThat(OperatingHoursSchedule.isOpen(WEEKLY, saturdayEarly)).isTrue();
        assertThat(OperatingHoursSchedule.isOpen(WEEKLY, fridayAfternoon)).isFalse();
    }

    @Test
    void rejectsInvalidSchedules() {
        assertThatThrownBy(() -> OperatingHoursSchedule.validate(
                "{\"monday\":{\"enabled\":true,\"start\":\"09:00\",\"end\":\"09:00\"}}"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must differ");
        assertThatThrownBy(() -> OperatingHoursSchedule.validate("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid weekly schedule");
    }

    @Test
    void exposesBookingRangesForConfiguredDaysAndClosedDays() {
        var monday = OperatingHoursSchedule.rangesFor(WEEKLY, LocalDate.of(2026, 7, 6), ZoneId.of("Africa/Kinshasa"));
        var tuesday = OperatingHoursSchedule.rangesFor(WEEKLY, LocalDate.of(2026, 7, 7), ZoneId.of("Africa/Kinshasa"));

        assertThat(monday).singleElement().satisfies(range -> {
            assertThat(range.start().toLocalTime()).isEqualTo(java.time.LocalTime.of(9, 0));
            assertThat(range.end().toLocalTime()).isEqualTo(java.time.LocalTime.of(17, 0));
        });
        assertThat(tuesday).isEmpty();
    }

    @Test
    void describesTheConfiguredWeekForConversationContext() {
        assertThat(OperatingHoursSchedule.describe(WEEKLY))
                .contains("Monday 09:00-17:00")
                .contains("Tuesday closed")
                .contains("Friday 20:00-02:00");
        assertThat(OperatingHoursSchedule.describe("weekdays"))
                .isEqualTo("Monday-Friday 09:00-17:00; Saturday-Sunday closed.");
    }
}
