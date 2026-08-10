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
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "telnyx-call-control");
        thread.setDaemon(true);
        return thread;
    });

    public TelnyxCallControlService(
            ObjectMapper objectMapper,
            TelnyxWebhookEventRepository eventRepository,
            CallPipelineService callPipelineService,
            TelnyxTelephonyProvider telephonyProvider,
            CallQueryService callQueryService,
            ProviderCostReconciliationService costReconciliation,
            PilotProvisioningPolicyService provisioningPolicies,
            BillingAccessPolicy billingAccess
    ) {
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository;
        this.callPipelineService = callPipelineService;
        this.telephonyProvider = telephonyProvider;
        this.callQueryService = callQueryService;
        this.costReconciliation = costReconciliation;
        this.provisioningPolicies = provisioningPolicies;
        this.billingAccess = billingAccess;
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
            if (!claim(eventId, eventType, callControlId, occurredAt)) {
                return;
            }
            executor.execute(() -> process(eventId, eventType, payload, occurredAt));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Telnyx webhook payload", exception);
        }
    }

    private void process(String eventId, String eventType, JsonNode payload, OffsetDateTime occurredAt) {
        var event = eventRepository.findByProviderEventId(eventId).orElseThrow();
        event.markProcessing();
        eventRepository.save(event);
        try {
            switch (eventType) {
                case "call.initiated" -> handleInitiated(payload);
                case "call.answered" -> handleAnswered(payload);
                case "call.hangup" -> handleHangup(payload, occurredAt);
                case "call.recording.saved" -> handleRecording(payload);
                case "call.conversation.ended" -> handleConversationEnded(payload);
                case "streaming.failed" -> handleStreamingFailed(payload);
                default -> {
                    // Persist and acknowledge lifecycle events that require no Sauti state change.
                }
            }
            event.markCompleted();
        } catch (Exception exception) {
            LOGGER.warn("Telnyx event processing failed eventId={} type={}", eventId, eventType, exception);
            event.markFailed(exception.getMessage());
        }
        eventRepository.save(event);
    }

    private void handleInitiated(JsonNode payload) {
        var callControlId = required(payload, "call_control_id");
        var to = required(payload, "to");
        var from = payload.path("from").asText("");
        var direction = payload.path("direction").asText("incoming");
        var normalizedDirection = direction.toLowerCase(java.util.Locale.ROOT);
        if (!normalizedDirection.contains("incoming") && !normalizedDirection.contains("inbound")) {
            return;
        }
        var call = callPipelineService.startInboundCall(to, callControlId, from);
        try { billingAccess.requirePaidCommunication(call.getTenant().getId()); }
        catch (RuntimeException blocked) { telephonyProvider.hangup(callControlId); throw blocked; }
        try { provisioningPolicies.authorize(call.getTenant().getId(), "live_calling"); }
        catch (IllegalStateException blocked) { telephonyProvider.hangup(callControlId); throw blocked; }
        var greeting = callQueryService.firstAgentResponse(call.getTenant().getId(), call.getId());
        telephonyProvider.answerInboundCall(call, callControlId, greeting);
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

    private boolean claim(String id, String type, String callControlId, OffsetDateTime occurredAt) {
        if (eventRepository.existsByProviderEventId(id)) return false;
        try {
            eventRepository.save(new TelnyxWebhookEvent(id, type, callControlId, occurredAt));
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
        executor.shutdown();
    }
}
