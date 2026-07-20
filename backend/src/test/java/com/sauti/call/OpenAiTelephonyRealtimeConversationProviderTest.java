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
    void letsTheMultilingualModelChooseTheAvailabilityToolFromMeaning() {
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
    void queuesTheDeterministicAvailabilityDecisionForExactProviderSpeech() {
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
        verify(session, timeout(1_000)).requestExactResponse(spoken, 0L);
        assertThat(events).isEmpty();
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
        verify(session, timeout(1_000).times(1)).requestExactResponse("That time is available.", 0L);
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
        socketListener.attach(session);

        socketListener.onText(mock(WebSocket.class), "{\"type\":\"response.created\"}", true);

        verify(session).cancelProviderResponse();
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

        verify(webSocket, timeout(1_000).times(3)).sendText(anyString(), eq(true));
        var payloads = ArgumentCaptor.forClass(String.class);
        verify(webSocket, times(3)).sendText(payloads.capture(), eq(true));
        assertThat(payloads.getAllValues()).anyMatch(payload -> payload.contains("Old response already in flight."));
        assertThat(payloads.getAllValues()).anyMatch(payload -> payload.contains("response.cancel"));
        assertThat(payloads.getAllValues()).anyMatch(payload -> payload.contains("Fresh response."));
        assertThat(payloads.getAllValues()).noneMatch(payload -> payload.contains("Old queued duplicate."));
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
