package com.sauti.outbound;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.agent.TelephonyProvider;
import com.sauti.calendar.Booking;
import com.sauti.call.CallPipelineService;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboundCallService {
    private final ScheduledCallRepository scheduledCallRepository;
    private final AgentRepository agentRepository;
    private final ObjectProvider<TelephonyProvider> telephonyProvider;
    private final ObjectProvider<CallPipelineService> callPipelineService;
    private final boolean enabled;
    private final String defaultFromNumber;

    public OutboundCallService(
            ScheduledCallRepository scheduledCallRepository,
            AgentRepository agentRepository,
            ObjectProvider<TelephonyProvider> telephonyProvider,
            ObjectProvider<CallPipelineService> callPipelineService,
            @Value("${sauti.telnyx.outbound.enabled:false}") boolean enabled,
            @Value("${sauti.telnyx.outbound.from-number:}") String defaultFromNumber
    ) {
        this.scheduledCallRepository = scheduledCallRepository;
        this.agentRepository = agentRepository;
        this.telephonyProvider = telephonyProvider;
        this.callPipelineService = callPipelineService;
        this.enabled = enabled;
        this.defaultFromNumber = defaultFromNumber;
    }

    @Transactional
    public void scheduleReminder(Booking booking) {
        var reminderAt = booking.getAppointmentAt().minusHours(24);
        if (reminderAt.isBefore(OffsetDateTime.now())) {
            return;
        }
        scheduledCallRepository.save(new ScheduledCall(
                booking.getTenant(),
                booking.getAgent(),
                booking,
                "booking_reminder",
                booking.getCallerPhone(),
                reminderAt
        ));
    }

    @Transactional
    public ScheduledCall scheduleCallback(UUID tenantId, UUID agentId, String targetPhone, OffsetDateTime scheduledFor) {
        Agent agent = agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        return scheduledCallRepository.save(new ScheduledCall(agent.getTenant(), agent, null, "callback", targetPhone, scheduledFor));
    }

    @Transactional
    public int deleteBookingReminders(UUID tenantId, UUID bookingId) {
        return scheduledCallRepository.deleteAllForBooking(tenantId, bookingId);
    }

    @Scheduled(fixedDelayString = "${sauti.telnyx.outbound.poll-delay-ms:60000}")
    @Transactional
    public void initiateDueCalls() {
        if (!enabled) {
            return;
        }
        scheduledCallRepository.findTop25ByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc("pending", OffsetDateTime.now())
                .forEach(this::initiate);
    }

    private void initiate(ScheduledCall scheduledCall) {
        try {
            var from = scheduledCall.getAgent().getTwilioPhoneNumber();
            if (from == null || from.isBlank()) from = defaultFromNumber;
            var callControlId = telephonyProvider.getObject().createOutboundCall(
                    scheduledCall.getTargetPhone(),
                    from,
                    "scheduled-call:" + scheduledCall.getId()
            );
            callPipelineService.getObject().startOutboundCall(
                    scheduledCall.getAgent(),
                    callControlId,
                    scheduledCall.getTargetPhone()
            );
            scheduledCall.markInitiated(callControlId);
        } catch (Exception exception) {
            scheduledCall.markFailed(exception.getMessage());
        }
    }
}
