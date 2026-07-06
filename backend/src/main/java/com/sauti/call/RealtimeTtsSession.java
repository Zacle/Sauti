package com.sauti.call;

public interface RealtimeTtsSession extends AutoCloseable {
    void speak(String text, boolean flush);

    @Override
    void close();
}
