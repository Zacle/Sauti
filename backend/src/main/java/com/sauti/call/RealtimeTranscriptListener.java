package com.sauti.call;

public interface RealtimeTranscriptListener {
    default void onPartialTranscript(String transcript) {
        onPartialTranscript(transcript, 1.0);
    }

    void onPartialTranscript(String transcript, double confidence);

    void onFinalTranscript(String transcript);

    void onError(Throwable error);
}
