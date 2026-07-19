package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;

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
                Map.of("date", "2026-07-22", "time_preference", "15:00", "duration_minutes", 60)
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
                Map.of("date", "2026-07-22", "time_preference", "15:00", "duration_minutes", 60)
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
    void acceptsLanguageIndependentNormalizedNoonRequest() {
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
                Map.of("date", "2026-07-22", "time_preference", "12:00", "duration_minutes", 60)
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
                Map.of("date", "2026-07-22", "time_preference", "17:00", "duration_minutes", 60)
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
    void enforcesPromptHoursWhenTheStructuredScheduleWasLeftAsAlways() {
        var fixture = fixture("always", List.of());
        when(fixture.agent.getSystemPrompt()).thenReturn("""
                - Hours: Mon 09:00-17:00; Wed 09:00-17:00; Thu 09:00-17:00 (Africa/Nairobi)
                """);

        var closingTime = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "availability-closing-prompt", "check_availability",
                Map.of("date", "2026-07-22", "time_preference", "17:00", "duration_minutes", 60)
        ));
        var saturday = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "availability-saturday-prompt", "check_availability",
                Map.of("date", "2026-07-25", "time_preference", "12:00", "duration_minutes", 60)
        ));

        assertThat(closingTime.result()).containsEntry("status", "outside_business_hours");
        assertThat(saturday.result()).containsEntry("status", "closed_by_business_hours");
        verify(fixture.provider, never()).availability(
                fixture.agent, LocalDate.of(2026, 7, 22), 60, java.time.ZoneId.of("UTC")
        );
        verify(fixture.provider, never()).availability(
                fixture.agent, LocalDate.of(2026, 7, 25), 60, java.time.ZoneId.of("UTC")
        );
    }

    @Test
    void savesBookingLocallyWhenGoogleCredentialIsUnavailable() {
        var fixture = fixture(HOURS, List.of());
        when(fixture.agent.getCalendarProvider()).thenReturn("Google Calendar");
        var booking = mock(com.sauti.calendar.Booking.class);
        when(booking.getId()).thenReturn(java.util.UUID.randomUUID());
        when(booking.getBookingReference()).thenReturn("SAT-AB12CD34");
        when(booking.getAppointmentAt()).thenReturn(OffsetDateTime.parse("2026-07-23T12:00:00Z"));
        when(booking.getCalendarSyncStatus()).thenReturn("pending_owner_action");
        when(fixture.bookingService.create(any(), any(), isNull())).thenReturn(booking);

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "booking-without-google", "book_slot",
                Map.of(
                        "appointment_at", "2026-07-23T12:00:00Z",
                        "caller_name", "Zachary",
                        "caller_phone", "01115753441",
                        "service_type", "Consultation"
                )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "booking_saved_pending_calendar")
                .containsEntry("bookingNumber", "SAT-AB12CD34")
                .containsEntry("calendarSynced", false);
    }

    @Test
    void requiresTemplateSpecificCustomerDetailsBeforeCreatingBooking() {
        var fixture = fixture(HOURS, List.of());
        when(fixture.agent.getBookingRequiredFields()).thenReturn(List.of(
                "caller_name", "caller_phone", "service_type", "appointment_at", "patient_date_of_birth"
        ));

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "booking-missing-vertical-field", "book_slot",
                Map.of(
                        "appointment_at", "2026-07-23T12:00:00Z",
                        "caller_name", "Zachary",
                        "caller_phone", "01115753441",
                        "service_type", "Consultation"
                )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "missing_required_information")
                .containsEntry("bookingCreated", false);
        assertThat((List<?>) result.result().get("missingFields"))
                .extracting(Object::toString)
                .containsExactly("patient_date_of_birth");
        verifyNoInteractions(fixture.bookingService);
    }

    @Test
    void answersBusinessHoursDeterministicallyWithoutQueryingCalendarSlots() {
        var fixture = fixture("always", List.of());
        when(fixture.call.getLanguageDetected()).thenReturn("fr");
        when(fixture.agent.getSystemPrompt()).thenReturn("""
                - Hours: Mon 09:00-17:00; Wed 09:00-17:00; Thu 09:00-17:00 (Africa/Nairobi)
                """);

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "business-hours", "get_business_hours", Map.of()
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result()).containsEntry("status", "business_hours");
        assertThat(result.result().get("schedule").toString())
                .contains("Monday 09:00-17:00")
                .contains("Saturday closed", "Sunday closed");
        verifyNoInteractions(fixture.provider);
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
        when(agent.getSystemPrompt()).thenReturn("");
        when(agent.getBookingRequiredFields()).thenReturn(List.of(
                "caller_name", "caller_phone", "service_type", "appointment_at"
        ));
        when(factory.forTool(tool, tenantId)).thenReturn(provider);
        when(provider.availability(
                agent, LocalDate.of(2026, 7, 22), 60, java.time.ZoneId.of("UTC")
        )).thenReturn(slots);
        return new Fixture(
                new SautiCalendarFulfillment(factory, bookingService),
                call,
                agent,
                tool,
                provider,
                bookingService
        );
    }

    private record Fixture(
            SautiCalendarFulfillment fulfillment,
            Call call,
            Agent agent,
            AgentTool tool,
            CalendarProvider provider,
            BookingService bookingService
    ) {
    }
}
