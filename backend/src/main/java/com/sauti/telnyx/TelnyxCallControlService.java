package com.sauti.telnyx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.TelnyxTelephonyProvider;
import com.sauti.call.CallPipelineService;
import com.sauti.call.CallQueryService;
import com.sauti.billing.ProviderCostReconciliationService;
import com.sauti.billing.BillingAccessPolicy;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.sauti.provisioning.PilotProvisioningPolicyService;

@Service
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "telnyx")
public class TelnyxCallControlService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelnyxCallControlService.class);
    private final ObjectMapper objectMapper;
    private final TelnyxWebhookEventRepository eventRepository;
    private final CallPipelineService callPipelineService;
    private final TelnyxTelephonyProvider telephonyProvider;
    private final CallQueryService callQueryService;
    private final ProviderCostReconciliationService costReconciliation;
    private final PilotProvisioningPolicyService provisioningPolicies;
    private final BillingAccessPolicy billingAccess;
    private final io.micrometer.core.instrument.MeterRegistry meters;
    private final com.sauti.call.CallTransferService transfers;
    /**
     * Events for one call run in order on one lane; different calls run on different lanes, so a slow
     * answer for one caller never holds up the ringing of another.
     */
    static final int LANES = 8;
    private final ExecutorService[] lanes = new ExecutorService[LANES];
    /**
     * A ring or an answer is only worth acting on while the caller is still there. Past this age a
     * replayed call.initiated is recorded as a missed call instead of being answered late.
     */
    static final java.time.Duration CALLER_WAITS = java.time.Duration.ofSeconds(20);
    /** Failed handling of other events is retried this many times in all, with growing waits. */
    static final int MAX_ATTEMPTS = 5;
    private static final java.util.Set<String> TIME_SENSITIVE = java.util.Set.of("call.initiated", "call.answered");
    private java.time.Clock clock = java.time.Clock.systemUTC();

    public TelnyxCallControlService(
            ObjectMapper objectMapper,
            TelnyxWebhookEventRepository eventRepository,
            CallPipelineService callPipelineService,
            TelnyxTelephonyProvider telephonyProvider,
            CallQueryService callQueryService,
            ProviderCostReconciliationService costReconciliation,
            PilotProvisioningPolicyService provisioningPolicies,
            BillingAccessPolicy billingAccess,
            io.micrometer.core.instrument.MeterRegistry meters,
            com.sauti.call.CallTransferService transfers
    ) {
        this.meters = meters;
        this.transfers = transfers;
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository;
        this.callPipelineService = callPipelineService;
        this.telephonyProvider = telephonyProvider;
        this.callQueryService = callQueryService;
        this.costReconciliation = costReconciliation;
        this.provisioningPolicies = provisioningPolicies;
        this.billingAccess = billingAccess;
        for (int index = 0; index < LANES; index++) {
            var name = "telnyx-call-control-" + index;
            lanes[index] = Executors.newSingleThreadExecutor(runnable -> {
                var thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    /** For tests: the time used to judge how old an event is. */
    void useClock(java.time.Clock clock) {
        this.clock = clock;
    }

    static int lane(String callControlId) {
        return Math.floorMod(callControlId == null ? 0 : callControlId.hashCode(), LANES);
    }

    public void accept(String rawPayload) {
        try {
            var data = objectMapper.readTree(rawPayload).path("data");
            var eventId = data.path("id").asText("");
            var eventType = data.path("event_type").asText("");
            var payload = data.path("payload");
            var callControlId = payload.path("call_control_id").asText("");
            if (eventId.isBlank() || eventType.isBlank()) {
                throw new IllegalArgumentException("Telnyx webhook is missing its event identity");
            }
            var occurredAt = parseTime(data.path("occurred_at").asText(""));
            if (!claim(eventId, eventType, callControlId, occurredAt, payload.toString())) {
                return;
            }
            lanes[lane(callControlId)].execute(() -> process(eventId, eventType, payload, occurredAt));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Telnyx webhook payload", exception);
        }
    }

    /**
     * Picks up events the server never finished: received but not started (it stopped before the
     * lane ran them), processing that never ended (it stopped mid-way), and failures due a retry.
     * Events whose caller has gone are closed out instead; see {@link #expire}.
     */
    @org.springframework.scheduling.annotation.Scheduled(
            initialDelayString = "${sauti.telnyx.event-replay-initial-delay-ms:10000}",
            fixedDelayString = "${sauti.telnyx.event-replay-delay-ms:15000}")
    public void replayUnfinished() {
        var now = OffsetDateTime.now(clock);
        for (var event : eventRepository.findTop100ByNextAttemptAtBeforeOrderByNextAttemptAtAsc(now)) {
            try {
                replay(event, now);
            } catch (Exception failure) {
                LOGGER.warn("Could not replay Telnyx event type={}", event.getEventType(), failure);
            }
        }
    }

    private void replay(TelnyxWebhookEvent event, OffsetDateTime now) throws Exception {
        if (event.getPayloadJson() == null) {
            event.markFailed("Nothing stored to replay");
            eventRepository.save(event);
            return;
        }
        var payload = objectMapper.readTree(event.getPayloadJson());
        if (transferCallId(payload).isEmpty()
                && TIME_SENSITIVE.contains(event.getEventType())
                && event.happenedAt() != null && event.happenedAt().plus(CALLER_WAITS).isBefore(now)) {
            expire(event, payload);
            return;
        }
        if (event.getAttempts() >= MAX_ATTEMPTS) {
            event.markFailed("Gave up after " + event.getAttempts() + " attempts");
            eventRepository.save(event);
            return;
        }
        event.claimForReplay(now);
        eventRepository.save(event);
        LOGGER.info("Replaying an unfinished Telnyx event type={} attempt={}", event.getEventType(), event.getAttempts() + 1);
        lanes[lane(event.getCallControlId())].execute(() -> process(
                event.getProviderEventId(), event.getEventType(), payload, event.getOccurredAt()));
    }

    /**
     * Too late to answer: a missed ring is recorded as an unanswered call (so the owner sees it) and
     * turned away in case it is somehow still ringing; a missed outbound answer is hung up rather
     * than left silent.
     */
    private void expire(TelnyxWebhookEvent event, JsonNode payload) {
        var callControlId = payload.path("call_control_id").asText("");
        if ("call.initiated".equals(event.getEventType())) {
            var direction = payload.path("direction").asText("incoming").toLowerCase(java.util.Locale.ROOT);
            if (direction.contains("incoming") || direction.contains("inbound")) {
                callPipelineService.inboundAgent(payload.path("to").asText("")).ifPresent(agent ->
                        callPipelineService.recordUnansweredInboundCall(
                                agent, callControlId, payload.path("from").asText(""), "not_handled"));
                safely(() -> telephonyProvider.reject(callControlId));
            }
        } else if (!callControlId.isBlank()) {
            safely(() -> telephonyProvider.hangup(callControlId));
        }
        LOGGER.info("Expired a Telnyx event the server did not handle in time type={}", event.getEventType());
        event.markExpired("Not handled while the caller was waiting");
        eventRepository.save(event);
    }

    void process(String eventId, String eventType, JsonNode payload, OffsetDateTime occurredAt) {
        var event = eventRepository.findByProviderEventId(eventId).orElseThrow();
        if ("completed".equals(event.getStatus()) || "expired".equals(event.getStatus())) return;
        event.markProcessing();
        eventRepository.save(event);
        try {
            var transferOf = transferCallId(payload);
            if (transferOf.isPresent()) {
                handleTransferLeg(eventType, payload, transferOf.get());
                event.markCompleted();
                eventRepository.save(event);
                return;
            }
            switch (eventType) {
                case "call.initiated" -> handleInitiated(payload);
                case "call.answered" -> handleAnswered(payload);
                case "call.hangup" -> handleHangup(payload, occurredAt);
                case "call.recording.saved" -> handleRecording(payload);
                case "call.conversation.ended" -> handleConversationEnded(payload);
                case "call.speak.ended" -> handleSpeakEnded(payload);
                case "streaming.failed" -> handleStreamingFailed(payload);
                default -> {
                    // Persist and acknowledge lifecycle events that require no Sauti state change.
                }
            }
            event.markCompleted();
        } catch (Exception exception) {
            LOGGER.warn("Telnyx event processing failed eventId={} type={}", eventId, eventType, exception);
            if (TIME_SENSITIVE.contains(eventType)) {
                // A retry would come after the caller has given up; do not leave them ringing meanwhile.
                if ("call.initiated".equals(eventType)) {
                    var callControlId = payload.path("call_control_id").asText("");
                    if (!callControlId.isBlank()) safely(() -> telephonyProvider.reject(callControlId));
                }
                event.markFailed(exception.getMessage());
            } else {
                event.markFailed(exception.getMessage(), event.getAttempts() >= MAX_ATTEMPTS ? null : retryAt(event.getAttempts()));
            }
        }
        eventRepository.save(event);
    }

    /** 30 s, 1 min, 2 min, 4 min after the 1st to 4th failure. */
    private OffsetDateTime retryAt(int attempts) {
        return OffsetDateTime.now(clock).plusSeconds(30L << Math.max(0, Math.min(attempts - 1, 4)));
    }

    /**
     * A call is ringing on one of our numbers. Every path ends with the caller hearing something or
     * being turned away at once, never left ringing: no active agent, a blocked workspace, or an
     * agent that cannot start are rejected (and recorded when there is an agent); a closed business
     * plays its message and hangs up; otherwise the agent's assistant answers.
     */
    private void handleInitiated(JsonNode payload) {
        var started = System.nanoTime();
        var result = pickUp(payload);
        if (result != null) {
            // How long Sauti took from the ring reaching it to the caller being answered or turned away.
            io.micrometer.core.instrument.Timer.builder("sauti.voice.inbound.pickup")
                    .description("Time from an inbound call reaching Sauti to it being answered or turned away")
                    .tag("result", result)
                    .publishPercentiles(0.5, 0.95)
                    .register(meters)
                    .record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /** @return answered, closed, no_agent, blocked or failed; null for calls that are not inbound */
    private String pickUp(JsonNode payload) {
        var callControlId = required(payload, "call_control_id");
        var to = required(payload, "to");
        var from = payload.path("from").asText("");
        var normalizedDirection = payload.path("direction").asText("incoming").toLowerCase(java.util.Locale.ROOT);
        if (!normalizedDirection.contains("incoming") && !normalizedDirection.contains("inbound")) {
            return null;
        }
        var agent = callPipelineService.inboundAgent(to).orElse(null);
        if (agent == null) {
            LOGGER.info("Rejected a call to a number with no active agent");
            safely(() -> telephonyProvider.reject(callControlId));
            return "no_agent";
        }
        var blocked = blockReason(agent.getTenant().getId());
        if (blocked != null) {
            callPipelineService.recordUnansweredInboundCall(agent, callControlId, from, blocked);
            safely(() -> telephonyProvider.reject(callControlId));
            return "blocked";
        }
        var call = callPipelineService.startInboundCall(to, callControlId, from);
        var greeting = callQueryService.firstAgentResponse(call.getTenant().getId(), call.getId());
        var businessHours = businessHours(call);
        try {
            if ("closed".equals(businessHours)) {
                telephonyProvider.answerWithClosedMessage(call, callControlId, greeting);
                return "closed";
            }
            telephonyProvider.answerInboundCall(call, callControlId, greeting, businessHours);
            return "answered";
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not answer an inbound call callId={}", call.getId(), failure);
            callPipelineService.recordUnansweredInboundCall(agent, callControlId, from, "agent_unavailable");
            // Still ringing: reject. Already answered (the assistant failed to start): hang up.
            if (!safely(() -> telephonyProvider.reject(callControlId))) safely(() -> telephonyProvider.hangup(callControlId));
            return "failed";
        }
    }

    /** The Sauti call whose transfer leg this event belongs to, if it is one. */
    private java.util.Optional<java.util.UUID> transferCallId(JsonNode payload) {
        var state = payload.path("client_state").asText("");
        if (state.isBlank()) return java.util.Optional.empty();
        try {
            return com.sauti.call.CallTransferService.transferCallId(
                    new String(java.util.Base64.getDecoder().decode(state), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalArgumentException notOurs) {
            return java.util.Optional.empty();
        }
    }

    /**
     * The team member's leg of a transfer: answering or bridging means the caller is connected; a
     * hang-up before that means the transfer failed, and why. The caller's own leg is untouched.
     */
    private void handleTransferLeg(String eventType, JsonNode payload, java.util.UUID callId) {
        var legId = payload.path("call_control_id").asText("");
        switch (eventType) {
            case "call.answered", "call.bridged" -> transfers.handleDialResult(callId, "answered", legId);
            case "call.hangup" -> transfers.handleDialResult(callId, switch (payload.path("hangup_cause").asText("")) {
                case "user_busy" -> "busy";
                case "timeout", "no_answer" -> "no_answer";
                case "originator_cancel" -> "canceled";
                default -> "failed";
            }, legId);
            default -> {
                // Other events on the transfer leg change nothing in Sauti.
            }
        }
    }

    /** Why the workspace may not take calls right now, or null when it may. */
    private String blockReason(java.util.UUID tenantId) {
        try {
            billingAccess.requirePaidCommunication(tenantId);
        } catch (RuntimeException blocked) {
            return "billing_blocked";
        }
        try {
            provisioningPolicies.authorize(tenantId, "live_calling");
        } catch (IllegalStateException blocked) {
            return "calling_not_allowed";
        }
        return null;
    }

    /** "open", "closed", or how the assistant should behave after hours. */
    static String businessHours(com.sauti.call.Call call) {
        if (!call.isAfterHours()) return TelnyxTelephonyProvider.BUSINESS_OPEN;
        var behaviour = call.getAgent().getAfterHoursBehavior();
        if ("closed".equals(behaviour)) return "closed";
        if ("take_message".equals(behaviour)) return "after_hours_take_message";
        return "after_hours_answer";
    }

    private void handleSpeakEnded(JsonNode payload) {
        var state = payload.path("client_state").asText("");
        if (state.isBlank()) return;
        String decoded;
        try {
            decoded = new String(java.util.Base64.getDecoder().decode(state), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notOurs) {
            return;
        }
        if (TelnyxTelephonyProvider.CLOSED_ANNOUNCEMENT.equals(decoded)) {
            var callControlId = required(payload, "call_control_id");
            callPipelineService.completeActiveCall(callControlId, "closed");
            telephonyProvider.hangup(callControlId);
        }
    }

    /** Runs a provider command whose failure must not stop the caller being dealt with. */
    private boolean safely(Runnable command) {
        try {
            command.run();
            return true;
        } catch (RuntimeException failure) {
            LOGGER.debug("Telnyx call command failed", failure);
            return false;
        }
    }

    private void handleHangup(JsonNode payload, OffsetDateTime occurredAt) {
        var callControlId = required(payload, "call_control_id");
        callPipelineService.completeActiveCall(callControlId, outcome(payload.path("hangup_cause").asText("")));
        costReconciliation.enqueueTelnyxVoiceByCallControlId(
                callControlId, payload.path("call_session_id").asText(""), occurredAt);
    }

    private void handleAnswered(JsonNode payload) {
        var callControlId = required(payload, "call_control_id");
        var call = callQueryService.findActiveByProviderCallId(callControlId);
        if (!"outbound".equals(call.getDirection())) return;
        var greeting = callQueryService.firstAgentResponse(call.getTenant().getId(), call.getId());
        telephonyProvider.startAiAssistant(call, callControlId, greeting);
    }

    private void handleRecording(JsonNode payload) {
        var callControlId = required(payload, "call_control_id");
        var urls = payload.path("recording_urls");
        var url = urls.path("mp3").asText(urls.path("wav").asText(""));
        callPipelineService.updateProviderStatus(
                callControlId,
                "",
                null,
                url,
                payload.path("recording_id").asText("")
        );
    }

    private void handleConversationEnded(JsonNode payload) {
        var callControlId = required(payload, "call_control_id");
        callPipelineService.completeActiveCall(callControlId, "completed");
    }

    private void handleStreamingFailed(JsonNode payload) {
        var callControlId = required(payload, "call_control_id");
        callPipelineService.completeActiveCall(callControlId, "media_failed");
        telephonyProvider.hangup(callControlId);
    }

    private String outcome(String cause) {
        return switch (cause) {
            case "normal_clearing" -> "completed";
            case "user_busy" -> "busy";
            case "timeout", "no_answer" -> "no_answer";
            case "call_rejected" -> "rejected";
            case "originator_cancel" -> "canceled";
            default -> cause == null || cause.isBlank() ? "completed" : cause.toLowerCase(java.util.Locale.ROOT);
        };
    }

    private boolean claim(String id, String type, String callControlId, OffsetDateTime occurredAt, String payload) {
        if (eventRepository.existsByProviderEventId(id)) return false;
        try {
            eventRepository.save(new TelnyxWebhookEvent(id, type, callControlId, occurredAt, payload));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private String required(JsonNode payload, String field) {
        var value = payload.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("Telnyx event is missing " + field);
        return value;
    }

    private OffsetDateTime parseTime(String value) {
        try {
            return value.isBlank() ? null : OffsetDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    @PreDestroy
    void stop() {
        for (var lane : lanes) lane.shutdown();
    }
}
