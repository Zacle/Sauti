package com.sauti.call;

public interface BrowserVoiceRuntimeProvider {
    String provider();

    boolean isConfigured();

    BrowserVoiceRuntimeSession prepare(Call call, String greeting, String callbackToken);
}
