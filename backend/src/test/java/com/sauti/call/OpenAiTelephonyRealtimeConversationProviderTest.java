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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class OpenAiTelephonyRealtimeConversationProviderTest {
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
        socketListener.onText(webSocket, "{\"type\":\"response.output_text.delta\",\"delta\":\"I can help.\"}", true);
        socketListener.onText(webSocket, "{\"type\":\"response.output_text.done\",\"text\":\"I can help.\"}", true);

        assertThat(events).containsExactly(
                "speech", "caller:Please book Monday", "delta:I can help.", "agent:I can help.:false"
        );
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
    void requiresTheAvailabilityToolBeforeRespondingToAnExactTime() {
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

        verify(session).requestResponseWithRequiredTool("check_availability");
        verify(session, never()).requestResponse();
    }

    @Test
    void convertsStructuredAvailabilityTextIntoASilentToolCall() {
        var events = new ArrayList<String>();
        var realtimeService = mock(OpenAiRealtimeService.class);
        var call = mock(Call.class);
        var toolResult = new com.sauti.llm.LlmToolResult(
                "recovered", "check_availability", true,
                Map.of("status", "requested_time_available"), ""
        );
        when(realtimeService.executeTool(eq(call), anyString(), eq("check_availability"), anyString()))
                .thenReturn(toolResult);
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

        assertThat(events).containsExactly("speech", "caller:Demain a midi");
        var recoveredCallId = ArgumentCaptor.forClass(String.class);
        verify(realtimeService, timeout(1_000)).executeTool(
                eq(call), recoveredCallId.capture(), eq("check_availability"),
                eq("{\"date\":\"2030-01-15\",\"time_preference\":\"12:00\"}")
        );
        assertThat(recoveredCallId.getValue()).hasSizeLessThanOrEqualTo(32);
        verify(session, timeout(1_000)).requestToolResultResponse();
    }

    @Test
    void speaksTheDeterministicAvailabilityDecisionWithoutAskingTheModelToRewriteIt() {
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
        assertThat(events).containsExactly("delta:" + spoken, "agent:" + spoken + ":false");
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

        verify(session).cancelResponse();
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
