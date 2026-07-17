package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LocalCalendarProviderTest {
    private static final String HOURS = """
            {"wednesday":{"enabled":true,"start":"14:00","end":"16:00"},
             "thursday":{"enabled":false,"start":"09:00","end":"17:00"}}
            """;

    @Test
    void previewAvailabilityRespectsAgentOpeningHours() {
        var agent = mock(Agent.class);
        when(agent.getOperatingHours()).thenReturn(HOURS);
        var provider = new LocalCalendarProvider();

        var wednesday = provider.availability(agent, LocalDate.of(2026, 7, 22), 60, ZoneId.of("UTC"));
        var thursday = provider.availability(agent, LocalDate.of(2026, 7, 23), 60, ZoneId.of("UTC"));

        assertThat(wednesday).extracting(slot -> slot.start().toLocalTime().toString())
                .containsExactly("14:00", "14:30", "15:00");
        assertThat(thursday).isEmpty();
    }
}
