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
                "updates", Map.of("preferred_day", "next Thursday", "preferred_time", "13:00"),
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
                .doesNotContainKey("spokenResponse");
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
                .doesNotContain("my name is Zachary", "don't book", "call back later");
    }

    private Call call(String sid) {
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getTwilioCallSid()).thenReturn(sid);
        when(call.getAgent()).thenReturn(agent);
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
