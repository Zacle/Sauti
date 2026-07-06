package com.sauti.call;

import java.util.concurrent.CompletableFuture;

public interface RealtimeTextToSpeechProvider {
    CompletableFuture<RealtimeTtsSession> open(String language, String voiceId, TtsAudioListener listener);
}
