package com.sauti.call;

import com.sauti.agent.Agent;
import java.util.concurrent.CompletableFuture;

public interface RealtimeSpeechToTextProvider {
    CompletableFuture<RealtimeSttSession> open(RealtimeTranscriptListener listener);

    default CompletableFuture<RealtimeSttSession> open(Agent agent, RealtimeTranscriptListener listener) {
        return open(listener);
    }
}
