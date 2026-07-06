package com.sauti.call;

public interface StreamingSttProvider {
    String transcribe(byte[] audioPayload);
}
