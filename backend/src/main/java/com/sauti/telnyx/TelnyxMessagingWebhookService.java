package com.sauti.telnyx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.billing.ProviderCostReconciliationService;
import java.time.OffsetDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "telnyx")
public class TelnyxMessagingWebhookService {
    private final ObjectMapper objectMapper;
    private final TelnyxWebhookEventRepository events;
    private final ProviderCostReconciliationService reconciliation;

    public TelnyxMessagingWebhookService(ObjectMapper objectMapper, TelnyxWebhookEventRepository events,
                                         ProviderCostReconciliationService reconciliation) {
        this.objectMapper = objectMapper;
        this.events = events;
        this.reconciliation = reconciliation;
    }

    @Transactional
    public void accept(String rawPayload) {
        try {
            var data = objectMapper.readTree(rawPayload).path("data");
            var eventId = required(data, "id");
            var eventType = required(data, "event_type");
            var payload = data.path("payload");
            var messageId = payload.path("id").asText("");
            if (!claim(eventId, eventType, messageId, time(data.path("occurred_at").asText("")))) return;
            var event = events.findByProviderEventId(eventId).orElseThrow();
            event.markProcessing();
            if ("message.finalized".equals(eventType) && "outbound".equals(payload.path("direction").asText(""))) {
                reconciliation.reconcileTelnyxFinalizedMessage(messageId, payload);
            }
            event.markCompleted();
            events.save(event);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Telnyx messaging webhook payload", exception);
        }
    }

    private boolean claim(String id, String type, String messageId, OffsetDateTime occurredAt) {
        if (events.existsByProviderEventId(id)) return false;
        try {
            events.saveAndFlush(new TelnyxWebhookEvent(id, type, messageId, occurredAt));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }

    private static String required(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("Telnyx event is missing " + field);
        return value;
    }

    private static OffsetDateTime time(String value) {
        try { return value == null || value.isBlank() ? null : OffsetDateTime.parse(value); }
        catch (Exception ignored) { return null; }
    }
}
