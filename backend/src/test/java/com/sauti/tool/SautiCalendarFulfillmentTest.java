package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;

import com.sauti.agent.Agent;
import com.sauti.calendar.BookingService;
import com.sauti.calendar.CalendarAvailabilitySlot;
import com.sauti.calendar.CalendarProvider;
import com.sauti.call.Call;
import com.sauti.call.CallIntakeNoteService;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.ConversationMessage;
import com.sauti.session.CallSessionStore;
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
    void excludesAnExistingSautiBookingAndReturnsNearbyAlternatives() {
        var openHours = """
                {"wednesday":{"enabled":true,"start":"09:00","end":"17:00"}}
                """;
        var occupied = new CalendarAvailabilitySlot(
                OffsetDateTime.parse("2026-07-22T12:00:00Z"),
                OffsetDateTime.parse("2026-07-22T13:00:00Z"),
                "12:00"
        );
        var alternative = new CalendarAvailabilitySlot(
                OffsetDateTime.parse("2026-07-22T13:00:00Z"),
                OffsetDateTime.parse("2026-07-22T14:00:00Z"),
                "13:00"
        );
        var fixture = fixture(openHours, List.of(occupied, alternative));
        when(fixture.bookingService.excludeLocalConflicts(
                any(), any(), any(), any(), any()
        )).thenReturn(List.of(alternative));

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "availability-local-conflict", "check_availability",
                Map.of("date", "2026-07-22", "time_preference", "12:00", "duration_minutes", 60)
        ));

        assertThat(result.result())
                .containsEntry("status", "requested_time_unavailable")
                .containsEntry("requestedTimeAvailable", false)
                .containsEntry("totalAvailableSlots", 1);
        @SuppressWarnings("unchecked")
        var slots = (List<Map<String, String>>) result.result().get("slots");
        assertThat(slots).singleElement().satisfies(slot ->
                assertThat(slot.get("start")).startsWith("2026-07-22T13:00")
        );
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

        var arguments = new java.util.LinkedHashMap<String, Object>();
        arguments.put("appointment_at", "2026-07-23T12:00:00Z");
        arguments.put("caller_name", "Zachary");
        arguments.put("caller_phone", "01115753441");
        arguments.put("caller_email", "zachary.123@gmail.com");
        arguments.put("service_type", "Consultation");
        arguments.put("duration_minutes", 75);
        arguments.put("customer_details", Map.of("preferred_staff", "any available staff"));
        var review = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "booking-review", "book_slot", Map.copyOf(arguments)
        ));

        assertThat(review.success()).isTrue();
        assertThat(review.result())
                .containsEntry("status", "booking_review_required")
                .containsEntry("bookingCreated", false);
        assertThat(review.result().get("spokenResponse").toString())
                .contains("Z for Zulu, A for Alfa, C for Charlie, H for Hotel, A for Alfa, R for Romeo, Y for Yankee")
                .contains("0, 1, 1, 1, 5, 7, 5, 3, 4, 4, 1")
                .contains("at sign", "dot", "G for Golf, M for Mike, A for Alfa, I for India, L for Lima")
                .contains("75 minutes", "Preferred Staff: any available staff")
                .doesNotContain(review.result().get("reviewToken").toString());
        verifyNoInteractions(fixture.bookingService);

        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage("user", "Everything is correct.")));
        arguments.put("review_token", review.result().get("reviewToken"));
        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "booking-without-google", "book_slot", Map.copyOf(arguments)
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "booking_saved_pending_calendar")
                .containsEntry("bookingNumber", "SAT-AB12CD34")
                .containsEntry("calendarSynced", false);
    }

    @Test
    void usesTheAppointmentRecipientsNameInsteadOfThePersonSpeaking() {
        var fixture = fixture(HOURS, List.of());
        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage("user", "Alexandra")));
        when(fixture.intakeNotes.notes(fixture.call, "Alexandra")).thenReturn(Map.of(
                "caller_name", "Zachary",
                "booking_for_relation", "wife",
                "appointment_name", "Alexandra",
                "service_type", "A woman hairstyle"
        ));

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "wife-booking-review", "book_slot", Map.of(
                        "appointment_at", "2026-07-23T13:00:00Z",
                        "appointment_name", "Alexandra",
                        "caller_phone", "0105753441",
                        "service_type", "Women hairstyle"
                )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result()).containsEntry("status", "booking_review_required");
        assertThat(result.result().get("spokenResponse").toString())
                .contains("Alexandra: A for Alfa, L for Lima, E for Echo, X for X-ray")
                .doesNotContain("Zachary: Z for Zulu");
    }

    @Test
    void refusesToUseTheSpeakersNameWhenTheThirdPartyNameIsStillMissing() {
        var fixture = fixture(HOURS, List.of());
        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage("user", "I am booking for my wife.")));
        when(fixture.intakeNotes.notes(fixture.call, "I am booking for my wife.")).thenReturn(Map.of(
                "caller_name", "Zachary",
                "booking_for_relation", "wife"
        ));

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "wife-name-missing", "book_slot", Map.of(
                        "appointment_at", "2026-07-23T13:00:00Z",
                        "caller_name", "Zachary",
                        "caller_phone", "0105753441",
                        "service_type", "Women hairstyle"
                )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result())
                .containsEntry("status", "missing_required_information")
                .containsEntry("nextMissingField", "appointment_name");
        verifyNoInteractions(fixture.bookingService);
    }

    @Test
    void normalizesARealtimeLocalAppointmentUsingTheBusinessTimezone() {
        var fixture = fixture(HOURS, List.of());
        when(fixture.agent.getTimezone()).thenReturn("Africa/Cairo");

        var result = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "local-time-review", "book_slot", Map.of(
                        "appointment_at", "2026-07-23T13:00:00",
                        "appointment_name", "Alexandra",
                        "caller_phone", "0105753441",
                        "service_type", "Women hairstyle"
                )
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.result()).containsEntry("status", "booking_review_required");
        @SuppressWarnings("unchecked")
        var review = (Map<String, Object>) result.result().get("bookingReview");
        assertThat(review).containsEntry("appointmentAt", "2026-07-23T13:00+03:00");
    }

    @Test
    void requiresARealApprovalAfterTheServerGeneratedReview() {
        var fixture = fixture(HOURS, List.of());
        var arguments = new java.util.LinkedHashMap<String, Object>();
        arguments.put("appointment_at", "2026-07-23T13:00:00Z");
        arguments.put("caller_name", "Alexandra");
        arguments.put("caller_phone", "0105753441");
        arguments.put("service_type", "Women hairstyle");
        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage("user", "Alexandra")));
        var review = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "review-before-confusion", "book_slot", Map.copyOf(arguments)
        ));
        arguments.put("review_token", review.result().get("reviewToken"));

        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage("user", "What do you want me to do?")));
        var notApproved = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "not-an-approval", "book_slot", Map.copyOf(arguments)
        ));

        assertThat(notApproved.success()).isTrue();
        assertThat(notApproved.result())
                .containsEntry("status", "booking_confirmation_required")
                .containsEntry("bookingCreated", false);
        verifyNoInteractions(fixture.bookingService);

        var booking = mock(com.sauti.calendar.Booking.class);
        when(booking.getId()).thenReturn(java.util.UUID.randomUUID());
        when(booking.getBookingReference()).thenReturn("SAT-WIFEOK");
        when(booking.getAppointmentAt()).thenReturn(OffsetDateTime.parse("2026-07-23T13:00:00Z"));
        when(booking.getCalendarSyncStatus()).thenReturn("not_configured");
        when(fixture.bookingService.create(any(), any(), any())).thenReturn(booking);
        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage("user", "Yes, it is.")));
        var reviewToken = arguments.get("review_token");
        arguments.clear();
        arguments.put("review_token", reviewToken);
        arguments.put("appointment_name", "Zachary");
        var approved = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "actual-approval", "book_slot", Map.copyOf(arguments)
        ));

        assertThat(approved.result())
                .containsEntry("bookingCreated", true)
                .containsEntry("bookingNumber", "SAT-WIFEOK");
        verify(fixture.bookingService).create(any(), argThat(request ->
                "Alexandra".equals(request.callerName())
        ), any());
    }

    @Test
    void requiresTemplateSpecificCustomerDetailsBeforeCreatingBooking() {
        var fixture = fixture(HOURS, List.of());
        when(fixture.agent.getBookingRequiredFields()).thenReturn(List.of(
                "caller_name", "caller_phone", "service_type", "appointment_at",
                "patient_date_of_birth", "insurance_member_number"
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
                .containsEntry("bookingCreated", false)
                .containsEntry("nextMissingField", "patient_date_of_birth")
                .containsEntry("remainingMissingFieldCount", 2);
        assertThat(result.result()).doesNotContainKey("missingFields");
        verifyNoInteractions(fixture.bookingService);
    }

    @Test
    void requiresANewReviewWhenTheCallerCorrectsAReviewedDetail() {
        var fixture = fixture(HOURS, List.of());
        var arguments = new java.util.LinkedHashMap<String, Object>();
        arguments.put("appointment_at", "2026-07-23T12:00:00Z");
        arguments.put("caller_name", "Zachary");
        arguments.put("caller_phone", "01115753441");
        arguments.put("caller_email", "wrong@example.com");
        arguments.put("service_type", "Men hairstyle");
        var first = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "review-before-correction", "book_slot", Map.copyOf(arguments)
        ));

        arguments.put("caller_email", "zachary.123@gmail.com");
        arguments.put("review_token", first.result().get("reviewToken"));
        var corrected = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "review-after-correction", "book_slot", Map.copyOf(arguments)
        ));

        assertThat(corrected.result())
                .containsEntry("status", "booking_review_required")
                .containsEntry("bookingCreated", false);
        assertThat(corrected.result().get("reviewToken"))
                .isNotEqualTo(first.result().get("reviewToken"));
        assertThat(corrected.result().get("spokenResponse").toString())
                .contains("Thanks for the correction")
                .contains("email spelled")
                .contains("at sign", "G for Golf, M for Mike, A for Alfa, I for India, L for Lima")
                .doesNotContain("the name", "phone number", "Men hairstyle");
        verifyNoInteractions(fixture.bookingService);
    }

    @Test
    void usesTheCallersExactLatestPhoneDigitsInsteadOfModelReconstruction() {
        var fixture = fixture(HOURS, List.of());
        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage(
                        "user", "Of course, my number is 0111-575-3441."
                )));
        when(fixture.intakeNotes.notes(
                fixture.call, "Of course, my number is 0111-575-3441."
        )).thenReturn(Map.of("caller_name", "Akari"));
        var arguments = new java.util.LinkedHashMap<String, Object>();
        arguments.put("appointment_at", "2026-07-23T12:00:00Z");
        arguments.put("caller_name", "Zachary");
        arguments.put("caller_phone", "011175753441");
        arguments.put("service_type", "Men hairstyle");

        var first = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "exact-phone-review", "book_slot", Map.copyOf(arguments)
        ));

        assertThat(first.result().get("spokenResponse").toString())
                .contains("Akari: A for Alfa, K for Kilo, A for Alfa, R for Romeo, I for India")
                .doesNotContain("Zachary")
                .contains("0, 1, 1, 1, 5, 7, 5, 3, 4, 4, 1")
                .doesNotContain("0, 1, 1, 1, 7, 5, 7, 5");

        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage(
                        "user", "Actually, the corrected number is 0115753441."
                )));
        arguments.put("caller_name", "Akari");
        arguments.put("review_token", first.result().get("reviewToken"));
        arguments.put("caller_phone", "011157543441");
        var correction = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "exact-phone-correction", "book_slot", Map.copyOf(arguments)
        ));

        assertThat(correction.result())
                .containsEntry("correctionReview", true)
                .containsEntry("changedFields", List.of("caller_phone"));
        assertThat(correction.result().get("spokenResponse").toString())
                .contains("phone number 0, 1, 1, 5, 7, 5, 3, 4, 4, 1")
                .doesNotContain("Men hairstyle", "A for Alfa");

        var booking = mock(com.sauti.calendar.Booking.class);
        when(booking.getId()).thenReturn(java.util.UUID.randomUUID());
        when(booking.getBookingReference()).thenReturn("SAT-PHONEOK");
        when(booking.getAppointmentAt()).thenReturn(OffsetDateTime.parse("2026-07-23T12:00:00Z"));
        when(booking.getCalendarSyncStatus()).thenReturn("not_configured");
        when(fixture.bookingService.create(any(), any(), any())).thenReturn(booking);
        when(fixture.callSessionStore.conversationHistory("call-sid"))
                .thenReturn(List.of(new ConversationMessage("user", "It is okay.")));
        arguments.put("review_token", correction.result().get("reviewToken"));
        arguments.put("caller_phone", "011157543441");
        var booked = fixture.fulfillment.execute(fixture.call, fixture.tool, new LlmToolCall(
                "book-after-phone-approval", "book_slot", Map.copyOf(arguments)
        ));

        assertThat(booked.result()).containsEntry("bookingCreated", true);
        verify(fixture.bookingService).create(any(), argThat(request ->
                "0115753441".equals(request.callerPhone()) && "Akari".equals(request.callerName())
        ), any());
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
        var callSessionStore = mock(CallSessionStore.class);
        var intakeNotes = mock(CallIntakeNoteService.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var tool = mock(AgentTool.class);
        var provider = mock(CalendarProvider.class);
        when(call.getAgent()).thenReturn(agent);
        when(call.getId()).thenReturn(java.util.UUID.randomUUID());
        when(call.getTwilioCallSid()).thenReturn("call-sid");
        var tenant = mock(com.sauti.tenant.Tenant.class);
        var tenantId = java.util.UUID.randomUUID();
        when(call.getTenant()).thenReturn(tenant);
        when(tenant.getId()).thenReturn(tenantId);
        when(call.getLanguageDetected()).thenReturn("en");
        when(agent.getDefaultLanguage()).thenReturn("en");
        when(agent.getId()).thenReturn(java.util.UUID.randomUUID());
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
        when(bookingService.excludeLocalConflicts(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(4));
        return new Fixture(
                new SautiCalendarFulfillment(factory, bookingService, callSessionStore, intakeNotes),
                call,
                agent,
                tool,
                provider,
                bookingService,
                callSessionStore,
                intakeNotes
        );
    }

    private record Fixture(
            SautiCalendarFulfillment fulfillment,
            Call call,
            Agent agent,
            AgentTool tool,
            CalendarProvider provider,
            BookingService bookingService,
            CallSessionStore callSessionStore,
            CallIntakeNoteService intakeNotes
    ) {
    }
}
