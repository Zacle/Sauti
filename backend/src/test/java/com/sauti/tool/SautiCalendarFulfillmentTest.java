package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.calendar.BookingService;
import com.sauti.calendar.CalendarAvailabilitySlot;
import com.sauti.calendar.CalendarProvider;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SautiCalendarFulfillmentTest {
    private static final String HOURS = """
            {"wednesday":{"enabled":false,"start":"09:00","end":"17:00"},
             "thursday":{"enabled":true,"start":"09:00","end":"17:00"}}
            """;

    @Test
    void explainsWhenTheRequestedDayIsClosedAndPreservesTheExactTime() {
        var fixture = fixture(HOURS, List.of());

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "availability-1", "check_availability",
                Map.of("date", "2026-07-22", "time_preference", "3 p.m.", "duration_minutes", 60)
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "closed_by_business_hours")
                .containsEntry("businessOpenOnRequestedDate", false)
                .containsEntry("requestedTime", "15:00")
                .containsEntry("requestedTimeAvailable", false);
        @SuppressWarnings("unchecked")
        var nextWindows = (List<Map<String, String>>) result.result().get("nextOpenBusinessWindows");
        assertThat(nextWindows).first().satisfies(window -> assertThat(window)
                .containsEntry("date", "2026-07-23")
                .containsEntry("opens", "09:00")
                .containsEntry("closes", "17:00"));
        verify(fixture.provider, never()).availability(
                fixture.agent, LocalDate.of(2026, 7, 22), 60, java.time.ZoneId.of("UTC")
        );
    }

    @Test
    void identifiesAnExactRequestedSlotWithoutChangingThreePmToFourPm() {
        var openHours = """
                {"wednesday":{"enabled":true,"start":"09:00","end":"17:00"}}
                """;
        var slot = new CalendarAvailabilitySlot(
                OffsetDateTime.parse("2026-07-22T15:00:00Z"),
                OffsetDateTime.parse("2026-07-22T16:00:00Z"),
                "15:00"
        );
        var fixture = fixture(openHours, List.of(slot));

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "availability-2", "check_availability",
                Map.of("date", "2026-07-22", "time_preference", "3 P.M.", "duration_minutes", 60)
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "requested_time_available")
                .containsEntry("requestedTime", "15:00")
                .containsEntry("requestedTimeWithinOperatingHours", true)
                .containsEntry("requestedTimeAvailable", true);
        @SuppressWarnings("unchecked")
        var matching = (Map<String, String>) result.result().get("matchingSlot");
        assertThat(matching.get("start")).startsWith("2026-07-22T15:00");
    }

    @Test
    void interpretsFrenchMidiAsAnExactNoonRequest() {
        var openHours = """
                {"wednesday":{"enabled":true,"start":"09:00","end":"17:00"}}
                """;
        var slot = new CalendarAvailabilitySlot(
                OffsetDateTime.parse("2026-07-22T12:00:00Z"),
                OffsetDateTime.parse("2026-07-22T13:00:00Z"),
                "12:00"
        );
        var fixture = fixture(openHours, List.of(slot));

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "availability-midi", "check_availability",
                Map.of("date", "2026-07-22", "time_preference", "midi", "duration_minutes", 60)
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "requested_time_available")
                .containsEntry("requestedTime", "12:00")
                .containsEntry("requestedTimeAvailable", true);
    }

    @Test
    void rejectsARequestThatWouldEndAfterClosingWithoutCallingGoogle() {
        var openHours = """
                {"wednesday":{"enabled":true,"start":"09:00","end":"17:00"}}
                """;
        var fixture = fixture(openHours, List.of());

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "availability-closing", "check_availability",
                Map.of("date", "2026-07-22", "time_preference", "5 p.m.", "duration_minutes", 60)
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "outside_business_hours")
                .containsEntry("requestedTime", "17:00")
                .containsEntry("requestedTimeWithinOperatingHours", false)
                .containsEntry("requestedTimeAvailable", false);
        verify(fixture.provider, never()).availability(
                fixture.agent, LocalDate.of(2026, 7, 22), 60, java.time.ZoneId.of("UTC")
        );
    }

    @Test
    void convertsAProviderOutageIntoAControlledSuccessfulDecision() {
        var openHours = """
                {"wednesday":{"enabled":true,"start":"09:00","end":"17:00"}}
                """;
        var fixture = fixture(openHours, List.of());
        when(fixture.provider.availability(
                fixture.agent, LocalDate.of(2026, 7, 22), 60, java.time.ZoneId.of("UTC")
        )).thenThrow(new IllegalStateException("upstream unavailable"));

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "availability-outage", "check_availability",
                Map.of("date", "2026-07-22", "time_preference", "12:00", "duration_minutes", 60)
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "calendar_temporarily_unavailable")
                .containsEntry("calendarLive", false)
                .containsEntry("requestedTime", "12:00");
        assertThat(result.result().get("spokenResponse").toString())
                .contains("cannot confirm live availability")
                .contains("has not been booked");
    }

    private Fixture fixture(String hours, List<CalendarAvailabilitySlot> slots) {
        var factory = mock(CalendarProviderFactory.class);
        var bookingService = mock(BookingService.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var tool = mock(AgentTool.class);
        var provider = mock(CalendarProvider.class);
        when(call.getAgent()).thenReturn(agent);
        var tenant = mock(com.sauti.tenant.Tenant.class);
        var tenantId = java.util.UUID.randomUUID();
        when(call.getTenant()).thenReturn(tenant);
        when(tenant.getId()).thenReturn(tenantId);
        when(call.getLanguageDetected()).thenReturn("en");
        when(agent.getDefaultLanguage()).thenReturn("en");
        when(agent.getTimezone()).thenReturn("UTC");
        when(agent.getOperatingHours()).thenReturn(hours);
        when(factory.forTool(tool, tenantId)).thenReturn(provider);
        when(provider.availability(
                agent, LocalDate.of(2026, 7, 22), 60, java.time.ZoneId.of("UTC")
        )).thenReturn(slots);
        return new Fixture(new SautiCalendarFulfillment(factory, bookingService), call, agent, tool, provider);
    }

    private record Fixture(
            SautiCalendarFulfillment fulfillment,
            Call call,
            Agent agent,
            AgentTool tool,
            CalendarProvider provider
    ) {
    }
}
