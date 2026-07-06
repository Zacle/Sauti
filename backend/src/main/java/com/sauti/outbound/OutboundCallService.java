package com.sauti.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.calendar.Booking;
import jakarta.persistence.EntityNotFoundException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboundCallService {
    private final ScheduledCallRepository scheduledCallRepository;
    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String accountSid;
    private final String authToken;
    private final String publicBaseUrl;
    private final String defaultFromNumber;

    public OutboundCallService(
            ScheduledCallRepository scheduledCallRepository,
            AgentRepository agentRepository,
            ObjectMapper objectMapper,
            @Value("${sauti.twilio.outbound.enabled}") boolean enabled,
            @Value("${sauti.twilio.account-sid}") String accountSid,
            @Value("${sauti.twilio.auth-token}") String authToken,
            @Value("${sauti.twilio.public-base-url}") String publicBaseUrl,
            @Value("${sauti.twilio.outbound.from-number}") String defaultFromNumber
    ) {
        this.scheduledCallRepository = scheduledCallRepository;
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.enabled = enabled;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.publicBaseUrl = publicBaseUrl;
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

    @Scheduled(fixedDelayString = "${sauti.twilio.outbound.poll-delay-ms}")
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
            var response = httpClient.send(twilioRequest(scheduledCall), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                scheduledCall.markInitiated(extractSid(response.body()));
                return;
            }
            scheduledCall.markFailed("Twilio returned HTTP " + response.statusCode());
        } catch (Exception exception) {
            scheduledCall.markFailed(exception.getMessage());
        }
    }

    private HttpRequest twilioRequest(ScheduledCall scheduledCall) {
        var from = scheduledCall.getAgent().getTwilioPhoneNumber();
        if (from == null || from.isBlank()) {
            from = defaultFromNumber;
        }
        var body = "To=" + encode(scheduledCall.getTargetPhone())
                + "&From=" + encode(from)
                + "&Url=" + encode(publicBaseUrl + "/webhooks/twilio/voice");
        return HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Calls.json"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String extractSid(String body) {
        try {
            return body == null ? "" : objectMapper.readTree(body).path("sid").asText("");
        } catch (Exception exception) {
            return "";
        }
    }
}
