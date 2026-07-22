package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class OpenAiTelephonyRealtimeConversationProviderTest {
    @Test
    void suppressesStandaloneAnswerMarkerAndRetriesWithoutSpeakingIt() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\",\"item\":{\"type\":\"message\",\"id\":\"answer-item\"}}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"item_id\":\"answer-item\",\"text\":\"ANSWER\"}",
                true);
        socketListener.onText(webSocket, "{\"type\":\"response.done\"}", true);

        assertThat(events).isEmpty();
        verify(session).requestToolResultResponse(0L);
        var deleted = ArgumentCaptor.forClass(String.class);
        verify(webSocket).sendText(deleted.capture(), eq(true));
        assertThat(deleted.getValue()).contains("conversation.item.delete", "answer-item");
    }

    @Test
    void speaksOnlyTheFinalAnswerPhaseFromAMultiPhaseResponse() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\",\"item\":{\"type\":\"message\",\"id\":\"phase-item\"}}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"item_id\":\"phase-item\",\"text\":\"ANSWER\"}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\",\"response\":{\"id\":\"phase-response\",\"output\":["
                        + "{\"type\":\"message\",\"phase\":\"commentary\",\"content\":[{\"type\":\"output_text\",\"text\":\"ANSWER\"}]},"
                        + "{\"type\":\"message\",\"phase\":\"final_answer\",\"content\":[{\"type\":\"output_text\",\"text\":\"I'm here to help. What would you like to know?\"}]}]}}",
                true);

        assertThat(events).containsExactly(
                "agent:I'm here to help. What would you like to know?:false"
        );
        verify(session, never()).requestToolResultResponse();
    }

    @Test
    void keepsACommentaryOnlyPhaseSilentWithoutTreatingItAsFinalSpeech() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.done\",\"response\":{\"output\":[{\"type\":\"message\","
                        + "\"phase\":\"commentary\",\"content\":[{\"type\":\"output_text\",\"text\":\"Let me check.\"}]}]}}",
                true);

        assertThat(events).isEmpty();
        verify(session, never()).requestToolResultResponse();
    }

    @Test
    void suppressesSplitProtocolMarkersAndRetriesWithoutTools() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.added\",\"item\":{\"type\":\"message\",\"id\":\"leaked-item\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"delta\":\"ana\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"delta\":\"lysis to=functions.get_business_hours code\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"text\":\"analysis to=functions.get_business_hours code\"}", true);

        assertThat(events).isEmpty();
        socketListener.onText(webSocket, "{\"type\":\"response.done\"}", true);

        assertThat(events).isEmpty();
        verify(session).requestToolResultResponse(0L);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(webSocket).sendText(payload.capture(), eq(true));
        assertThat(payload.getValue())
                .contains("\"type\":\"conversation.item.delete\"")
                .contains("\"item_id\":\"leaked-item\"");
    }

    @Test
    void ignoresDuplicateFinalTranscriptionEvents() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var payload = "{\"type\":\"conversation.item.input_audio_transcription.completed\","
                + "\"item_id\":\"caller-1\",\"transcript\":\"When are you open?\"}";

        socketListener.onText(mock(WebSocket.class), payload, true);
        socketListener.onText(mock(WebSocket.class), payload, true);

        assertThat(events).containsExactly("speech", "caller:When are you open?");
        verify(session, times(1)).requestResponse();
        verify(session, never()).requestResponseWithRequiredTool(anyString());
    }

    @Test
    void requestsAResponseOnlyAfterCallerTranscriptArrives() {
        var events = new ArrayList<String>();
        var listener = new RecordingListener(events);
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(),
                mock(OpenAiRealtimeService.class),
                mock(Call.class),
                listener,
                Map.of()
        );
        var webSocket = mock(WebSocket.class);
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L, 1L);
        socketListener.attach(session);

        socketListener.onText(webSocket, "{\"type\":\"input_audio_buffer.speech_started\"}", true);
        assertThat(events).isEmpty();
        socketListener.onText(webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.delta\",\"delta\":\"Please\"}",
                true);
        assertThat(events).containsExactly("speech");
        socketListener.onText(webSocket, "{\"type\":\"input_audio_buffer.speech_stopped\"}", true);
        verify(session, never()).requestResponse();

        socketListener.onText(webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"transcript\":\"Please book Monday\"}",
                true);

        assertThat(events).containsExactly("speech", "caller:Please book Monday");
        verify(session).requestResponse();
        verify(session, never()).requestResponseWithRequiredTool(anyString());

        socketListener.onText(webSocket, "{\"type\":\"response.created\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\",\"item\":{\"type\":\"message\",\"id\":\"message-1\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"item_id\":\"message-1\",\"delta\":\"I can help.\"}", true);
        assertThat(events).containsExactly("speech", "caller:Please book Monday");
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"item_id\":\"message-1\",\"text\":\"I can help.\"}", true);
        socketListener.onText(webSocket, "{\"type\":\"response.done\"}", true);

        assertThat(events).containsExactly(
                "speech", "caller:Please book Monday", "agent:I can help.:false"
        );
    }

    @Test
    void rawCallerVadDoesNotStopPlaybackBeforeTranscriptRecognition() {
        var listener = mock(TelephonyRealtimeConversationProvider.Listener.class);
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class), listener, Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(
                mock(WebSocket.class),
                "{\"type\":\"input_audio_buffer.speech_started\",\"item_id\":\"caller-vad\"}",
                true
        );

        verify(listener, never()).onCallerSpeechStarted();
        verify(session, never()).cancelResponse();
        verify(session, never()).requestResponse();
    }

    @Test
    void stripsAssistantRoleLabelOnlyAfterTheCompleteMessageIsValidated() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\",\"item\":{\"type\":\"message\",\"id\":\"message-1\"}}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"item_id\":\"message-1\",\"delta\":\"assis\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"item_id\":\"message-1\","
                        + "\"delta\":\"tant: Hi Walker, a men's haircut costs 5 dollars.\"}", true);

        assertThat(events).isEmpty();

        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"item_id\":\"message-1\","
                        + "\"text\":\"assistant: Hi Walker, a men's haircut costs 5 dollars.\"}", true);
        socketListener.onText(webSocket, "{\"type\":\"response.done\"}", true);

        assertThat(events).containsExactly(
                "agent:Hi Walker, a men's haircut costs 5 dollars.:false"
        );
        verify(session, never()).requestToolResultResponse();
    }

    @Test
    void neverSpeaksTextEventsAttachedToANonMessageItem() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\","
                        + "\"item\":{\"type\":\"function_call\",\"id\":\"call-item\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"item_id\":\"call-item\",\"delta\":\"Do not speak\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"item_id\":\"call-item\",\"text\":\"Do not speak\"}", true);

        assertThat(events).isEmpty();
        verify(session, never()).requestToolResultResponse();
    }

    @Test
    void ignoresAnEmptyVadTurnWithoutStartingAnAgentResponse() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var webSocket = mock(WebSocket.class);
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(webSocket, "{\"type\":\"input_audio_buffer.speech_started\"}", true);
        socketListener.onText(webSocket, "{\"type\":\"input_audio_buffer.speech_stopped\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.completed\",\"transcript\":\" [silence] \"}",
                true);

        assertThat(events).isEmpty();
        verify(session, never()).requestResponse();
    }

    @Test
    void emptyVadNoiseDoesNotDiscardAValidAgentResponse() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket, "{\"type\":\"response.created\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"input_audio_buffer.speech_started\",\"item_id\":\"noise\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"input_audio_buffer.speech_stopped\",\"item_id\":\"noise\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\",\"item\":{\"type\":\"message\",\"id\":\"message-1\"}}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"item_id\":\"message-1\",\"text\":\"How can I help?\"}",
                true);
        socketListener.onText(webSocket, "{\"type\":\"response.done\"}", true);
        assertThat(events).isEmpty();

        socketListener.onText(webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.completed\","
                        + "\"item_id\":\"noise\",\"transcript\":\"[silence]\"}", true);

        assertThat(events).containsExactly("agent:How can I help?:false");
    }

    @Test
    void acceptedTranscriptIsMirroredBeforeANaturalResponseWithoutSynchronousPreparation() {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call, new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"conversation.item.input_audio_transcription.completed\","
                        + "\"item_id\":\"caller-hours\",\"transcript\":\"When are you available?\"}", true);

        assertThat(events).containsExactly("speech", "caller:When are you available?");
        verify(session).mirrorCallerTranscript("When are you available?");
        verify(session).requestResponse();
    }

    @Test
    void multilingualCorrectionUsesTheSameTranscriptMirrorAndNaturalToolChoicePath() {
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call,
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"conversation.item.input_audio_transcription.completed\","
                        + "\"item_id\":\"semantic-caller\",\"transcript\":\"El nombre anterior era incorrecto.\"}",
                true);

        verify(session).mirrorCallerTranscript("El nombre anterior era incorrecto.");
        verify(session).requestResponse();
        verify(session, never()).requestResponseWithRequiredTool(com.sauti.tool.ConversationStateTool.NAME);
    }

    @Test
    void overlappingVadTurnsKeepAgentSpeechDeferredUntilEveryTranscriptionTerminates() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"input_audio_buffer.speech_started\",\"item_id\":\"caller-a\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"input_audio_buffer.speech_stopped\",\"item_id\":\"caller-a\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"input_audio_buffer.speech_started\",\"item_id\":\"caller-b\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"input_audio_buffer.speech_stopped\",\"item_id\":\"caller-b\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\",\"item\":{\"type\":\"message\",\"id\":\"message-1\"}}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"item_id\":\"message-1\",\"text\":\"Old reply.\"}",
                true);
        socketListener.onText(webSocket, "{\"type\":\"response.done\"}", true);

        socketListener.onText(webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.completed\","
                        + "\"item_id\":\"caller-a\",\"transcript\":\"[silence]\"}", true);
        assertThat(events).isEmpty();

        socketListener.onText(webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.failed\","
                        + "\"item_id\":\"caller-b\"}", true);
        assertThat(events).containsExactly("agent:Old reply.:false");
    }

    @Test
    void unpreparedTurnStillUsesTheNaturalSessionResponsePath() {
        var tools = List.of(Map.of("type", "function", "name", "check_availability"));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(new ArrayList<>()), Map.of("tools", tools)
        );
        var webSocket = mock(WebSocket.class);
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.completed\","
                        + "\"transcript\":\"Wednesday at 3 P.M.\"}",
                true);

        verify(session).requestResponse();
        verify(session, never()).requestResponseWithRequiredTool("check_availability");
    }

    @Test
    void rejectsTextualToolArgumentsInsteadOfExecutingThem() {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var tools = List.of(Map.of("type", "function", "name", "check_availability"));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call, new RecordingListener(events), Map.of("tools", tools)
        );
        var webSocket = mock(WebSocket.class);
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        socketListener.attach(session);

        socketListener.onText(webSocket,
                "{\"type\":\"conversation.item.input_audio_transcription.completed\"," +
                        "\"transcript\":\"Demain a midi\"}", true);
        socketListener.onText(webSocket, "{\"type\":\"response.created\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.added\",\"item\":{\"type\":\"message\",\"id\":\"msg-1\"}}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"delta\":\"{\\\"date\\\":\\\"2030-01-15\\\",\\\"time_preference\\\":\\\"12:00\\\"}\"}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"text\":\"{\\\"date\\\":\\\"2030-01-15\\\",\\\"time_preference\\\":\\\"12:00\\\"}\"}",
                true);
        socketListener.onText(webSocket, "{\"type\":\"response.done\"}", true);

        assertThat(events).containsExactly("speech", "caller:Demain a midi");
        verify(realtimeService, never()).executeTool(any(), anyString(), anyString(), anyString());
        verify(session).requestToolResultResponse(0L);
        var deleted = ArgumentCaptor.forClass(String.class);
        verify(webSocket).sendText(deleted.capture(), eq(true));
        assertThat(deleted.getValue()).contains("conversation.item.delete", "msg-1");
    }

    @Test
    void sendsTheDeterministicAvailabilityDecisionDirectlyToExternalSpeech() {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var spoken = "That time is available. Would you like me to continue with the booking?";
        when(realtimeService.executeTool(eq(call), eq("availability-1"), eq("check_availability"), anyString()))
                .thenReturn(new com.sauti.llm.LlmToolResult(
                        "availability-1", "check_availability", true,
                        Map.of("status", "requested_time_available", "spokenResponse", spoken), ""
                ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call, new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.function_call_arguments.done\","
                        + "\"call_id\":\"availability-1\",\"name\":\"check_availability\","
                        + "\"arguments\":\"{\\\"date\\\":\\\"2026-07-20\\\"}\"}", true);

        verify(realtimeService, timeout(1_000)).executeTool(
                eq(call), eq("availability-1"), eq("check_availability"), anyString()
        );
        verify(session, never()).requestToolResultResponse();
        verify(session, timeout(1_000)).seedAssistantText(spoken);
        verify(session, never()).requestExactResponse(anyString(), anyLong());
        assertThat(events).containsExactly("agent:" + spoken + ":false");
    }

    @Test
    void authorizesPhoneClosureOnlyFromTheSuccessfulEndCallToolResult() {
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        when(realtimeService.executeTool(eq(call), eq("end-1"), eq("end_call"), anyString()))
                .thenReturn(new com.sauti.llm.LlmToolResult(
                        "end-1", "end_call", true,
                        Map.of("ended", true, "outcome", "completed"), ""
                ));
        var listener = mock(TelephonyRealtimeConversationProvider.Listener.class);
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call, listener, Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.function_call_arguments.done\","
                        + "\"call_id\":\"end-1\",\"name\":\"end_call\","
                        + "\"arguments\":\"{\\\"outcome\\\":\\\"completed\\\"}\"}", true);

        verify(listener, timeout(1_000)).onCallEndAuthorized("completed");
        verify(session, timeout(1_000)).requestToolResultResponse(0L);
    }

    @Test
    void chainsAvailableCompleteIntakeDirectlyToTheBookingToolWithoutSpeakingAPreamble() {
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        when(realtimeService.executeTool(eq(call), eq("availability-review"), eq("check_availability"), anyString()))
                .thenReturn(new com.sauti.llm.LlmToolResult(
                        "availability-review", "check_availability", true,
                        Map.of(
                                "status", "requested_time_available",
                                "nextTool", "book_slot",
                                "nextToolAuthorized", true,
                                "nextToolArguments", Map.of(
                                        "appointment_name", "Zachary",
                                        "caller_phone", "0105752441",
                                        "service_type", "Men hairstyle",
                                        "appointment_at", "2026-07-24T15:00:00Z"
                                )
                        ), ""
                ));
        when(realtimeService.executeTool(
                eq(call), eq("sauti-chain:availability-review:book_slot"), eq("book_slot"), anyString()
        )).thenReturn(new com.sauti.llm.LlmToolResult(
                "sauti-chain:availability-review:book_slot", "book_slot", true,
                Map.of("status", "booking_review_required", "spokenResponse", "Please confirm the details."), ""
        ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call,
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.function_call_arguments.done\","
                        + "\"call_id\":\"availability-review\",\"name\":\"check_availability\","
                        + "\"arguments\":\"{\\\"date\\\":\\\"2026-07-24\\\",\\\"time_preference\\\":\\\"15:00\\\"}\"}",
                true);

        verify(realtimeService, timeout(1_000)).executeTool(
                eq(call), eq("sauti-chain:availability-review:book_slot"), eq("book_slot"), anyString()
        );
        verify(session, never()).requestResponseWithRequiredTool("book_slot");
        verify(session, never()).requestToolResultResponse();
        verify(session, never()).requestExactResponse(anyString(), anyLong());
    }

    @Test
    void executesASemanticallySelectedSlotWithoutASecondModelGeneration() throws Exception {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var spoken = "Four in the afternoon is available. Would you like to continue?";
        when(realtimeService.executeTool(
                eq(call), eq("semantic-action"), eq(com.sauti.tool.ConversationStateTool.NAME), anyString()
        )).thenReturn(new com.sauti.llm.LlmToolResult(
                "semantic-action", com.sauti.tool.ConversationStateTool.NAME, true,
                Map.of(
                        "status", "conversation_state_updated",
                        "nextAction", "use_business_tool",
                        "nextTool", "check_availability",
                        "nextToolAuthorized", true,
                        "nextToolArguments", Map.of(
                                "date", "2026-07-22", "time_preference", "16:00"
                        )
                ), ""
        ));
        when(realtimeService.executeTool(
                eq(call), eq("sauti-chain:semantic-action:check_availability"),
                eq("check_availability"),
                anyString()
        )).thenReturn(new com.sauti.llm.LlmToolResult(
                "sauti-chain:semantic-action:check_availability", "check_availability", true,
                Map.of("status", "requested_time_available", "spokenResponse", spoken), ""
        ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call,
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.function_call_arguments.done\","
                        + "\"call_id\":\"semantic-action\",\"name\":\"update_conversation_state\","
                        + "\"arguments\":\"{\\\"next_action\\\":\\\"use_business_tool\\\"}\"}",
                true);

        var arguments = ArgumentCaptor.forClass(String.class);
        verify(realtimeService, timeout(1_000)).executeTool(
                eq(call), eq("sauti-chain:semantic-action:check_availability"),
                eq("check_availability"),
                arguments.capture()
        );
        assertThat(new ObjectMapper().readTree(arguments.getValue()))
                .isEqualTo(new ObjectMapper().readTree(
                        "{\"date\":\"2026-07-22\",\"time_preference\":\"16:00\"}"
                ));
        verify(session, never()).requestResponseWithRequiredTool("check_availability");
        verify(session, never()).requestResponse();
        verify(session, never()).requestToolResultResponse();
        verify(session, never()).requestExactResponse(anyString(), anyLong());
        verify(session, timeout(1_000)).seedAssistantText(spoken);
        assertThat(events).containsExactly("agent:" + spoken + ":false");
    }

    @Test
    void confirmsAnApprovedBookingBeforeOptionalAiGeneratedReferenceGuidance() {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var confirmation = "Your appointment is booked. Your booking number is SAT-APPROVED123.";
        var guidanceInstruction = "Tell the caller in their current language to keep the booking number and "
                + "provide it when calling back to change, reschedule, or cancel the booking.";
        var guidance = "Please keep that booking number and give it to us if you call back to change or cancel.";
        when(realtimeService.executeTool(
                eq(call), eq("semantic-approval"), eq(com.sauti.tool.ConversationStateTool.NAME), anyString()
        )).thenReturn(new com.sauti.llm.LlmToolResult(
                "semantic-approval", com.sauti.tool.ConversationStateTool.NAME, true,
                Map.of(
                        "nextAction", "use_business_tool",
                        "nextTool", "book_slot",
                        "nextToolAuthorized", true,
                        "nextToolArguments", Map.of("review_token", "signed-review-token")
                ), ""
        ));
        when(realtimeService.executeTool(
                eq(call), eq("sauti-chain:semantic-approval:book_slot"), eq("book_slot"),
                eq("{\"review_token\":\"signed-review-token\"}")
        )).thenReturn(new com.sauti.llm.LlmToolResult(
                "sauti-chain:semantic-approval:book_slot", "book_slot", true,
                Map.of(
                        "status", "booking_confirmed",
                        "spokenResponse", confirmation,
                        "callerGuidanceInstruction", guidanceInstruction
                ), ""
        ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call, new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        var webSocket = mock(WebSocket.class);
        socketListener.attach(session);

        socketListener.onText(webSocket,
                "{\"type\":\"response.function_call_arguments.done\"," +
                        "\"call_id\":\"semantic-approval\",\"name\":\"update_conversation_state\"," +
                        "\"arguments\":\"{\\\"updates\\\":{\\\"review_decision\\\":\\\"approved\\\"}}\"}",
                true);

        verify(realtimeService, timeout(1_000)).executeTool(
                eq(call), eq("sauti-chain:semantic-approval:book_slot"), eq("book_slot"),
                eq("{\"review_token\":\"signed-review-token\"}")
        );
        verify(session, never()).requestResponseWithRequiredTool("book_slot");
        verify(session, timeout(1_000)).seedAssistantText(confirmation);
        verify(session, timeout(1_000)).requestPostBookingGuidance(guidanceInstruction, 0L);
        assertThat(events).containsExactly("agent:" + confirmation + ":false");
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(webSocket, timeout(1_000).atLeastOnce()).sendText(payloads.capture(), eq(true));
        assertThat(payloads.getAllValues()).noneMatch(payload -> payload.contains("sauti-chain:semantic-approval"));

        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L);
        when(session.dispatchedPurpose()).thenReturn("post_booking_guidance");
        when(session.dispatchedProgressKey()).thenReturn("");
        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"guidance-response\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.added\","
                        + "\"item\":{\"type\":\"message\",\"id\":\"guidance-message\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"text\":\"" + guidance + "\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\","
                        + "\"response\":{\"id\":\"guidance-response\",\"status\":\"completed\"}}", true);

        assertThat(events).containsExactly(
                "agent:" + confirmation + ":false",
                "agent:" + guidance + ":false"
        );
    }

    @Test
    void keepsAConfirmedBookingSuccessfulWhenOptionalReferenceGuidanceFails() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L);
        when(session.dispatchedPurpose()).thenReturn("post_booking_guidance");
        when(session.dispatchedProgressKey()).thenReturn("");
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"guidance-response\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"error\",\"error\":{\"message\":\"guidance generation timed out\"}}", true);

        assertThat(events).isEmpty();
        verify(session).responseFinished();
    }

    @Test
    void tellsTheCallerWhenAnActualBookingSaveIsStillRunning() throws Exception {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var releaseSave = new CountDownLatch(1);
        var progress = "I’m sorry for the wait; I’m still saving that appointment for you.";
        var confirmation = "Your appointment is booked. Your booking number is SAT-SLOW123456.";
        when(realtimeService.executeTool(eq(call), eq("slow-booking"), eq("book_slot"), anyString()))
                .thenAnswer(ignored -> {
                    releaseSave.await(5, TimeUnit.SECONDS);
                    return new com.sauti.llm.LlmToolResult(
                            "slow-booking", "book_slot", true,
                            Map.of("status", "booking_confirmed", "spokenResponse", confirmation), ""
                    );
                });
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call, new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.function_call_arguments.done\"," +
                        "\"call_id\":\"slow-booking\",\"name\":\"book_slot\"," +
                        "\"arguments\":\"{\\\"review_token\\\":\\\"signed-review-token\\\"}\"}",
                true);

        var progressKey = ArgumentCaptor.forClass(String.class);
        verify(session, timeout(2_500)).requestBusinessActionProgress(
                eq("book_slot"), progressKey.capture(), eq(0L)
        );
        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L);
        when(session.dispatchedPurpose()).thenReturn("business_progress");
        when(session.dispatchedProgressKey()).thenReturn(progressKey.getValue());
        var webSocket = mock(WebSocket.class);
        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"progress-response\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.added\","
                        + "\"item\":{\"type\":\"message\",\"id\":\"progress-message\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"text\":\"" + progress + "\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\","
                        + "\"response\":{\"id\":\"progress-response\",\"status\":\"completed\"}}", true);

        assertThat(events).contains("agent:" + progress + ":false");

        releaseSave.countDown();
        verify(session, timeout(1_000)).seedAssistantText(confirmation);
        assertThat(events).containsSubsequence(
                "agent:" + progress + ":false",
                "agent:" + confirmation + ":false"
        );
    }

    @Test
    void suppressesAGeneratedDelayApologyWhenTheBusinessActionFinishesFirst() throws Exception {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var releaseSave = new CountDownLatch(1);
        var confirmation = "Your appointment is booked. Your booking number is SAT-FASTENOUGH.";
        when(realtimeService.executeTool(eq(call), eq("racing-booking"), eq("book_slot"), anyString()))
                .thenAnswer(ignored -> {
                    releaseSave.await(5, TimeUnit.SECONDS);
                    return new com.sauti.llm.LlmToolResult(
                            "racing-booking", "book_slot", true,
                            Map.of("status", "booking_confirmed", "spokenResponse", confirmation), ""
                    );
                });
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call, new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.function_call_arguments.done\","
                        + "\"call_id\":\"racing-booking\",\"name\":\"book_slot\","
                        + "\"arguments\":\"{\\\"review_token\\\":\\\"signed-review-token\\\"}\"}", true);

        var progressKey = ArgumentCaptor.forClass(String.class);
        verify(session, timeout(2_500)).requestBusinessActionProgress(
                eq("book_slot"), progressKey.capture(), eq(0L)
        );
        releaseSave.countDown();
        verify(session, timeout(1_000)).seedAssistantText(confirmation);

        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L);
        when(session.dispatchedPurpose()).thenReturn("business_progress");
        when(session.dispatchedProgressKey()).thenReturn(progressKey.getValue());
        var webSocket = mock(WebSocket.class);
        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"stale-progress\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.added\","
                        + "\"item\":{\"type\":\"message\",\"id\":\"stale-message\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\","
                        + "\"text\":\"Sorry for the wait; I’m still saving it.\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\","
                        + "\"response\":{\"id\":\"stale-progress\",\"status\":\"completed\"}}", true);

        assertThat(events).containsExactly("agent:" + confirmation + ":false");
        var payload = ArgumentCaptor.forClass(String.class);
        verify(webSocket, timeout(1_000).atLeastOnce()).sendText(payload.capture(), eq(true));
        assertThat(payload.getAllValues()).anyMatch(value -> value.contains("conversation.item.delete")
                && value.contains("stale-message"));
    }

    @Test
    void progressInstructionsDelegateLanguageAndWordingToTheModel() {
        assertThat(OpenAiTelephonyRealtimeConversationProvider.supportsBusinessActionProgress("book_slot")).isTrue();
        assertThat(OpenAiTelephonyRealtimeConversationProvider.supportsBusinessActionProgress("check_availability")).isTrue();
        assertThat(OpenAiTelephonyRealtimeConversationProvider.supportsBusinessActionProgress("reschedule_booking")).isTrue();
        assertThat(OpenAiTelephonyRealtimeConversationProvider.supportsBusinessActionProgress("cancel_booking")).isTrue();
        assertThat(OpenAiTelephonyRealtimeConversationProvider.businessActionProgressInstruction("book_slot"))
                .contains("caller's current language", "apology", "still saving the appointment")
                .doesNotContain("I’m saving the appointment");
        assertThat(OpenAiTelephonyRealtimeConversationProvider.businessActionProgressInstruction("check_availability"))
                .contains("still checking the live schedule");
        assertThat(OpenAiTelephonyRealtimeConversationProvider.businessActionProgressInstruction("reschedule_booking"))
                .contains("still rescheduling the appointment");
        assertThat(OpenAiTelephonyRealtimeConversationProvider.businessActionProgressInstruction("cancel_booking"))
                .contains("still cancelling the appointment");
        assertThat(OpenAiTelephonyRealtimeConversationProvider.slowResponseProgressInstruction())
                .contains("caller's current language", "still working on their request")
                .doesNotContain("Could you repeat");
    }

    @Test
    void deliversConcurrentOutOfBandProgressWithoutReplacingTheMainPhoneResponse() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L);
        when(session.dispatchedPurpose()).thenReturn("conversation");
        when(session.dispatchedProgressKey()).thenReturn("");
        when(session.isDispatchedRequest("main-request")).thenReturn(true);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"main-response\"," +
                        "\"metadata\":{\"sauti_request_id\":\"main-request\"}}}", true);
        socketListener.registerSlowResponseProgress("progress-request", 0L);
        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"progress-response\"," +
                        "\"metadata\":{\"sauti_request_id\":\"progress-request\"," +
                        "\"purpose\":\"slow_response_progress\",\"main_request_id\":\"main-request\"}}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\",\"response\":{\"id\":\"progress-response\"," +
                        "\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[" +
                        "{\"type\":\"output_text\",\"text\":\"Sorry for the wait. I am still working on that.\"}]}]}}",
                true);

        assertThat(events).containsExactly(
                "agent:Sorry for the wait. I am still working on that.:false"
        );
        verify(session, times(1)).consumeExpectedResponse();
        verify(session, never()).responseFinished();
    }

    @Test
    void executesAFunctionCallRecoveredFromResponseDoneWhenTheDeltaEventIsMissing() {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var spoken = "Which service would you like to book?";
        when(realtimeService.executeTool(
                eq(call), eq("semantic-from-done"), eq(com.sauti.tool.ConversationStateTool.NAME), anyString()
        )).thenReturn(new com.sauti.llm.LlmToolResult(
                "semantic-from-done", com.sauti.tool.ConversationStateTool.NAME, true,
                Map.of("spokenResponse", spoken, "nextAction", "reply"), ""
        ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call,
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.done\",\"response\":{" +
                        "\"id\":\"semantic-response\",\"status\":\"completed\",\"output\":[{" +
                        "\"type\":\"function_call\",\"call_id\":\"semantic-from-done\"," +
                        "\"name\":\"update_conversation_state\"," +
                        "\"arguments\":\"{\\\"booking_intent\\\":\\\"active\\\"}\"}]}}",
                true);

        verify(realtimeService, timeout(1_000).times(1)).executeTool(
                eq(call), eq("semantic-from-done"), eq(com.sauti.tool.ConversationStateTool.NAME), anyString()
        );
        verify(session, timeout(1_000)).seedAssistantText(spoken);
        assertThat(events).containsExactly("agent:" + spoken + ":false");
    }

    @Test
    void executesEachNativeToolCallIdOnlyOnce() {
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        when(realtimeService.executeTool(eq(call), eq("tool-1"), eq("check_availability"), anyString()))
                .thenReturn(new com.sauti.llm.LlmToolResult(
                        "tool-1", "check_availability", true, Map.of(), ""
                ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call,
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var event = "{\"type\":\"response.function_call_arguments.done\","
                + "\"call_id\":\"tool-1\",\"name\":\"check_availability\",\"arguments\":\"{}\"}";

        socketListener.onText(mock(WebSocket.class), event, true);
        socketListener.onText(mock(WebSocket.class), event, true);

        verify(realtimeService, timeout(1_000).times(1))
                .executeTool(eq(call), eq("tool-1"), eq("check_availability"), anyString());
    }

    @Test
    void acceptsCompletedFunctionItemsFromBothCurrentRealtimeEventShapes() {
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        when(realtimeService.executeTool(eq(call), eq("tool-item-1"), eq("check_availability"), anyString()))
                .thenReturn(new com.sauti.llm.LlmToolResult(
                        "tool-item-1", "check_availability", true, Map.of(), ""
                ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call,
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var item = "{\"type\":\"function_call\",\"call_id\":\"tool-item-1\"," +
                "\"name\":\"check_availability\",\"arguments\":\"{}\"}";

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.function_call_arguments.done\",\"item\":" + item + "}", true);
        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.output_item.done\",\"item\":" + item + "}", true);

        verify(realtimeService, timeout(1_000).times(1))
                .executeTool(eq(call), eq("tool-item-1"), eq("check_availability"), anyString());
    }

    @Test
    void coalescesSemanticallyDuplicateToolCallsWithinOneCallerTurn() {
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        when(realtimeService.executeTool(eq(call), anyString(), eq("check_availability"), anyString()))
                .thenAnswer(invocation -> new com.sauti.llm.LlmToolResult(
                        invocation.getArgument(1), "check_availability", true,
                        Map.of("spokenResponse", "That time is available."), ""
                ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call,
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var first = "{\"type\":\"response.function_call_arguments.done\","
                + "\"call_id\":\"tool-a\",\"name\":\"check_availability\","
                + "\"arguments\":\"{\\\"date\\\":\\\"2026-07-22\\\",\\\"time\\\":\\\"15:00\\\"}\"}";
        var second = "{\"type\":\"response.function_call_arguments.done\","
                + "\"call_id\":\"tool-b\",\"name\":\"check_availability\","
                + "\"arguments\":\"{\\\"time\\\":\\\"15:00\\\",\\\"date\\\":\\\"2026-07-22\\\"}\"}";

        socketListener.onText(mock(WebSocket.class), first, true);
        socketListener.onText(mock(WebSocket.class), second, true);

        verify(realtimeService, timeout(1_000).times(1))
                .executeTool(eq(call), anyString(), eq("check_availability"), anyString());
        verify(session, timeout(1_000).times(1)).seedAssistantText("That time is available.");
        verify(session, never()).requestExactResponse(anyString(), anyLong());
    }

    @Test
    void suppressesMessagePreambleWhenTheSameResponseContainsANativeToolCall() {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        when(realtimeService.executeTool(eq(call), eq("tool-2"), eq("check_availability"), anyString()))
                .thenReturn(new com.sauti.llm.LlmToolResult(
                        "tool-2", "check_availability", true, Map.of(), ""
                ));
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call, new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket, "{\"type\":\"response.created\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.added\",\"item\":{\"type\":\"message\",\"id\":\"msg-2\"}}",
                true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"item_id\":\"msg-2\",\"delta\":\"Let me check.\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"item_id\":\"msg-2\",\"text\":\"Let me check.\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.added\","
                        + "\"item\":{\"type\":\"function_call\",\"id\":\"tool-item-2\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.function_call_arguments.done\","
                        + "\"call_id\":\"tool-2\",\"name\":\"check_availability\",\"arguments\":\"{}\"}", true);
        socketListener.onText(webSocket, "{\"type\":\"response.done\"}", true);

        assertThat(events).isEmpty();
        verify(realtimeService, timeout(1_000)).executeTool(
                eq(call), eq("tool-2"), eq("check_availability"), anyString()
        );
    }

    @Test
    void serializesResponseCreationUntilThePreviousResponseIsDone() throws Exception {
        var webSocket = mock(WebSocket.class);
        when(webSocket.sendText(anyString(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(webSocket));
        var executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> keepAlive = mock(ScheduledFuture.class);
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(ignored -> keepAlive);
        var session = new OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession(
                webSocket, new ObjectMapper(), executor
        );

        session.requestResponse();
        session.requestExactResponse("That time is available.");

        verify(webSocket, timeout(1_000).times(1)).sendText(anyString(), eq(true));
        session.responseFinished();
        verify(webSocket, timeout(1_000).times(2)).sendText(anyString(), eq(true));

        var payloads = ArgumentCaptor.forClass(String.class);
        verify(webSocket, times(2)).sendText(payloads.capture(), eq(true));
        assertThat(payloads.getAllValues())
                .allMatch(payload -> payload.contains("\"type\":\"response.create\""));
        assertThat(payloads.getAllValues().get(0))
                .as("normal replies must inherit the complete personalized session instructions")
                .doesNotContain("\"instructions\"");
        assertThat(payloads.getAllValues().get(1)).contains("That time is available.");
    }

    @Test
    void toolFollowUpsKeepSessionInstructionsInsteadOfReplacingPersonalizedFacts() throws Exception {
        var webSocket = mock(WebSocket.class);
        when(webSocket.sendText(anyString(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(webSocket));
        var executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> keepAlive = mock(ScheduledFuture.class);
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(ignored -> keepAlive);
        var session = new OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession(
                webSocket, new ObjectMapper(), executor
        );

        session.requestToolResultResponse();

        var payload = ArgumentCaptor.forClass(String.class);
        verify(webSocket, timeout(1_000)).sendText(payload.capture(), eq(true));
        assertThat(payload.getValue())
                .contains("\"tool_choice\":\"none\"")
                .doesNotContain("\"instructions\"");
    }

    @Test
    void retriesAResponseRejectedWhileTheProviderIsBusy() throws Exception {
        var webSocket = mock(WebSocket.class);
        when(webSocket.sendText(anyString(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(webSocket));
        var executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> keepAlive = mock(ScheduledFuture.class);
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(ignored -> keepAlive);
        var session = new OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession(
                webSocket, new ObjectMapper(), executor
        );

        session.requestExactResponse("Please confirm the booking details.");
        session.responseCreationRejectedAsBusy();

        verify(webSocket, timeout(1_000).times(1)).sendText(anyString(), eq(true));
        session.responseFinished();
        verify(webSocket, timeout(1_000).times(2)).sendText(anyString(), eq(true));

        var payloads = ArgumentCaptor.forClass(String.class);
        verify(webSocket, times(2)).sendText(payloads.capture(), eq(true));
        assertThat(payloads.getAllValues())
                .allMatch(payload -> payload.contains("Please confirm the booking details."));
    }

    @Test
    void cancelsAnUnsolicitedAgentResponse() {
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(false, true);
        when(session.dispatchedGeneration()).thenReturn(0L);
        socketListener.attach(session);

        var webSocket = mock(WebSocket.class);
        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"response-unsolicited\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\",\"response\":{\"id\":\"response-unsolicited\"}}", true);

        verify(session).cancelProviderResponse("response-unsolicited");
        verify(session, never()).responseFinished();

        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"response-expected\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\",\"response\":{\"id\":\"response-expected\"}}", true);

        verify(session).responseFinished();
    }

    @Test
    void defersCancellationUntilTheOutstandingResponseIsCreated() {
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.hasResponseOutstanding()).thenReturn(true);
        when(session.currentGeneration()).thenReturn(1L);
        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L);
        socketListener.attach(session);

        socketListener.markCurrentResponseCancelled();

        verify(session, never()).cancelProviderResponse(anyString());

        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.created\",\"response\":{\"id\":\"response-old\"}}", true);

        verify(session).cancelProviderResponse("response-old");
        socketListener.onText(mock(WebSocket.class),
                "{\"type\":\"response.cancelled\",\"response\":{\"id\":\"response-old\"}}", true);
    }

    @Test
    void discardsLateTextFromAnInterruptedResponseWithoutContaminatingTheNextTurn() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L, 1L);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"response-old\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\",\"response_id\":\"response-old\","
                        + "\"item\":{\"type\":\"message\",\"id\":\"old-message\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"response_id\":\"response-old\","
                        + "\"item_id\":\"old-message\",\"delta\":\"Old start.\"}", true);
        socketListener.markCurrentResponseCancelled();
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"response_id\":\"response-old\",\"delta\":\" Late duplicate.\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\",\"response\":{\"id\":\"response-old\"}}", true);

        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"response-new\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_item.created\",\"response_id\":\"response-new\","
                        + "\"item\":{\"type\":\"message\",\"id\":\"new-message\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.delta\",\"response_id\":\"response-new\","
                        + "\"item_id\":\"new-message\",\"delta\":\"Fresh answer.\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.output_text.done\",\"response_id\":\"response-new\","
                        + "\"item_id\":\"new-message\",\"text\":\"Fresh answer.\"}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\",\"response\":{\"id\":\"response-new\"}}", true);

        assertThat(events).containsExactly(
                "agent:Fresh answer.:false"
        );
    }

    @Test
    void cancellationTerminalUnblocksTheQueueWithoutLettingLateOldEventsFinishTheNewResponse() {
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        when(session.dispatchedGeneration()).thenReturn(0L, 1L);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"response-old\"}}", true);
        socketListener.markCurrentResponseCancelled();
        socketListener.onText(webSocket,
                "{\"type\":\"response.cancelled\",\"response\":{\"id\":\"response-old\"}}", true);

        verify(session, times(1)).responseFinished();

        socketListener.onText(webSocket,
                "{\"type\":\"response.created\",\"response\":{\"id\":\"response-new\"}}", true);
        socketListener.onText(webSocket,
                "{\"type\":\"response.done\",\"response\":{\"id\":\"response-old\"}}", true);

        verify(session, times(1)).responseFinished();

        socketListener.onText(webSocket,
                "{\"type\":\"response.done\",\"response\":{\"id\":\"response-new\"}}", true);
        verify(session, times(2)).responseFinished();
    }

    @Test
    void suppressesTheSameCompletedSpeechTwiceWithinOneCallerGeneration() {
        var events = new ArrayList<String>();
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), mock(OpenAiRealtimeService.class), mock(Call.class),
                new RecordingListener(events), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        when(session.consumeExpectedResponse()).thenReturn(true);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        for (var responseId : List.of("response-one", "response-two")) {
            socketListener.onText(webSocket,
                    "{\"type\":\"response.created\",\"response\":{\"id\":\"" + responseId + "\"}}", true);
            socketListener.onText(webSocket,
                    "{\"type\":\"response.output_item.created\",\"response_id\":\"" + responseId + "\","
                            + "\"item\":{\"type\":\"message\",\"id\":\"message-" + responseId + "\"}}", true);
            socketListener.onText(webSocket,
                    "{\"type\":\"response.output_text.done\",\"response_id\":\"" + responseId + "\","
                            + "\"item_id\":\"message-" + responseId + "\","
                            + "\"text\":\"Please confirm the booking details.\"}", true);
            socketListener.onText(webSocket,
                    "{\"type\":\"response.done\",\"response\":{\"id\":\"" + responseId + "\"}}", true);
        }

        assertThat(events).containsExactly(
                "agent:Please confirm the booking details.:false"
        );
    }

    @Test
    void anInterruptedToolMayFinishButCannotQueueLateSpeech() throws Exception {
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(realtimeService.executeTool(eq(call), eq("late-tool"), eq("check_availability"), anyString()))
                .thenAnswer(ignored -> {
                    started.countDown();
                    release.await(1, TimeUnit.SECONDS);
                    return new com.sauti.llm.LlmToolResult(
                            "late-tool", "check_availability", true,
                            Map.of("spokenResponse", "That time is available."), ""
                    );
                });
        var socketListener = new OpenAiTelephonyRealtimeConversationProvider.RealtimeWebSocketListener(
                new ObjectMapper(), realtimeService, call,
                new RecordingListener(new ArrayList<>()), Map.of()
        );
        var session = mock(OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.class);
        socketListener.attach(session);
        var webSocket = mock(WebSocket.class);

        socketListener.onText(webSocket,
                "{\"type\":\"response.function_call_arguments.done\","
                        + "\"call_id\":\"late-tool\",\"name\":\"check_availability\",\"arguments\":\"{}\"}", true);
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        socketListener.markCurrentResponseCancelled();
        release.countDown();

        verify(realtimeService, timeout(1_000)).executeTool(
                eq(call), eq("late-tool"), eq("check_availability"), anyString()
        );
        verify(session, never()).requestExactResponse(anyString(), anyLong());
        verify(session, never()).requestToolResultResponse(anyLong());
    }

    @Test
    void interruptionPurgesQueuedResponsesFromTheOlderGeneration() throws Exception {
        var webSocket = mock(WebSocket.class);
        when(webSocket.sendText(anyString(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(webSocket));
        var executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> keepAlive = mock(ScheduledFuture.class);
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(ignored -> keepAlive);
        var session = new OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession(
                webSocket, new ObjectMapper(), executor
        );

        session.requestExactResponse("Old response already in flight.");
        session.requestExactResponse("Old queued duplicate.");
        session.cancelResponse();
        session.requestExactResponse("Fresh response.");
        session.responseFinished();

        verify(webSocket, timeout(1_000).times(2)).sendText(anyString(), eq(true));
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(webSocket, times(2)).sendText(payloads.capture(), eq(true));
        assertThat(payloads.getAllValues()).anyMatch(payload -> payload.contains("Old response already in flight."));
        assertThat(payloads.getAllValues()).anyMatch(payload -> payload.contains("Fresh response."));
        assertThat(payloads.getAllValues()).noneMatch(payload -> payload.contains("Old queued duplicate."));
        assertThat(payloads.getAllValues()).noneMatch(payload -> payload.contains("response.cancel"));
        assertThat(payloads.getAllValues()).allMatch(payload -> payload.contains("sauti_request_id"));
    }

    @Test
    void instructionUpdatesDeclareARealtimeSession() throws Exception {
        var webSocket = mock(WebSocket.class);
        when(webSocket.sendText(anyString(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(webSocket));
        var executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> keepAlive = mock(ScheduledFuture.class);
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(ignored -> keepAlive);
        var session = new OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession(
                webSocket, new ObjectMapper(), executor
        );

        session.updateInstructions("Stay concise.");

        var payload = ArgumentCaptor.forClass(String.class);
        verify(webSocket, timeout(1_000)).sendText(payload.capture(), eq(true));
        var event = new ObjectMapper().readTree(payload.getValue());
        assertThat(event.path("type").asText()).isEqualTo("session.update");
        assertThat(event.path("session").path("type").asText()).isEqualTo("realtime");
        assertThat(event.path("session").path("instructions").asText()).isEqualTo("Stay concise.");
    }

    @Test
    void transcriptMirrorKeepsExactFieldsInAnUnprivilegedUserItem() throws Exception {
        var webSocket = mock(WebSocket.class);
        when(webSocket.sendText(anyString(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(webSocket));
        var executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> keepAlive = mock(ScheduledFuture.class);
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenAnswer(ignored -> keepAlive);
        var session = new OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession(
                webSocket, new ObjectMapper(), executor
        );

        session.mirrorCallerTranscript("My name is Zachary and my number is 0105753221.");

        var payload = ArgumentCaptor.forClass(String.class);
        verify(webSocket, timeout(1_000)).sendText(payload.capture(), eq(true));
        var event = new ObjectMapper().readTree(payload.getValue());
        assertThat(event.path("type").asText()).isEqualTo("conversation.item.create");
        assertThat(event.path("item").path("role").asText()).isEqualTo("user");
        assertThat(event.path("item").path("content").path(0).path("type").asText()).isEqualTo("input_text");
        assertThat(event.path("item").path("content").path(0).path("text").asText())
                .startsWith("SAUTI_INPUT_TRANSCRIPT:")
                .contains("If it is incoherent or not a clear answer or choice")
                .contains("do not update state, reuse a stored choice, or call a business tool")
                .endsWith("My name is Zachary and my number is 0105753221.");
    }

    @Test
    void convertsSixteenKilohertzPcmToTwentyFourKilohertzPcm() {
        var input = new byte[] {0, 0, 10, 0, 20, 0, 30, 0};

        var output = OpenAiTelephonyRealtimeConversationProvider.OpenAiTelephonySession.pcm16kToPcm24k(input);

        assertThat(output).hasSize(12);
        assertThat(output).isNotEqualTo(input);
    }

    private static final class RecordingListener implements TelephonyRealtimeConversationProvider.Listener {
        private final List<String> events;

        private RecordingListener(List<String> events) {
            this.events = events;
        }

        @Override
        public void onCallerSpeechStarted() {
            events.add("speech");
        }

        @Override
        public void onCallerTranscript(String transcript) {
            events.add("caller:" + transcript);
        }

        @Override
        public void onAgentTextDelta(String delta) {
            events.add("delta:" + delta);
        }

        @Override
        public void onAgentTextComplete(String text, boolean interrupted) {
            events.add("agent:" + text + ":" + interrupted);
        }

        @Override
        public void onError(Throwable error) {
            events.add("error:" + error.getMessage());
        }

        @Override
        public void onDisconnected(Throwable error) {
            events.add("disconnected:" + error.getMessage());
        }
    }
}
