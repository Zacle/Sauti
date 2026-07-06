package com.sauti.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.calendar.provider", havingValue = "webhook")
public class WebhookCalendarProvider implements CalendarProvider {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final String webhookSecret;

    public WebhookCalendarProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.calendar.webhook.url}") String webhookUrl,
            @Value("${sauti.calendar.webhook.secret:}") String webhookSecret
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public List<CalendarAvailabilitySlot> availability(Agent agent, LocalDate date, int durationMinutes, ZoneId timezone) {
        throw new UnsupportedOperationException("Webhook calendar availability is not implemented for global calendar provider mode");
    }

    @Override
    public CalendarSyncResult createEvent(Booking booking) {
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "bookingId", booking.getId(),
                    "tenantId", booking.getTenant().getId(),
                    "agentId", booking.getAgent().getId(),
                    "callerName", booking.getCallerName(),
                    "callerPhone", booking.getCallerPhone(),
                    "serviceType", booking.getServiceType(),
                    "appointmentAt", booking.getAppointmentAt().toString()
            ));
            var requestBuilder = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!webhookSecret.isBlank()) {
                sign(requestBuilder, body);
            }
            var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Calendar webhook failed with status " + response.statusCode());
            }
            var externalEventId = objectMapper.readTree(response.body()).path("externalEventId").asText();
            return new CalendarSyncResult(externalEventId.isBlank() ? "webhook-" + booking.getId() : externalEventId);
        } catch (IOException exception) {
            throw new IllegalStateException("Calendar webhook request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Calendar webhook request was interrupted", exception);
        }
    }

    private void sign(HttpRequest.Builder requestBuilder, String body) {
        var timestamp = Long.toString(Instant.now().getEpochSecond());
        requestBuilder
                .header("X-Sauti-Timestamp", timestamp)
                .header("X-Sauti-Signature", "sha256=" + hmac(timestamp + "." + body));
    }

    private String hmac(String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign calendar webhook request", exception);
        }
    }
}
