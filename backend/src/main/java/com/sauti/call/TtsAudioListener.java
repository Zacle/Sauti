package com.sauti.call;

public interface TtsAudioListener {
    void onPcmAudio(byte[] pcm16kAudio);

    void onComplete();

    void onError(Throwable error);
}
