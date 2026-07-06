package com.sauti.call;

public interface StreamingTtsProvider {
    byte[] synthesize(String language, String text);
}
