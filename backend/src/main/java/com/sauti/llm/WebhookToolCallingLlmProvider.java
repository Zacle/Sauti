package com.sauti.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sauti.llm.provider", havingValue = "webhook")
public class WebhookToolCallingLlmProvider implements LlmToolCallingProvider {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;
    private final String webhookSecret;
    private final String defaultModel;
    private final String advancedModel;

    public WebhookToolCallingLlmProvider(
            ObjectMapper objectMapper,
            @Value("${sauti.llm.webhook.url}") String webhookUrl,
            @Value("${sauti.llm.webhook.secret:}") String webhookSecret,
            @Value("${sauti.llm.default-model:}") String defaultModel,
            @Value("${sauti.llm.advanced-model:}") String advancedModel
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
        this.webhookSecret = webhookSecret;
        this.defaultModel = defaultModel;
        this.advancedModel = advancedModel;
    }

    @Override
    public LlmToolTurnResponse completeTurn(LlmToolTurnContext context) {
        String selectedModel = "advanced".equals(context.agent().llmTier()) && !advancedModel.isBlank()
                ? advancedModel
                : defaultModel;
        return request(context, selectedModel);
    }

    private LlmToolTurnResponse request(LlmToolTurnContext context, String model) {
        try {
            var body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "agent", Map.of(
                            "id", context.agent().id(),
                            "name", context.agent().name(),
                            "systemPrompt", context.systemPrompt(),
                            "bookingEnabled", context.agent().bookingEnabled(),
                            "timezone", context.agent().timezone()
                    ),
                    "language", context.language(),
                    "messages", context.messages(),
                    "callerTranscript", context.callerTranscript(),
                    "callerPhone", context.callerPhone(),
                    "callId", context.callId(),
                    "callSid", context.callSid(),
                    "tools", context.tools(),
                    "toolResults", context.toolResults()
            ));
            var requestBuilder = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (!webhookSecret.isBlank()) {
                sign(requestBuilder, body);
            }
            var response = httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI tool webhook failed with status " + response.statusCode());
            }
            return parse(response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("AI tool webhook request failed", exception);
        } catch (CompletionException exception) {
            throw new IllegalStateException("AI tool webhook request failed or timed out", exception.getCause());
        }
    }

    private LlmToolTurnResponse parse(String body) throws IOException {
        var node = objectMapper.readTree(body);
        var toolCalls = new ArrayList<LlmToolCall>();
        for (var toolNode : node.withArray("toolCalls")) {
            var arguments = new HashMap<String, Object>();
            toolNode.path("arguments").fields().forEachRemaining(entry -> arguments.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class)));
            toolCalls.add(new LlmToolCall(
                    toolNode.path("id").asText(toolNode.path("name").asText()),
                    toolNode.path("name").asText(),
                    arguments
            ));
        }
        return new LlmToolTurnResponse(node.path("responseText").asText(""), toolCalls);
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
            throw new IllegalStateException("Could not sign AI webhook request", exception);
        }
    }
}
