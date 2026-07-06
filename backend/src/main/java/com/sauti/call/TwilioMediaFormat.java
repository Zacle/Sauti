package com.sauti.call;

public record TwilioMediaFormat(
        String encoding,
        int sampleRate,
        int channels
) {
    public static TwilioMediaFormat unknown() {
        return new TwilioMediaFormat("", 0, 0);
    }
}
