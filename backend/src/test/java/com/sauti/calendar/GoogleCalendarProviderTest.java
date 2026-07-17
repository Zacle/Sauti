package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.tool.CalendarCredential;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleCalendarProviderTest {
    private static final String HOURS = """
            {"monday":{"enabled":true,"start":"10:00","end":"12:00"},
             "tuesday":{"enabled":false,"start":"09:00","end":"17:00"}}
            """;

    @Test
    void usesAgentOperatingHoursAndSkipsClosedDays() {
        var credential = mock(CalendarCredential.class);
        var client = mock(GoogleCalendarApiClient.class);
        var agent = mock(Agent.class);
        when(agent.getOperatingHours()).thenReturn(HOURS);
        when(client.busy(any(), any(), any(), any())).thenReturn(List.of());
        var provider = new GoogleCalendarProvider(credential, client);

        var monday = provider.availability(agent, LocalDate.of(2026, 7, 6), 60, ZoneId.of("UTC"));
        var tuesday = provider.availability(agent, LocalDate.of(2026, 7, 7), 60, ZoneId.of("UTC"));

        assertThat(monday).extracting(slot -> slot.start().toLocalTime().toString())
                .containsExactly("10:00", "10:30", "11:00");
        assertThat(tuesday).isEmpty();
        verify(client).busy(any(), any(), any(), any());
        verify(client, never()).createEvent(any(), any());
    }
}
