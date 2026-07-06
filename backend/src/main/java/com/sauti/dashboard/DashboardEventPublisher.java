package com.sauti.dashboard;

import com.sauti.calendar.Booking;
import com.sauti.call.Call;
import com.sauti.dashboard.DashboardDtos.DashboardEvent;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardEventPublisher {
    private final DashboardWebSocketHandler dashboardWebSocketHandler;

    public DashboardEventPublisher(DashboardWebSocketHandler dashboardWebSocketHandler) {
        this.dashboardWebSocketHandler = dashboardWebSocketHandler;
    }

    public void callStarted(Call call) {
        publish(DashboardEvent.of("call.started", call.getTenant().getId(), callPayload(call)));
    }

    public void transcriptPartial(Call call, String transcript, double confidence) {
        publish(DashboardEvent.of("transcript.partial", call.getTenant().getId(), Map.of(
                "callId", call.getId().toString(),
                "twilioCallSid", call.getTwilioCallSid(),
                "transcript", transcript,
                "confidence", confidence
        )));
    }

    public void transcriptFinal(Call call, String transcript) {
        publish(DashboardEvent.of("transcript.final", call.getTenant().getId(), Map.of(
                "callId", call.getId().toString(),
                "twilioCallSid", call.getTwilioCallSid(),
                "transcript", transcript
        )));
    }

    public void agentSpeaking(Call call, boolean speaking) {
        publish(DashboardEvent.of("agent.speaking", call.getTenant().getId(), Map.of(
                "callId", call.getId().toString(),
                "twilioCallSid", call.getTwilioCallSid(),
                "speaking", speaking
        )));
    }

    public void callEnded(Call call) {
        publish(DashboardEvent.of("call.ended", call.getTenant().getId(), callPayload(call)));
    }

    public void bookingCreated(Booking booking) {
        publish(DashboardEvent.of("booking.created", booking.getTenant().getId(), Map.of(
                "bookingId", booking.getId().toString(),
                "agentId", booking.getAgent().getId().toString(),
                "callId", booking.getCall() == null ? "" : booking.getCall().getId().toString(),
                "callerName", booking.getCallerName(),
                "callerPhone", booking.getCallerPhone(),
                "serviceType", booking.getServiceType(),
                "appointmentAt", booking.getAppointmentAt().toString(),
                "status", booking.getStatus()
        )));
    }

    private void publish(DashboardEvent event) {
        dashboardWebSocketHandler.publish(event);
    }

    private Map<String, Object> callPayload(Call call) {
        return Map.of(
                "callId", call.getId().toString(),
                "agentId", call.getAgent().getId().toString(),
                "agentName", call.getAgent().getName(),
                "twilioCallSid", call.getTwilioCallSid(),
                "callerNumber", call.getCallerNumber() == null ? "" : call.getCallerNumber(),
                "direction", call.getDirection(),
                "outcome", call.getOutcome() == null ? "" : call.getOutcome(),
                "startedAt", call.getStartedAt().toString(),
                "endedAt", call.getEndedAt() == null ? "" : call.getEndedAt().toString(),
                "durationSeconds", call.getDurationSeconds() == null ? 0 : call.getDurationSeconds()
        );
    }
}
