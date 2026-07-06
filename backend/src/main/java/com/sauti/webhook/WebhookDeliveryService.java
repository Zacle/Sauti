package com.sauti.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.calendar.Booking;
import com.sauti.call.Call;
import com.sauti.tenant.Tenant;
import com.sauti.tool.WebhookDestinationValidator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookDeliveryService {
    private final WebhookDeliveryRepository repository;
    private final ObjectMapper objectMapper;
    private final WebhookDestinationValidator destinationValidator;
    private final HttpClient httpClient;
    private final String fallbackSigningSecret;
    private final int maxAttempts;
    private final int retryWindowHours;

    public WebhookDeliveryService(
            WebhookDeliveryRepository repository,
            ObjectMapper objectMapper,
            WebhookDestinationValidator destinationValidator,
            @Value("${sauti.webhooks.signing-secret}") String fallbackSigningSecret,
            @Value("${sauti.webhooks.max-attempts}") int maxAttempts,
            @Value("${sauti.webhooks.retry-window-hours}") int retryWindowHours
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.destinationValidator = destinationValidator;
        this.httpClient = HttpClient.newHttpClient();
        this.fallbackSigningSecret = fallbackSigningSecret;
        this.maxAttempts = maxAttempts;
        this.retryWindowHours = retryWindowHours;
    }

    @Transactional
    public void bookingCreated(Booking booking) {
        enqueue(booking.getTenant(), "booking.created", bookingPayload(booking));
    }

    @Transactional
    public void bookingCancelled(Booking booking) {
        enqueue(booking.getTenant(), "booking.cancelled", bookingPayload(booking));
    }

    @Transactional
    public void callCompleted(Call call) {
        enqueue(call.getTenant(), "call.completed", callPayload(call, "call.completed"));
    }

    @Transactional
    public void callAnalysed(Call call) {
        enqueue(call.getTenant(), "call.analysed", callPayload(call, "call.analysed"));
    }

    @Transactional
    public void enqueue(Tenant tenant, String eventType, Map<String, Object> payload) {
        if (tenant.getWebhookUrl() == null || tenant.getWebhookUrl().isBlank()) {
            return;
        }
        repository.save(new WebhookDelivery(tenant, eventType, serialize(payload), tenant.getWebhookUrl()));
    }

    @Scheduled(fixedDelayString = "${sauti.webhooks.retry-delay-ms}")
    @Transactional
    public void deliverDue() {
        var now = OffsetDateTime.now();
        repository.findTop50BySuccessFalseAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(now)
                .forEach(delivery -> deliver(delivery, now));
    }

    private void deliver(WebhookDelivery delivery, OffsetDateTime now) {
        try {
            var uri = URI.create(delivery.getEndpointUrl());
            destinationValidator.validatePublicHost(uri.getHost());
            var response = httpClient.send(request(delivery), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.markSuccess(response.statusCode());
                return;
            }
            delivery.markFailure(response.statusCode(), "HTTP " + response.statusCode(), nextAttempt(delivery, now));
        } catch (Exception exception) {
            delivery.markFailure(null, exception.getMessage(), nextAttempt(delivery, now));
        }
    }

    private HttpRequest request(WebhookDelivery delivery) {
        var secret = delivery.getTenant().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            secret = fallbackSigningSecret;
        }
        return HttpRequest.newBuilder(URI.create(delivery.getEndpointUrl()))
                .header("Content-Type", "application/json")
                .header("X-Sauti-Event", delivery.getEventType())
                .header("X-Sauti-Delivery", delivery.getId().toString())
                .header("X-Sauti-Signature", signature(delivery.getPayloadJson(), secret))
                .POST(HttpRequest.BodyPublishers.ofString(delivery.getPayloadJson()))
                .build();
    }

    private OffsetDateTime nextAttempt(WebhookDelivery delivery, OffsetDateTime now) {
        if (delivery.getAttemptCount() + 1 >= maxAttempts) {
            return null;
        }
        if (delivery.getCreatedAt() != null && delivery.getCreatedAt().plusHours(retryWindowHours).isBefore(now)) {
            return null;
        }
        long delayMinutes = Math.min(60, 1L << Math.min(6, delivery.getAttemptCount()));
        return now.plusMinutes(delayMinutes);
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Webhook payload is not serializable", exception);
        }
    }

    private String signature(String payload, String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign webhook payload", exception);
        }
    }

    private Map<String, Object> bookingPayload(Booking booking) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("bookingId", booking.getId());
        payload.put("tenantId", booking.getTenant().getId());
        payload.put("agentId", booking.getAgent().getId());
        payload.put("callId", booking.getCall() == null ? null : booking.getCall().getId());
        payload.put("callerName", booking.getCallerName());
        payload.put("callerPhone", booking.getCallerPhone());
        payload.put("serviceType", booking.getServiceType());
        payload.put("bookedAt", booking.getBookedAt());
        payload.put("appointmentAt", booking.getAppointmentAt());
        payload.put("externalEventId", booking.getExternalEventId());
        payload.put("status", booking.getStatus());
        return payload;
    }

    private Map<String, Object> callPayload(Call call, String event) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("event", event);
        payload.put("test", "test".equals(call.getDirection()));
        payload.put("callId", call.getId());
        payload.put("tenantId", call.getTenant().getId());
        payload.put("agentId", call.getAgent().getId());
        payload.put("callerPhone", call.getCallerNumber());
        payload.put("direction", call.getDirection());
        payload.put("outcome", call.getOutcome());
        payload.put("summary", call.getCallSummary());
        payload.put("sentiment", call.getSentiment());
        payload.put("intent", call.getIntent());
        payload.put("recordingUrl", call.getRecordingUrl());
        payload.put("startedAt", call.getStartedAt());
        payload.put("endedAt", call.getEndedAt());
        return payload;
    }
}
