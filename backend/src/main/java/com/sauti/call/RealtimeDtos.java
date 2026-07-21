package com.sauti.call;

public final class RealtimeDtos {
    private RealtimeDtos() {
    }

    public record RealtimeTranscriptRequest(String role, String text, boolean interrupted) {
    }

    public record RealtimeTranscriptResponse(String instructions, String directResponse, String requiredTool) {
        public RealtimeTranscriptResponse(String instructions) {
            this(instructions, "", "");
        }

        public RealtimeTranscriptResponse(String instructions, String directResponse) {
            this(instructions, directResponse, "");
        }
    }

    public record RealtimeToolRequest(String callId, String name, String arguments) {
    }
}
