package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class GoogleCalendarApiClientTest {

    @Test
    void formatsExactMinuteBoundariesAsGoogleRfc3339Timestamps() {
        assertThat(GoogleCalendarApiClient.googleDateTime(
                OffsetDateTime.parse("2026-08-03T09:00:00+03:00")
        )).isEqualTo("2026-08-03T09:00:00+03:00");
    }

    @Test
    void formatsUtcWithSecondsAndZuluOffset() {
        assertThat(GoogleCalendarApiClient.googleDateTime(
                OffsetDateTime.parse("2026-08-03T06:30:45Z")
        )).isEqualTo("2026-08-03T06:30:45Z");
    }
}
