package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeepgramRealtimeSpeechToTextProviderTest {
    @Test
    void deliversTranscriptImmediatelyWhenDeepgramConfirmsSpeechFinal() {
        var finalTranscripts = new ArrayList<String>();
        var listener = new DeepgramRealtimeSpeechToTextProvider.DeepgramWebSocketListener(
                new ObjectMapper(),
                transcriptListener(finalTranscripts)
        );

        listener.handle(result("zero one one", true, false));
        assertThat(finalTranscripts).isEmpty();

        listener.handle(result("five seven five", true, true));

        assertThat(finalTranscripts).containsExactly("zero one one five seven five");
    }

    private RealtimeTranscriptListener transcriptListener(List<String> finalTranscripts) {
        return new RealtimeTranscriptListener() {
            @Override
            public void onPartialTranscript(String transcript, double confidence) {
            }

            @Override
            public void onFinalTranscript(String transcript) {
                finalTranscripts.add(transcript);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        };
    }

    private String result(String transcript, boolean isFinal, boolean speechFinal) {
        return """
                {"type":"Results","is_final":%s,"speech_final":%s,
                 "channel":{"alternatives":[{"transcript":"%s","confidence":0.99}]}}
                """.formatted(isFinal, speechFinal, transcript);
    }
}
