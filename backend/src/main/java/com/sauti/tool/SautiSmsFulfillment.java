package com.sauti.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SautiSmsFulfillment implements ToolFulfillment {
    private static final Logger LOGGER = LoggerFactory.getLogger(SautiSmsFulfillment.class);
    private static final String TELNYX_MESSAGES_URL = "https://api.telnyx.com/v2/messages";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final String telnyxApiKey;

    public SautiSmsFulfillment(
            ObjectMapper objectMapper,
            @Value("${sauti.telnyx.api-key:}") String telnyxApiKey
    ) {
        this.objectMapper = objectMapper;
        this.telnyxApiKey = telnyxApiKey;
    }

    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        if (telnyxApiKey == null || telnyxApiKey.isBlank()) {
            LOGGER.warn("Telnyx API key not configured; SMS not sent for callId={}", call.getId());
            return LlmToolResult.success(toolCall, Map.of("sent", false, "reason", "sms_provider_not_configured"));
        }

        var to = toolCall.arguments().getOrDefault("phone", call.getCallerNumber()).toString();
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
            var body = objectMapper.writeValueAsString(Map.of(
                    "from", from,
                    "to", to,
                    "text", text
            ));
            var request = HttpRequest.newBuilder(URI.create(TELNYX_MESSAGES_URL))
                    .header("Authorization", "Bearer " + telnyxApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.info("SMS sent via Telnyx to={} callId={}", to, call.getId());
                return LlmToolResult.success(toolCall, Map.of("sent", true, "to", to));
            }
            LOGGER.error("Telnyx SMS failed status={} body={}", response.statusCode(), response.body());
            return LlmToolResult.error(toolCall, "SMS delivery failed (HTTP " + response.statusCode() + ")");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LlmToolResult.error(toolCall, "SMS request interrupted");
        } catch (Exception exception) {
            LOGGER.error("SMS send exception callId={}", call.getId(), exception);
            return LlmToolResult.error(toolCall, "SMS send error: " + exception.getMessage());
        }
    }
}
