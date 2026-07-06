package com.sauti.call;

public interface RealtimeSttSession extends AutoCloseable {
    void sendPcmAudio(byte[] pcm16kAudio);

    @Override
    void close();
}
