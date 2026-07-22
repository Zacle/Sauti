package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.session.CallSessionStore;
import com.sauti.session.BookingDraft;
import com.sauti.session.ConversationState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConversationStateToolTest {
    @Test
    void correctedSpeakerNameUpdatesASelfBookingWithoutDependingOnCallerWording() {
        var sessions = mock(CallSessionStore.class);
        var call = call("semantic-call");
        when(sessions.conversationState("semantic-call")).thenReturn(Optional.of(new ConversationState(
                Map.of("caller_name", "Art Gary", "appointment_name", "Art Gary"),
                ConversationState.SUBJECT_SELF,
                ConversationState.INTENT_ACTIVE,
                1
        )));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("caller_name", "Zachary"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "Thanks for the correction. Which service would you like?"
        )));

        var state = captureState(sessions, "semantic-call");
        assertThat(state.values())
                .containsEntry("caller_name", "Zachary")
                .containsEntry("appointment_name", "Zachary")
                .doesNotContainValue("Art Gary");
        assertThat(result.result()).containsEntry(
                "spokenResponse", "Thanks for the correction. Which service would you like?"
        );
    }

    @Test
    void callerCorrectionPreservesAnExplicitlyDifferentRecipientInAnyLanguage() {
        var sessions = mock(CallSessionStore.class);
        var call = call("multilingual-call");
        when(sessions.conversationState("multilingual-call")).thenReturn(Optional.of(new ConversationState(
                Map.of(
                        "caller_name", "Art Gary",
                        "appointment_name", "Alexandra",
                        "recipient_relation", "wife"
                ),
                ConversationState.SUBJECT_OTHER,
                ConversationState.INTENT_ACTIVE,
                4
        )));
        var tool = new ConversationStateTool(sessions);

        tool.execute(call, toolCall(Map.of(
                "updates", Map.of("caller_name", "زكريا"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "شكرًا للتصحيح. ما الخدمة التي تريدها ألكسندرا؟"
        )));

        assertThat(captureState(sessions, "multilingual-call").values())
                .containsEntry("caller_name", "زكريا")
                .containsEntry("appointment_name", "Alexandra")
                .containsEntry("recipient_relation", "wife");
    }

    @Test
    void explicitSelfSubjectAndPausedIntentAreDeterministicSemanticTransitions() {
        var sessions = mock(CallSessionStore.class);
        var call = call("subject-call");
        when(sessions.conversationState("subject-call")).thenReturn(Optional.of(new ConversationState(
                Map.of(
                        "caller_name", "Zachary",
                        "appointment_name", "Alexandra",
                        "recipient_relation", "wife",
                        "review_decision", "approved"
                ),
                ConversationState.SUBJECT_OTHER,
                ConversationState.INTENT_ACTIVE,
                7
        )));
        var tool = new ConversationStateTool(sessions);

        tool.execute(call, toolCall(Map.of(
                "updates", Map.of(),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "self",
                "booking_intent", "paused",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "Hakuna nafasi itakayowekwa. Asante kwa kupiga simu."
        )));

        var state = captureState(sessions, "subject-call");
        assertThat(state.bookingSubject()).isEqualTo(ConversationState.SUBJECT_SELF);
        assertThat(state.bookingIntent()).isEqualTo(ConversationState.INTENT_PAUSED);
        assertThat(state.values())
                .containsEntry("appointment_name", "Zachary")
                .doesNotContainKeys("recipient_relation", "review_decision");
    }

    @Test
    void changingFromSelfToAnUnnamedThirdPartyCannotReuseTheCallersName() {
        var sessions = mock(CallSessionStore.class);
        var call = call("new-recipient-call");
        when(sessions.conversationState("new-recipient-call")).thenReturn(Optional.of(new ConversationState(
                Map.of("caller_name", "Zachary", "appointment_name", "Zachary"),
                ConversationState.SUBJECT_SELF,
                ConversationState.INTENT_ACTIVE,
                2
        )));
        var tool = new ConversationStateTool(sessions);

        tool.execute(call, toolCall(Map.of(
                "updates", Map.of("recipient_relation", "wife"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "other",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "What is her name?"
        )));

        assertThat(captureState(sessions, "new-recipient-call").values())
                .containsEntry("caller_name", "Zachary")
                .containsEntry("recipient_relation", "wife")
                .doesNotContainKey("appointment_name");
    }

    @Test
    void businessActionSuppressesPreambleAndAuthorizesExactlyOneConfiguredToolName() {
        var sessions = mock(CallSessionStore.class);
        var call = call("business-action-call");
        when(sessions.conversationState("business-action-call"))
                .thenReturn(Optional.of(ConversationState.empty()));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("preferred_day", "2026-07-30", "preferred_time", "13:00"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "active",
                "next_action", "use_business_tool",
                "business_tool", "check_availability",
                "spoken_response", "Please hold while I check."
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "use_business_tool")
                .containsEntry("nextTool", "check_availability")
                .containsEntry("nextToolAuthorized", true)
                .containsEntry("nextToolArguments", Map.of(
                        "date", "2026-07-30", "time_preference", "13:00"
                ))
                .doesNotContainKey("spokenResponse");
        verify(sessions).updatePendingBooking("business-action-call", null);
    }

    @Test
    void activeBookingDateOrTimeAlwaysContinuesToLiveAvailability() {
        var sessions = mock(CallSessionStore.class);
        var call = call("availability-transition-call");
        when(sessions.conversationState("availability-transition-call")).thenReturn(Optional.of(
                new ConversationState(
                        Map.of("service_type", "Nails", "appointment_name", "Sandria"),
                        ConversationState.SUBJECT_OTHER,
                        ConversationState.INTENT_ACTIVE,
                        3
                )
        ));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("preferred_day", "2026-07-23", "preferred_time", "15:00"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "Thursday at 3 p.m. should be fine."
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "use_business_tool")
                .containsEntry("nextTool", "check_availability")
                .containsEntry("nextToolAuthorized", true)
                .containsEntry("nextToolArguments", Map.of(
                        "date", "2026-07-23", "time_preference", "15:00"
                ))
                .doesNotContainKey("spokenResponse");
        verify(sessions).updatePendingBooking("availability-transition-call", null);
    }

    @Test
    void correctedReviewDateChecksAvailabilityBeforeRegeneratingTheReview() {
        var sessions = mock(CallSessionStore.class);
        var call = call("review-date-correction-call");
        when(sessions.conversationState("review-date-correction-call")).thenReturn(Optional.of(
                new ConversationState(
                        Map.of(
                                "caller_name", "Zachary",
                                "appointment_name", "Zachary",
                                "caller_phone", "0105753221",
                                "service_type", "Men hairstyle",
                                "preferred_day", "Tuesday",
                                "preferred_time", "15:00"
                        ),
                        ConversationState.SUBJECT_SELF,
                        ConversationState.INTENT_ACTIVE,
                        7
                )
        ));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("preferred_day", "2026-07-23", "review_decision", "corrected"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "book_slot",
                "spoken_response", "I updated the review to Thursday."
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "use_business_tool")
                .containsEntry("nextTool", "check_availability")
                .containsEntry("nextToolAuthorized", true)
                .containsEntry("nextToolArguments", Map.of(
                        "date", "2026-07-23", "time_preference", "15:00"
                ))
                .doesNotContainKey("spokenResponse");
        verify(sessions).updatePendingBooking("review-date-correction-call", null);
    }

    @Test
    void unclearWorkflowReplyPreservesStateAndCannotReuseAProposedTime() {
        var sessions = mock(CallSessionStore.class);
        var call = call("unclear-slot-call");
        when(sessions.conversationState("unclear-slot-call")).thenReturn(Optional.of(
                new ConversationState(
                        Map.of(
                                "service_type", "Men hairstyle",
                                "preferred_day", "2026-07-22",
                                "preferred_time", "16:00",
                                "review_decision", "approved"
                        ),
                        ConversationState.SUBJECT_SELF,
                        ConversationState.INTENT_ACTIVE,
                        5
                )
        ));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("preferred_time", "15:00"),
                "additional_details", Map.of(),
                "clear_fields", List.of("service_type"),
                "booking_subject", "other",
                "booking_intent", "active",
                "turn_understanding", "unclear",
                "next_action", "use_business_tool",
                "business_tool", "check_availability",
                "spoken_response", "Sorry, I didn't catch which time you chose. Could you say it again?"
        )));

        assertThat(captureState(sessions, "unclear-slot-call"))
                .satisfies(state -> {
                    assertThat(state.bookingSubject()).isEqualTo(ConversationState.SUBJECT_SELF);
                    assertThat(state.bookingIntent()).isEqualTo(ConversationState.INTENT_ACTIVE);
                    assertThat(state.values())
                            .containsEntry("service_type", "Men hairstyle")
                            .containsEntry("preferred_day", "2026-07-22")
                            .containsEntry("preferred_time", "16:00")
                            .doesNotContainKey("review_decision");
                });
        assertThat(result.result())
                .containsEntry("status", "conversation_turn_unclear")
                .containsEntry("nextAction", "reply")
                .containsEntry(
                        "spokenResponse",
                        "Sorry, I didn't catch which time you chose. Could you say it again?"
                )
                .doesNotContainKeys("nextTool", "nextToolAuthorized", "nextToolArguments");
    }

    @Test
    void informationOnlyDateMentionDoesNotForceABookingWorkflow() {
        var sessions = mock(CallSessionStore.class);
        var call = call("information-date-call");
        when(sessions.conversationState("information-date-call"))
                .thenReturn(Optional.of(ConversationState.empty()));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("preferred_day", "Thursday"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "information_only",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "What would you like to know about Thursday?"
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "reply")
                .containsEntry("spokenResponse", "What would you like to know about Thursday?")
                .doesNotContainKeys("nextTool", "nextToolAuthorized");
    }

    @Test
    void pausedBookingIntentCannotAuthorizeTheBookingSideEffect() {
        var sessions = mock(CallSessionStore.class);
        var call = call("paused-business-call");
        when(sessions.conversationState("paused-business-call"))
                .thenReturn(Optional.of(ConversationState.empty()));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of(),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "self",
                "booking_intent", "paused",
                "next_action", "use_business_tool",
                "business_tool", "book_slot",
                "spoken_response", ""
        )));

        assertThat(result.result())
                .containsEntry("bookingAllowed", false)
                .containsEntry("nextAction", "use_business_tool")
                .doesNotContainKeys("nextTool", "nextToolAuthorized");
    }

    @Test
    void collectingTheLastRequiredFieldUsesTheVerifiedSlotWithoutAnotherModelTurn() {
        var sessions = mock(CallSessionStore.class);
        var call = call("phone-completes-booking-call");
        when(call.getAgent().getBookingRequiredFields()).thenReturn(List.of(
                "caller_name", "caller_phone", "service_type", "appointment_at"
        ));
        when(sessions.conversationState("phone-completes-booking-call")).thenReturn(Optional.of(
                new ConversationState(
                        Map.of(
                                "caller_name", "Zachary",
                                "appointment_name", "Zachary",
                                "service_type", "Men hairstyle",
                                "preferred_day", "2026-07-22",
                                "preferred_time", "16:00"
                        ),
                        ConversationState.SUBJECT_SELF,
                        ConversationState.INTENT_ACTIVE,
                        5
                )
        ));
        when(sessions.pendingBooking("phone-completes-booking-call")).thenReturn(Optional.of(
                new BookingDraft(
                        "Zachary", "Men hairstyle", "2026-07-22",
                        "2026-07-22T16:00:00Z", "", true, "", 60
                )
        ));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("caller_phone", "0105752441"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "Thanks."
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "use_business_tool")
                .containsEntry("nextTool", "book_slot")
                .containsEntry("nextToolAuthorized", true)
                .doesNotContainKey("spokenResponse");
        @SuppressWarnings("unchecked")
        var arguments = (Map<String, Object>) result.result().get("nextToolArguments");
        assertThat(arguments)
                .containsEntry("appointment_name", "Zachary")
                .containsEntry("caller_phone", "0105752441")
                .containsEntry("service_type", "Men hairstyle")
                .containsEntry("appointment_at", "2026-07-22T16:00Z")
                .containsEntry("duration_minutes", 60)
                .doesNotContainKey("review_token");
    }

    @Test
    void cancellationUsesTheStoredBookingNumberWithoutAnotherModelTurn() {
        var sessions = mock(CallSessionStore.class);
        var call = call("cancel-booking-call");
        when(sessions.conversationState("cancel-booking-call")).thenReturn(Optional.of(
                new ConversationState(
                        Map.of("booking_number", "SAT-AB12CD34"),
                        ConversationState.SUBJECT_UNKNOWN,
                        ConversationState.INTENT_ACTIVE,
                        3
                )
        ));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of(),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "use_business_tool",
                "business_tool", "cancel_booking",
                "spoken_response", ""
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "use_business_tool")
                .containsEntry("nextTool", "cancel_booking")
                .containsEntry("nextToolAuthorized", true)
                .containsEntry("nextToolArguments", Map.of(
                        "booking_number", "SAT-AB12CD34",
                        "question_handling", "ready_for_action",
                        "confirmation_state", "confirmed"
                ))
                .doesNotContainKey("spokenResponse");
    }

    @Test
    void rescheduleUsesTheBookingNumberAndVerifiedReplacementSlotWithoutAnotherModelTurn() {
        var sessions = mock(CallSessionStore.class);
        var call = call("reschedule-booking-call");
        when(sessions.conversationState("reschedule-booking-call")).thenReturn(Optional.of(
                new ConversationState(
                        Map.of(
                                "booking_number", "SAT-AB12CD34",
                                "preferred_day", "2026-07-23",
                                "preferred_time", "14:00"
                        ),
                        ConversationState.SUBJECT_UNKNOWN,
                        ConversationState.INTENT_ACTIVE,
                        6
                )
        ));
        when(sessions.pendingBooking("reschedule-booking-call")).thenReturn(Optional.of(
                new BookingDraft("", "", "2026-07-23", "2026-07-23T14:00:00Z", "", true, "", 45)
        ));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of(),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "use_business_tool",
                "business_tool", "reschedule_booking",
                "spoken_response", ""
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "use_business_tool")
                .containsEntry("nextTool", "reschedule_booking")
                .containsEntry("nextToolAuthorized", true)
                .containsEntry("nextToolArguments", Map.of(
                        "booking_number", "SAT-AB12CD34",
                        "appointment_at", "2026-07-23T14:00Z",
                        "duration_minutes", 45,
                        "question_handling", "ready_for_action",
                        "confirmation_state", "confirmed"
                ));
    }

    @Test
    void multilingualReviewApprovalCannotBeTurnedIntoAnotherConfirmationQuestion() {
        var sessions = mock(CallSessionStore.class);
        var call = call("approved-review-call");
        when(sessions.conversationState("approved-review-call")).thenReturn(Optional.of(new ConversationState(
                Map.of(
                        "caller_name", "Zachary",
                        "appointment_name", "Zachary",
                        "caller_phone", "0105753221",
                        "service_type", "Men hairstyle",
                        "preferred_day", "Thursday 23 July",
                        "preferred_time", "15:00"
                ),
                ConversationState.SUBJECT_SELF,
                ConversationState.INTENT_ACTIVE,
                8
        )));
        when(sessions.pendingBooking("approved-review-call")).thenReturn(Optional.of(new BookingDraft(
                "Zachary", "Men hairstyle", "", "2026-07-23T15:00:00Z", "0105753221", true,
                "signed-review-token"
        )));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("review_decision", "approved"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "Oui, tout est correct ?"
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "use_business_tool")
                .containsEntry("nextTool", "book_slot")
                .containsEntry("nextToolAuthorized", true)
                .doesNotContainKey("progressResponse")
                .doesNotContainKey("spokenResponse");
        @SuppressWarnings("unchecked")
        var arguments = (Map<String, Object>) result.result().get("nextToolArguments");
        assertThat(arguments)
                .containsEntry("review_token", "signed-review-token")
                .containsEntry("appointment_name", "Zachary")
                .containsEntry("caller_phone", "0105753221")
                .containsEntry("appointment_at", "2026-07-23T15:00Z");
    }

    @Test
    void correctedReviewForcesOneFocusedBookingReviewInsteadOfDefendingTheOldValue() {
        var sessions = mock(CallSessionStore.class);
        var call = call("corrected-review-call");
        when(sessions.conversationState("corrected-review-call")).thenReturn(Optional.of(new ConversationState(
                Map.of(
                        "caller_name", "Akari",
                        "appointment_name", "Akari",
                        "caller_phone", "0105753221",
                        "service_type", "Men hairstyle",
                        "preferred_day", "2026-07-23",
                        "preferred_time", "15:00"
                ),
                ConversationState.SUBJECT_SELF,
                ConversationState.INTENT_ACTIVE,
                3
        )));
        when(sessions.pendingBooking("corrected-review-call")).thenReturn(Optional.of(new BookingDraft(
                "Akari", "Men hairstyle", "", "2026-07-23T15:00:00Z", "0105753221", true,
                "preceding-review-token"
        )));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("caller_name", "Zachary", "review_decision", "corrected"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "I heard Akari. Is that right?"
        )));

        assertThat(captureState(sessions, "corrected-review-call").values())
                .containsEntry("caller_name", "Zachary")
                .containsEntry("appointment_name", "Zachary")
                .doesNotContainValue("Akari");
        assertThat(result.result())
                .containsEntry("nextAction", "use_business_tool")
                .containsEntry("nextTool", "book_slot")
                .containsEntry("nextToolAuthorized", true)
                .doesNotContainKey("spokenResponse");
        @SuppressWarnings("unchecked")
        var arguments = (Map<String, Object>) result.result().get("nextToolArguments");
        assertThat(arguments)
                .containsEntry("review_token", "preceding-review-token")
                .containsEntry("appointment_name", "Zachary")
                .containsEntry("caller_phone", "0105753221");
    }

    @Test
    void answersAQuestionAttachedToReviewApprovalBeforeAuthorizingTheSave() {
        var sessions = mock(CallSessionStore.class);
        var call = call("approval-question-call");
        when(sessions.conversationState("approval-question-call")).thenReturn(Optional.of(
                new ConversationState(
                        Map.of(
                                "caller_name", "Zachary",
                                "appointment_name", "Sandra",
                                "caller_phone", "0105752443",
                                "service_type", "Nails",
                                "preferred_day", "2026-07-23",
                                "preferred_time", "10:00"
                        ),
                        ConversationState.SUBJECT_OTHER,
                        ConversationState.INTENT_ACTIVE,
                        8
                )
        ));
        when(sessions.pendingBooking("approval-question-call")).thenReturn(Optional.of(new BookingDraft(
                "Sandra", "Nails", "", "2026-07-23T10:00:00+03:00", "0105752443", true,
                "signed-review-token"
        )));
        var tool = new ConversationStateTool(sessions);

        var result = tool.execute(call, toolCall(Map.of(
                "updates", Map.of("review_decision", "approved"),
                "additional_details", Map.of(),
                "clear_fields", List.of(),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "turn_understanding", "clear",
                "caller_question", "answered_in_spoken_response",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "The nails service costs 4 dollars. Would you still like me to save the appointment?"
        )));

        assertThat(result.result())
                .containsEntry("nextAction", "reply")
                .containsEntry(
                        "spokenResponse",
                        "The nails service costs 4 dollars. Would you still like me to save the appointment?"
                )
                .doesNotContainKeys("nextTool", "nextToolAuthorized", "nextToolArguments");
        assertThat(captureState(sessions, "approval-question-call").values())
                .doesNotContainKey("review_decision")
                .containsEntry("service_type", "Nails");
    }

    @Test
    void configuredVerticalFieldsCanBeRetractedWithoutLanguageSpecificRules() {
        var sessions = mock(CallSessionStore.class);
        var call = call("vertical-field-call");
        when(call.getAgent().getBookingRequiredFields()).thenReturn(List.of("patient_date_of_birth"));
        when(sessions.conversationState("vertical-field-call")).thenReturn(Optional.of(new ConversationState(
                Map.of("patient_date_of_birth", "1988-04-12"),
                ConversationState.SUBJECT_SELF,
                ConversationState.INTENT_ACTIVE,
                3
        )));
        var tool = new ConversationStateTool(sessions);

        tool.execute(call, toolCall(Map.of(
                "updates", Map.of(),
                "additional_details", Map.of(),
                "clear_fields", List.of("patient_date_of_birth"),
                "booking_subject", "unchanged",
                "booking_intent", "unchanged",
                "next_action", "reply",
                "business_tool", "",
                "spoken_response", "I removed that date. What is the correct date of birth?"
        )));

        assertThat(captureState(sessions, "vertical-field-call").values())
                .doesNotContainKey("patient_date_of_birth");
    }

    @Test
    void schemaAsksForMeaningRatherThanLanguageSpecificKeywords() {
        var definition = ConversationStateTool.definition();

        assertThat(definition.description())
                .contains("semantically in any language or phrasing")
                .contains("Do not map by keywords")
                .contains("Corrections replace");
        assertThat(definition.inputSchema().toString())
                .contains(
                        "turn_understanding", "gibberish", "booking_number", "yyyy-MM-dd", "HH:mm",
                        "caller_question", "answered_in_spoken_response", "requires_business_tool"
                )
                .doesNotContain("my name is Zachary", "don't book", "call back later");
    }

    private Call call(String sid) {
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getTwilioCallSid()).thenReturn(sid);
        when(call.getAgent()).thenReturn(agent);
        when(agent.getTimezone()).thenReturn("UTC");
        when(agent.getBookingRequiredFields()).thenReturn(List.of());
        return call;
    }

    private LlmToolCall toolCall(Map<String, Object> arguments) {
        return new LlmToolCall("semantic-tool-call", ConversationStateTool.NAME, arguments);
    }

    private ConversationState captureState(CallSessionStore sessions, String sid) {
        var captor = ArgumentCaptor.forClass(ConversationState.class);
        verify(sessions).updateConversationState(eq(sid), captor.capture());
        return captor.getValue();
    }
}
