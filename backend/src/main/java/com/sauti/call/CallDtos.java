package com.sauti.call;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class CallDtos {
    private CallDtos() {
    }

    public record CallResponse(
            UUID id,
            UUID agentId,
            String twilioCallSid,
            String callerNumber,
            String direction,
            String languageDetected,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            Integer durationSeconds,
            String outcome,
            String transcript,
            String conversationJson,
            String recordingUrl,
            String recordingSid,
            String failureReason,
            String callSummary,
            Boolean callSuccessful,
            String sentiment,
            String intent,
            String transferStatus,
            String transferTargetNumber,
            String transferChildCallSid,
            String transferFailureReason,
            OffsetDateTime transferRequestedAt,
            OffsetDateTime transferCompletedAt,
            boolean afterHours
    ) {
        public static CallResponse from(Call call) {
            return new CallResponse(
                    call.getId(),
                    call.getAgent().getId(),
                    call.getTwilioCallSid(),
                    call.getCallerNumber(),
                    call.getDirection(),
                    call.getLanguageDetected(),
                    call.getStartedAt(),
                    call.getEndedAt(),
                    call.getDurationSeconds(),
                    call.getOutcome(),
                    call.getTranscript(),
                    call.getConversationJson(),
                    call.getRecordingUrl(),
                    call.getRecordingSid(),
                    call.getFailureReason(),
                    call.getCallSummary(),
                    call.getCallSuccessful(),
                    call.getSentiment(),
                    call.getIntent(),
                    call.getTransferStatus(),
                    call.getTransferTargetNumber(),
                    call.getTransferChildCallSid(),
                    call.getTransferFailureReason(),
                    call.getTransferRequestedAt(),
                    call.getTransferCompletedAt(),
                    call.isAfterHours()
            );
        }
    }

    public record SimulatedTurnRequest(String transcript) {
    }

    public record SimulatedTurnResponse(String language, String response, String transcript, String outcome, boolean acceptedTranscript) {
    }

    public record StartTestCallRequest(UUID agentId, String ttsVoiceId) {
    }

    public record CompleteTestCallRequest(String outcome) {
    }

    public record StartTestCallResponse(
            CallResponse call,
            String greeting,
            TestCallSettings settings,
            String websocketUrl,
            String token,
            int inputSampleRate
    ) {
    }

    public record TestCallSettings(
            double bargeInSensitivity,
            int bargeInGraceMs,
            int sttEndpointingMs,
            int maxCallDurationSeconds,
            int endCallOnSilenceSeconds,
            int reminderAfterSilenceSeconds,
            int maxReminders,
            boolean detectVoicemail,
            boolean handleCallScreening
    ) {
        public static TestCallSettings from(com.sauti.agent.Agent agent) {
            return new TestCallSettings(
                    agent.getBargeInSensitivity(),
                    agent.getBargeInGraceMs(),
                    agent.getSttEndpointingMs(),
                    agent.getMaxCallDurationSeconds(),
                    agent.getEndCallOnSilenceSeconds(),
                    agent.getReminderAfterSilenceSeconds(),
                    agent.getMaxReminders(),
                    agent.isDetectVoicemail(),
                    agent.isHandleCallScreening()
            );
        }
    }

    public record TestAudioTurnResponse(
            String callerTranscript,
            String language,
            String response,
            String outcome,
            String audioBase64,
            int sttLatencyMs,
            int llmLatencyMs,
            int ttsLatencyMs,
            int totalLatencyMs
    ) {
    }

    public record CallTurnResponse(
            int turnIndex,
            String callerTranscript,
            String agentResponse,
            String language,
            boolean interrupted
    ) {
        public static CallTurnResponse from(CallTurn turn) {
            return new CallTurnResponse(
                    turn.getTurnIndex(),
                    turn.getCallerTranscript(),
                    turn.getAgentResponse(),
                    turn.getLanguage(),
                    turn.isInterrupted()
            );
        }
    }
}
