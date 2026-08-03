package com.sauti.demo;

public class PublicDemoVoiceLimitExceededException extends RuntimeException {
    private PublicDemoVoiceLimitExceededException(String message) {
        super(message);
    }

    public static PublicDemoVoiceLimitExceededException visitorLimit() {
        return new PublicDemoVoiceLimitExceededException(
                "You have used today’s short voice demos. Request a tailored demo and we’ll continue with your business use case."
        );
    }

    public static PublicDemoVoiceLimitExceededException atCapacity() {
        return new PublicDemoVoiceLimitExceededException(
                "The live voice demo is busy right now. Please try again in a minute or request a tailored demo."
        );
    }
}
