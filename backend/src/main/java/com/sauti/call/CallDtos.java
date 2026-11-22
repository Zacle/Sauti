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
            OffsetDateTime privacyRedactedAt,
            OffsetDateTime recordingPurgedAt,
            boolean afterHours,
            String recordStatus,
            CallBookingSummary booking,
            /** In a search result: where the search text was found, such as the transcript line. */
            String matchedText
    ) {
        public static CallResponse from(Call call) {
            return from(call, null, true);
        }

        public static CallResponse from(Call call, String recordStage, boolean withTranscript) {
            return from(call, recordStage, withTranscript, null);
        }

        /**
         * @param recordStage the unfinished stage of the call's record work ({@code transcript} or
         *                    {@code analysis}), or null when the record is complete
         * @param withTranscript false for lists, which link to the transcript instead of carrying it
         */
        public static CallResponse from(Call call, String recordStage, boolean withTranscript,
                                        CallBookingSummary booking) {
            return from(call, recordStage, withTranscript, booking, null);
        }

        public static CallResponse from(Call call, String recordStage, boolean withTranscript,
                                        CallBookingSummary booking, String matchedText) {
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
                    withTranscript ? call.getTranscript() : null,
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
                    call.getPrivacyRedactedAt(),
                    call.getRecordingPurgedAt(),
                    call.isAfterHours(),
                    recordStage,
                    booking,
                    matchedText
            );
        }
    }

    /** The booking made on a call, enough to show it in the call list and link to it. */
    public record CallBookingSummary(UUID id, String bookingReference, String serviceType,
                                     OffsetDateTime appointmentAt, String status, String callerName) {
    }

    /** One page of calls, newest first, and totals over every call that matches. */
    public record CallSearchResponse(
            java.util.List<CallResponse> items,
            long total,
            int page,
            int size,
            long answered,
            long averageDurationSeconds,
            long bookings
    ) {
    }

    public record SimulatedTurnRequest(String transcript) {
    }

    public record SimulatedTurnResponse(String language, String response, String transcript, String outcome, boolean acceptedTranscript) {
    }

    public record StartTestCallRequest(UUID agentId, String ttsVoiceId, String language) {
    }

    public record CompleteTestCallRequest(
            String outcome,
            String providerCallControlId,
            String providerCallLegId
    ) {
    }

    public record ProviderCallCorrelationRequest(String providerCallControlId, String providerCallLegId) {
    }

    public record StartupLatencyRequest(int latencyMs) {
    }

    public record StartTestCallResponse(
            CallResponse call,
            String greeting,
            TestCallSettings settings,
            BrowserVoiceRuntimeSession runtime
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
