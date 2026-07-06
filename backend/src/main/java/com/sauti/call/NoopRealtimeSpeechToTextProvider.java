package com.sauti.call;

import java.util.concurrent.CompletableFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.stt.streaming-provider", havingValue = "noop", matchIfMissing = true)
public class NoopRealtimeSpeechToTextProvider implements RealtimeSpeechToTextProvider {
    @Override
    public CompletableFuture<RealtimeSttSession> open(RealtimeTranscriptListener listener) {
        return CompletableFuture.completedFuture(new RealtimeSttSession() {
            @Override
            public void sendPcmAudio(byte[] pcm16kAudio) {
            }

            @Override
            public void close() {
            }
        });
    }
}
