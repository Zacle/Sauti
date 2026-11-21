package com.sauti.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Calls an owner's own HTTPS endpoint. The caller is waiting on the line, so the request has hard
 * time limits, and each delivery carries an {@code Idempotency-Key} that stays the same when the voice
 * provider repeats a tool call, so the owner's system can ignore the repeat.
 */
@Component
public class WebhookToolFulfillment implements ToolFulfillment {
    static final java.time.Duration CONNECT_TIMEOUT = java.time.Duration.ofSeconds(5);
    /** Below the voice provider's own tool limit, so Sauti answers before the provider gives up. */
    static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(12);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper objectMapper;
    private final CredentialEncryption credentialEncryption;
    private final WebhookDestinationValidator webhookDestinationValidator;

    public WebhookToolFulfillment(
            ObjectMapper objectMapper,
            CredentialEncryption credentialEncryption,
            WebhookDestinationValidator webhookDestinationValidator
    ) {
        this.objectMapper = objectMapper;
        this.credentialEncryption = credentialEncryption;
        this.webhookDestinationValidator = webhookDestinationValidator;
    }

    @Override
    public LlmToolResult execute(Call call, AgentTool toolConfig, LlmToolCall toolCall) {
        try {
            var idempotencyKey = idempotencyKey(call, toolCall);
            var body = objectMapper.writeValueAsString(Map.of(
                    "toolName", toolCall.name(),
                    "idempotencyKey", idempotencyKey,
                    "callId", call.getId(),
                    "agentId", call.getAgent().getId(),
                    "tenantId", call.getTenant().getId(),
                    "callerPhone", call.getCallerNumber(),
                    "arguments", toolCall.arguments(),
                    "timestamp", Instant.now().toString()
            ));
            var request = request(toolConfig, body, idempotencyKey);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(toolCall, response);
        } catch (java.net.http.HttpTimeoutException exception) {
            return LlmToolResult.error(toolCall, "The business system did not answer in time");
        } catch (IOException exception) {
            return LlmToolResult.error(toolCall, "Webhook request failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return LlmToolResult.error(toolCall, "Webhook request was interrupted");
        } catch (RuntimeException exception) {
            return LlmToolResult.error(toolCall, exception.getMessage());
        }
    }

    /**
     * The same for every delivery of one provider tool call; new for each call Sauti's own model loop
     * makes, because that loop can reuse its ids across turns.
     */
    static String idempotencyKey(Call call, LlmToolCall toolCall) {
        var id = toolCall.id() == null ? "" : toolCall.id().trim();
        if (!ToolActionLog.providerDelivered(id)) return "sauti-" + java.util.UUID.randomUUID();
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((call.getId() + ":" + id).getBytes(StandardCharsets.UTF_8));
            return "sauti-" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private HttpRequest request(AgentTool toolConfig, String body, String idempotencyKey) {
        var uri = URI.create(toolConfig.getWebhookUrl());
        webhookDestinationValidator.validatePublicHost(uri.getHost());
        var builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey);
        if ("GET".equalsIgnoreCase(toolConfig.getWebhookMethod())) {
            builder.GET();
        } else {
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        }
        var secret = toolConfig.getAuthCredential() == null || toolConfig.getAuthCredential().isBlank()
                ? ""
                : credentialEncryption.decrypt(toolConfig.getAuthCredential());
        if ("bearer".equals(toolConfig.getAuthType()) && !secret.isBlank()) {
            builder.header("Authorization", "Bearer " + secret);
        } else if ("api_key".equals(toolConfig.getAuthType()) && !secret.isBlank()) {
            builder.header(toolConfig.getAuthHeaderName(), secret);
        } else if ("hmac_sha256".equals(toolConfig.getAuthType()) && !secret.isBlank()) {
            var timestamp = Long.toString(Instant.now().getEpochSecond());
            builder.header("X-Sauti-Timestamp", timestamp);
            builder.header("X-Sauti-Signature", "sha256=" + hmac(secret, timestamp + "." + body));
        }
        return builder.build();
    }

    private LlmToolResult parse(LlmToolCall toolCall, HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return LlmToolResult.error(toolCall, "Webhook returned HTTP " + response.statusCode());
        }
        var node = objectMapper.readTree(response.body());
        if (!node.path("success").asBoolean(true)) {
            return LlmToolResult.error(toolCall, node.path("error").asText("Webhook reported failure"));
        }
        var data = new HashMap<String, Object>();
        var dataNode = node.has("data") ? node.path("data") : node;
        dataNode.fields().forEachRemaining(entry -> data.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
        return LlmToolResult.success(toolCall, data);
    }

    private String hmac(String secret, String payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign webhook request", exception);
        }
    }
}
