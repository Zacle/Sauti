package com.sauti.call;

public final class RealtimeDtos {
    private RealtimeDtos() {
    }

    public record RealtimeTranscriptRequest(String role, String text, boolean interrupted) {
    }

    public record RealtimeTranscriptResponse(String instructions) {
    }

    public record RealtimeToolRequest(String callId, String name, String arguments) {
    }
}
