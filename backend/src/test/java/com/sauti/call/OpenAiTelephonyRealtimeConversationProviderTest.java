package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiTelephonyRealtimeConversationProviderTest {
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
