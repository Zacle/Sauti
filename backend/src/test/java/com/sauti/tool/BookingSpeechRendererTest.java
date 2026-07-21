package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.calendar.Booking;
import com.sauti.call.Call;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class BookingSpeechRendererTest {
    @Test
    void distinguishesConfirmedLocalAndPendingExternalCalendarStatuses() {
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var booking = mock(Booking.class);
        when(call.getAgent()).thenReturn(agent);
        when(call.getLanguageDetected()).thenReturn("en");
        when(agent.getDefaultLanguage()).thenReturn("en");
        when(booking.getServiceType()).thenReturn("men hairstyle");
        when(booking.getAppointmentAt()).thenReturn(OffsetDateTime.parse("2026-07-22T15:00:00Z"));

        when(booking.getCalendarSyncStatus()).thenReturn("synced");
        assertThat(BookingSpeechRenderer.render(call, booking, "SAT-SYNCED1234"))
                .contains("is booked", "SAT-SYNCED1234")
                .doesNotContain("pending");

        when(booking.getCalendarSyncStatus()).thenReturn("not_configured");
        assertThat(BookingSpeechRenderer.render(call, booking, "SAT-LOCAL12345"))
                .contains("is saved", "SAT-LOCAL12345")
                .doesNotContain("external calendar");

        when(booking.getCalendarSyncStatus()).thenReturn("pending_owner_action");
        assertThat(BookingSpeechRenderer.render(call, booking, "SAT-PENDING123"))
                .contains("saved in our system", "external calendar confirmation is still pending", "SAT-PENDING123")
                .doesNotContain("is booked");
    }
}
