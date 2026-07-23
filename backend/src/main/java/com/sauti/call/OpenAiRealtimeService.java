package com.sauti.call;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.llm.ConversationOrchestrator;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolResult;
import com.sauti.tool.AgentToolLoader;
import com.sauti.tool.ToolFulfillmentRouter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiRealtimeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiRealtimeService.class);
    public static final String VOICE_PREFIX = "openai:";
    private final ObjectMapper objectMapper;
    private final ConversationOrchestrator conversationOrchestrator;
    private final AgentToolLoader agentToolLoader;
    private final ToolFulfillmentRouter toolRouter;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String callsUrl;
    private final String model;
    private final String transcriptionModel;
    private final int maxOutputTokens;
    private final int contextTokenLimit;
    private final double contextRetentionRatio;

    public OpenAiRealtimeService(
            ObjectMapper objectMapper,
            ConversationOrchestrator conversationOrchestrator,
            AgentToolLoader agentToolLoader,
            ToolFulfillmentRouter toolRouter,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${sauti.realtime.openai.calls-url:https://api.openai.com/v1/realtime/calls}") String callsUrl,
            @Value("${sauti.realtime.openai.model:gpt-realtime-1.5}") String model,
            @Value("${sauti.realtime.openai.transcription-model:gpt-4o-mini-transcribe}") String transcriptionModel,
            @Value("${sauti.realtime.openai.max-output-tokens:512}") int maxOutputTokens,
            @Value("${sauti.realtime.openai.context-token-limit:1000}") int contextTokenLimit,
            @Value("${sauti.realtime.openai.context-retention-ratio:0.8}") double contextRetentionRatio
    ) {
        this.objectMapper = objectMapper;
        this.conversationOrchestrator = conversationOrchestrator;
        this.agentToolLoader = agentToolLoader;
        this.toolRouter = toolRouter;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.callsUrl = callsUrl;
        this.model = model;
        this.transcriptionModel = transcriptionModel;
        this.maxOutputTokens = Math.max(64, Math.min(4096, maxOutputTokens));
        this.contextTokenLimit = Math.max(500, Math.min(16_000, contextTokenLimit));
        this.contextRetentionRatio = Math.max(0.5, Math.min(0.95, contextRetentionRatio));
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public boolean enabled() {
        return !apiKey.isBlank();
    }

    public boolean usesOpenAiVoice(Call call) {
        return call.getAgent().getTtsVoiceId() != null
                && call.getAgent().getTtsVoiceId().startsWith(VOICE_PREFIX);
    }

    public boolean usesCartesiaVoice(Call call) {
        return call.getAgent().getTtsVoiceId() != null
                && call.getAgent().getTtsVoiceId().startsWith(CartesiaRealtimeTextToSpeechClient.VOICE_PREFIX);
    }

    public boolean hasTool(Call call, String toolName) {
        if (call == null || toolName == null || toolName.isBlank()) return false;
        return agentToolLoader.loadForAgent(call.getAgent().getId()).stream()
                .anyMatch(tool -> toolName.equals(tool.name()));
    }

    public String createWebRtcSession(Call call, String sdpOffer) {
        if (!enabled()) throw new IllegalStateException("OpenAI Realtime is not configured");
        if (!usesOpenAiVoice(call) && !usesCartesiaVoice(call)) {
            throw new IllegalArgumentException("This call does not use a Realtime-compatible voice");
        }
        if (sdpOffer == null || sdpOffer.isBlank()) throw new IllegalArgumentException("A WebRTC SDP offer is required");

        try {
            var session = sessionConfiguration(call, false);
            var boundary = "----SautiRealtime" + UUID.randomUUID().toString().replace("-", "");
            var body = multipart(boundary, sdpOffer, objectMapper.writeValueAsString(session));
            var request = HttpRequest.newBuilder(URI.create(callsUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI Realtime session failed with status " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI Realtime session creation was interrupted", exception);
        } catch (Exception exception) {
            if (exception instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("Unable to create the OpenAI Realtime session", exception);
        }
    }

    public LlmToolResult executeTool(Call call, String callId, String name, String argumentsJson) {
        try {
            Map<String, Object> arguments = argumentsJson == null || argumentsJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(argumentsJson, new TypeReference<>() { });
            var toolCall = new LlmToolCall(callId, name, arguments);
            var result = toolRouter.route(call, toolCall);
            if (!result.success()) {
                LOGGER.warn("Realtime tool failed callId={} tool={} reason={}", call.getId(), name, result.error());
            }
            return result;
        } catch (Exception exception) {
            LOGGER.warn("Realtime tool execution failed callId={} tool={}", call.getId(), name, exception);
            return new LlmToolResult(callId, name, false, Map.of(), "The requested action could not be completed");
        }
    }

    Map<String, Object> telephonySessionConfiguration(Call call) {
        return sessionConfiguration(call, true);
    }

    private Map<String, Object> sessionConfiguration(Call call, boolean telephony) {
        // All model output is text-first. Native provider audio begins playback
        // before the parallel transcript can be validated, so it cannot enforce
        // the speech/tool boundary required by Sauti.
        var input = new LinkedHashMap<String, Object>();
        input.put("noise_reduction", Map.of("type", "near_field"));
        if (telephony) {
            input.put("format", Map.of("type", "audio/pcm", "rate", 24000));
        }
        input.put("transcription", Map.of(
                "model", transcriptionModel,
                "language", call.getLanguageDetected() == null ? call.getAgent().getDefaultLanguage() : call.getLanguageDetected()
        ));
        var telephonyThreshold = Math.round(Math.max(0.60, Math.min(0.90,
                0.83 - (0.4 * call.getAgent().getBargeInSensitivity()))) * 100.0) / 100.0;
        var telephonySilenceMs = Math.max(250, Math.min(900, call.getAgent().getSttEndpointingMs()));
        input.put("turn_detection", Map.of(
                "type", "server_vad",
                // Cartesia playback is captured outside OpenAI's native output
                // channel. Use a slightly stricter and more patient endpoint in
                // hybrid mode so playback leakage and natural mid-sentence pauses
                // do not create a new turn prematurely.
                "threshold", telephony ? telephonyThreshold : 0.60,
                "prefix_padding_ms", 250,
                "silence_duration_ms", telephony ? telephonySilenceMs : 520,
                // A response is requested only after a non-empty final caller
                // transcript is accepted. Provider-managed responses could run
                // on noise/empty VAD turns and make the agent speak twice.
                "create_response", false,
                // Sauti validates sustained caller speech before cancelling
                // either native OpenAI audio or external Cartesia playback.
                "interrupt_response", false
        ));

        var loadedTools = agentToolLoader.loadForAgent(call.getAgent().getId());
        if (call.getAgent().isBookingEnabled()) {
            var activeNames = loadedTools.stream().map(com.sauti.llm.LlmToolDefinition::name).toList();
            if (!activeNames.contains("check_availability") || !activeNames.contains("book_slot")) {
                LOGGER.warn(
                        "Booking agent missing realtime tools callId={} agentId={} activeTools={}",
                        call.getId(), call.getAgent().getId(), activeNames
                );
            }
        }
        var tools = loadedTools.stream()
                .map(tool -> {
                    var definition = new LinkedHashMap<String, Object>();
                    definition.put("type", "function");
                    definition.put("name", tool.name());
                    definition.put("description", tool.description() == null ? "" : tool.description());
                    definition.put("parameters", tool.inputSchema() == null ? Map.of("type", "object") : tool.inputSchema());
                    return definition;
                })
                .toList();
        var session = new LinkedHashMap<String, Object>();
        session.put("type", "realtime");
        session.put("model", model);
        session.put("instructions", conversationOrchestrator.realtimeInstructions(call, call.getLanguageDetected()));
        session.put("output_modalities", List.of("text"));
        // Sauti requests text only and synthesizes it after validation. A finite
        // budget is therefore safe for normal voice replies and tool arguments,
        // and avoids reserving the model's maximum output allowance on every
        // turn in a long-lived Realtime session.
        session.put("max_output_tokens", maxOutputTokens);
        // Keep the current server-owned state and recent exchange while
        // preventing every long call from resending its complete transcript.
        // This directly bounds rolling TPM consumption; max_output_tokens does
        // not limit input/history tokens.
        session.put("truncation", Map.of(
                "type", "retention_ratio",
                "retention_ratio", contextRetentionRatio,
                "token_limits", Map.of("post_instructions", contextTokenLimit)
        ));
        // OpenAI supplies audio understanding and VAD. Validated response text
        // is synthesized only after the client has accepted a message item.
        session.put("audio", Map.of("input", input));
        if (!tools.isEmpty()) {
            session.put("tools", tools);
            session.put("tool_choice", "auto");
            // Voice turns must resolve tools in a deterministic order. Parallel
            // calls can race side effects and produce more than one caller-facing
            // follow-up for a single utterance.
            session.put("parallel_tool_calls", false);
        }
        return session;
    }

    private byte[] multipart(String boundary, String sdp, String sessionJson) {
        var separator = "--" + boundary + "\r\n";
        var body = separator
                + "Content-Disposition: form-data; name=\"sdp\"\r\n"
                + "Content-Type: application/sdp\r\n\r\n"
                + sdp + "\r\n"
                + separator
                + "Content-Disposition: form-data; name=\"session\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + sessionJson + "\r\n"
                + "--" + boundary + "--\r\n";
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
