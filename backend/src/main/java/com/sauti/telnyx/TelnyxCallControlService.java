package com.sauti.telnyx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.TelnyxTelephonyProvider;
import com.sauti.call.CallPipelineService;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "telnyx")
public class TelnyxCallControlService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelnyxCallControlService.class);
    private final ObjectMapper objectMapper;
    private final TelnyxWebhookEventRepository eventRepository;
    private final CallPipelineService callPipelineService;
    private final TelnyxTelephonyProvider telephonyProvider;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "telnyx-call-control");
        thread.setDaemon(true);
        return thread;
    });

    public TelnyxCallControlService(
            ObjectMapper objectMapper,
            TelnyxWebhookEventRepository eventRepository,
            CallPipelineService callPipelineService,
            TelnyxTelephonyProvider telephonyProvider
    ) {
        this.objectMapper = objectMapper;
        this.eventRepository = eventRepository;
        this.callPipelineService = callPipelineService;
        this.telephonyProvider = telephonyProvider;
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
            if (!claim(eventId, eventType, callControlId, parseTime(data.path("occurred_at").asText("")))) {
                return;
            }
            executor.execute(() -> process(eventId, eventType, payload));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Telnyx webhook payload", exception);
        }
    }

    private void process(String eventId, String eventType, JsonNode payload) {
        var event = eventRepository.findByProviderEventId(eventId).orElseThrow();
        event.markProcessing();
        eventRepository.save(event);
        try {
            switch (eventType) {
                case "call.initiated" -> handleInitiated(payload);
                case "call.hangup" -> handleHangup(payload);
                case "call.recording.saved" -> handleRecording(payload);
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
        telephonyProvider.answerInboundCall(call, callControlId);
    }

    private void handleHangup(JsonNode payload) {
        var callControlId = required(payload, "call_control_id");
        callPipelineService.completeActiveCall(callControlId, outcome(payload.path("hangup_cause").asText("")));
    }

    private void handleRecording(JsonNode payload) {
        var callControlId = required(payload, "call_control_id");
        var urls = payload.path("recording_urls");
        var url = urls.path("mp3").asText(urls.path("wav").asText(""));
        callPipelineService.updateTwilioStatus(
                callControlId,
                "",
                null,
                url,
                payload.path("recording_id").asText("")
        );
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
