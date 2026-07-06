package com.sauti.call;

import java.util.concurrent.CompletableFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.tts.streaming-provider", havingValue = "noop", matchIfMissing = true)
public class NoopRealtimeTextToSpeechProvider implements RealtimeTextToSpeechProvider {
    @Override
    public CompletableFuture<RealtimeTtsSession> open(String language, String voiceId, TtsAudioListener listener) {
        return CompletableFuture.completedFuture(new RealtimeTtsSession() {
            @Override
            public void speak(String text, boolean flush) {
                if (flush) {
                    listener.onComplete();
                }
            }

            @Override
            public void close() {
            }
        });
    }
}
