package com.sauti.call;

import java.util.concurrent.CompletableFuture;

/** Low-latency audio-to-text-response runtime used by real telephony media streams. */
public interface TelephonyRealtimeConversationProvider {
    boolean supports(Call call);

    CompletableFuture<Session> open(Call call, Listener listener);

    interface Session extends RealtimeSttSession {
        void seedAssistantText(String text);

        void sendUserText(String text);

        void cancelResponse();
    }

    interface Listener {
        void onCallerSpeechStarted();

        void onCallerTranscript(String transcript);

        void onAgentTextDelta(String delta);

        void onAgentTextComplete(String text, boolean interrupted);

        default void onCallEndAuthorized(String outcome) { }

        void onError(Throwable error);

        void onDisconnected(Throwable error);
    }
}
