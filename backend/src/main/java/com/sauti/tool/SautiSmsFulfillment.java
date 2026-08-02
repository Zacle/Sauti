package com.sauti.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.billing.CommunicationUsageMeteringService;
import com.sauti.call.Call;
import com.sauti.integration.MessagingRecipientResolver;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SautiSmsFulfillment implements ToolFulfillment {
    private static final Logger LOGGER = LoggerFactory.getLogger(SautiSmsFulfillment.class);
    private static final String TELNYX_MESSAGES_URL = "https://api.telnyx.com/v2/messages";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper;
    private final MessagingRecipientResolver recipients;
    private final String telnyxApiKey;
    private final String messagingProfileId;
    private final CommunicationUsageMeteringService usageMetering;

    public SautiSmsFulfillment(
            ObjectMapper objectMapper,
            MessagingRecipientResolver recipients,
            CommunicationUsageMeteringService usageMetering,
            @Value("${sauti.telnyx.api-key:}") String telnyxApiKey,
            @Value("${sauti.telnyx.messaging-profile-id:}") String messagingProfileId
    ) {
        this.objectMapper = objectMapper;
        this.recipients = recipients;
        this.usageMetering = usageMetering;
        this.telnyxApiKey = telnyxApiKey;
        this.messagingProfileId = messagingProfileId == null ? "" : messagingProfileId.trim();
    }

    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        if (telnyxApiKey == null || telnyxApiKey.isBlank()) {
            LOGGER.warn("Telnyx API key not configured; SMS not sent for callId={}", call.getId());
            return LlmToolResult.success(toolCall, Map.of("sent", false, "reason", "sms_provider_not_configured"));
        }

        MessagingRecipientResolver.Recipient recipient;
        try {
            recipient = recipients.resolve(call, toolCall.arguments().get("phone"));
        } catch (IllegalArgumentException exception) {
            return LlmToolResult.error(toolCall, exception.getMessage());
        }
        var text = toolCall.arguments().getOrDefault("message", "").toString();
        var from = call.getAgent().getTwilioPhoneNumber();

        if (from == null || from.isBlank()) {
            LOGGER.warn("Agent has no provisioned number; SMS not sent for callId={}", call.getId());
            return LlmToolResult.success(toolCall, Map.of("sent", false, "reason", "agent_has_no_number"));
        }
        if (text.isBlank()) {
            return LlmToolResult.error(toolCall, "SMS message text is required");
        }

        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("from", from);
            payload.put("to", recipient.e164());
            payload.put("text", text);
            if (!messagingProfileId.isBlank()) payload.put("messaging_profile_id", messagingProfileId);
            var body = objectMapper.writeValueAsString(payload);
            var request = HttpRequest.newBuilder(URI.create(TELNYX_MESSAGES_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + telnyxApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                var providerMessageId = objectMapper.readTree(response.body()).path("data").path("id").asText("");
                usageMetering.meterOutboundMessage(
                        call.getTenant().getId(), call.getAgent().getId(), "sms", providerMessageId,
                        call.getId() + ":" + toolCall.id(), "text");
                LOGGER.info("SMS queued via Telnyx recipient={} callId={}", recipient.masked(), call.getId());
                return LlmToolResult.success(toolCall, Map.of(
                        "queued", true, "destination", recipient.masked(), "recipientSource", recipient.source(),
                        "providerMessageId", providerMessageId));
            }
            LOGGER.error("Telnyx SMS failed status={} callId={}", response.statusCode(), call.getId());
            return LlmToolResult.error(toolCall, "SMS delivery failed (HTTP " + response.statusCode() + ")");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LlmToolResult.error(toolCall, "SMS request interrupted");
        } catch (Exception exception) {
            LOGGER.error("SMS send exception callId={}", call.getId(), exception);
            return LlmToolResult.error(toolCall, "SMS could not be queued");
        }
    }
}
