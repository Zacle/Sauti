package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.calendar.Booking;
import com.sauti.call.Call;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarSpeechRendererTest {
    @Test
    void speaksFrenchWholeHoursWithoutZeroZero() {
        var call = frenchCall();
        var booking = mock(Booking.class);
        when(booking.getServiceType()).thenReturn("consultation dentaire");
        when(booking.getAppointmentAt()).thenReturn(OffsetDateTime.parse("2026-07-22T15:00:00+03:00"));

        var response = BookingSpeechRenderer.render(call, booking, "SAT-ABC12345");

        assertThat(response)
                .contains("mercredi 22 juillet à 15 heures")
                .doesNotContain("15:00")
                .doesNotContain("zéro zéro");
    }

    @Test
    void formatsAvailabilityAlternativesAsSpeechInsteadOfRawClockValues() {
        var response = AvailabilitySpeechRenderer.render(frenchCall(), Map.of(
                "status", "slots_available",
                "slots", List.of(Map.of(
                        "start", "2026-07-22T15:00:00+03:00",
                        "displayString", "15:00"
                ))
        ));

        assertThat(response).contains("15 heures").doesNotContain("15:00");
    }

    private Call frenchCall() {
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getAgent()).thenReturn(agent);
        when(call.getLanguageDetected()).thenReturn("fr");
        when(agent.getDefaultLanguage()).thenReturn("fr");
        return call;
    }
}
