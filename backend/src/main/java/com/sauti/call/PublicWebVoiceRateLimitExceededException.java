package com.sauti.call;

public class PublicWebVoiceRateLimitExceededException extends RuntimeException {
    public PublicWebVoiceRateLimitExceededException() {
        super("Too many Web Voice sessions. Please try again shortly.");
    }
}
