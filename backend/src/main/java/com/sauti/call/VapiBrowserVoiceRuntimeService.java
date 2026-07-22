package com.sauti.call;

import com.sauti.llm.ConversationOrchestrator;
import com.sauti.tool.AgentToolLoader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VapiBrowserVoiceRuntimeService implements BrowserVoiceRuntimeProvider {
    private final ConversationOrchestrator conversationOrchestrator;
    private final AgentToolLoader agentToolLoader;
    private final String apiKey;
    private final String publicBaseUrl;
    private final String modelProvider;
    private final String model;
    private final String transcriberProvider;
    private final String transcriberModel;
    private final String transcriberLanguage;
    private final String voiceProvider;
    private final String voiceId;
    private final int voiceVersion;
    private final String voiceLanguage;
    private final int toolTimeoutSeconds;
    private final int delayedMessageMs;
    private final Map<String, PendingWebCall> pendingWebCalls = new ConcurrentHashMap<>();

    public VapiBrowserVoiceRuntimeService(
            ConversationOrchestrator conversationOrchestrator,
            AgentToolLoader agentToolLoader,
            @Value("${sauti.vapi.api-key:}") String apiKey,
            @Value("${sauti.vapi.public-base-url:${sauti.telephony.public-base-url:http://localhost:8080}}") String publicBaseUrl,
            @Value("${sauti.vapi.model-provider:openai}") String modelProvider,
            @Value("${sauti.vapi.model:gpt-4.1-mini}") String model,
            @Value("${sauti.vapi.transcriber-provider:deepgram}") String transcriberProvider,
            @Value("${sauti.vapi.transcriber-model:nova-3}") String transcriberModel,
            @Value("${sauti.vapi.transcriber-language:multi}") String transcriberLanguage,
            @Value("${sauti.vapi.voice-provider:vapi}") String voiceProvider,
            @Value("${sauti.vapi.voice-id:Savannah}") String voiceId,
            @Value("${sauti.vapi.voice-version:2}") int voiceVersion,
            @Value("${sauti.vapi.voice-language:auto}") String voiceLanguage,
            @Value("${sauti.vapi.tool-timeout-seconds:30}") int toolTimeoutSeconds,
            @Value("${sauti.vapi.delayed-message-ms:1600}") int delayedMessageMs
    ) {
        this.conversationOrchestrator = conversationOrchestrator;
        this.agentToolLoader = agentToolLoader;
        this.apiKey = trim(apiKey);
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.modelProvider = trim(modelProvider);
        this.model = trim(model);
        this.transcriberProvider = trim(transcriberProvider);
        this.transcriberModel = trim(transcriberModel);
        this.transcriberLanguage = trim(transcriberLanguage);
        this.voiceProvider = trim(voiceProvider);
        this.voiceId = trim(voiceId);
        this.voiceVersion = voiceVersion;
        this.voiceLanguage = trim(voiceLanguage);
        this.toolTimeoutSeconds = Math.max(1, Math.min(300, toolTimeoutSeconds));
        this.delayedMessageMs = Math.max(100, Math.min(120_000, delayedMessageMs));
    }

    @Override
    public String provider() {
        return "vapi";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String apiKey() {
        if (!isConfigured()) throw new IllegalStateException("Vapi is not configured. Set VAPI_API_KEY.");
        return apiKey;
    }

    @Override
    public BrowserVoiceRuntimeSession prepare(Call call, String greeting, String callbackToken) {
        if (!isConfigured()) throw new IllegalStateException("Vapi is not configured. Set VAPI_API_KEY.");
        var callbackUrl = callbackUrl(call.getTwilioCallSid(), callbackToken);
        var callbackServer = Map.<String, Object>of(
                "url", callbackUrl,
                "timeoutSeconds", toolTimeoutSeconds
        );
        var tools = agentToolLoader.loadForAgent(call.getAgent().getId()).stream()
                .map(tool -> Map.<String, Object>of(
                        "type", "function",
                        "async", false,
                        "function", Map.of(
                                "name", tool.name(),
                                "description", tool.description() == null ? "" : tool.description(),
                                "parameters", tool.inputSchema() == null ? Map.of("type", "object") : tool.inputSchema()
                        ),
                        "server", callbackServer,
                        // Vapi supplies and translates the wording. Fast tools
                        // return normally; delayed tools receive one apology
                        // without Sauti maintaining language-specific phrases.
                        "messages", List.of(Map.of(
                                "type", "request-response-delayed",
                                "timingMilliseconds", delayedMessageMs
                        ))
                ))
                .toList();

        var language = call.getLanguageDetected() == null
                ? call.getAgent().getDefaultLanguage()
                : call.getLanguageDetected();
        var modelConfig = new LinkedHashMap<String, Object>();
        modelConfig.put("provider", modelProvider);
        modelConfig.put("model", model);
        modelConfig.put("temperature", 0.2);
        modelConfig.put("maxTokens", 500);
        modelConfig.put("messages", List.of(Map.of(
                "role", "system",
                "content", conversationOrchestrator.realtimeInstructions(call, language)
        )));
        if (!tools.isEmpty()) modelConfig.put("tools", tools);

        var transcriber = new LinkedHashMap<String, Object>();
        transcriber.put("provider", transcriberProvider);
        transcriber.put("model", transcriberModel);
        transcriber.put("language", transcriberLanguage);
        if ("deepgram".equalsIgnoreCase(transcriberProvider)) {
            transcriber.put("smartFormat", true);
            transcriber.put("numerals", true);
            transcriber.put("endpointing", Math.max(10, Math.min(500, call.getAgent().getSttEndpointingMs())));
        }

        var voice = new LinkedHashMap<String, Object>();
        voice.put("provider", voiceProvider);
        voice.put("voiceId", voiceId);
        if ("vapi".equalsIgnoreCase(voiceProvider)) {
            voice.put("version", voiceVersion);
            voice.put("language", voiceLanguage.isBlank() ? "auto" : voiceLanguage);
        }

        var assistant = new LinkedHashMap<String, Object>();
        assistant.put("name", assistantName(call));
        assistant.put("firstMessage", greeting == null ? "" : greeting);
        assistant.put("firstMessageMode", "assistant-speaks-first");
        assistant.put("firstMessageInterruptionsEnabled", true);
        assistant.put("model", modelConfig);
        assistant.put("transcriber", transcriber);
        assistant.put("voice", voice);
        assistant.put("backgroundSound", "off");
        assistant.put("maxDurationSeconds", Math.max(10, call.getAgent().getMaxCallDurationSeconds()));
        assistant.put("startSpeakingPlan", Map.of("waitSeconds", 0.2));
        assistant.put("stopSpeakingPlan", Map.of("numWords", 1, "backoffSeconds", 0.5));
        assistant.put("clientMessages", List.of(
                "transcript", "speech-update", "status-update", "tool-calls-result", "user-interrupted"
        ));
        // Call lifecycle and transcripts are persisted from the authenticated
        // browser session. The public callback accepts business tools only.
        assistant.put("serverMessages", List.of("tool-calls"));
        assistant.put("server", callbackServer);
        assistant.put("artifactPlan", Map.of(
                "recordingEnabled", false,
                "loggingEnabled", true,
                "transcriptPlan", Map.of("enabled", true)
        ));
        assistant.put("metadata", Map.of(
                "sautiCallSid", call.getTwilioCallSid(),
                "sautiCallId", call.getId().toString(),
                "sautiAgentId", call.getAgent().getId().toString()
        ));

        var configuration = Map.copyOf(assistant);
        pendingWebCalls.put(call.getTwilioCallSid(), new PendingWebCall(
                callbackToken,
                configuration,
                Instant.now().plusSeconds(Math.max(120, call.getAgent().getMaxCallDurationSeconds() + 120L))
        ));
        purgeExpiredWebCalls();
        return new BrowserVoiceRuntimeSession(
                "vapi",
                callbackToken,
                "/api/v1/public/vapi/" + url(call.getTwilioCallSid()),
                configuration
        );
    }

    public Map<String, Object> claimWebCall(String callSid, String callbackToken) {
        purgeExpiredWebCalls();
        var pending = pendingWebCalls.remove(callSid);
        if (pending == null || !pending.callbackToken().equals(callbackToken)
                || Instant.now().isAfter(pending.expiresAt())) {
            throw new IllegalArgumentException("The Vapi web call session is unavailable");
        }
        return pending.configuration();
    }

    public void restoreWebCall(String callSid, String callbackToken, Map<String, Object> configuration) {
        pendingWebCalls.putIfAbsent(callSid, new PendingWebCall(
                callbackToken,
                configuration,
                Instant.now().plusSeconds(120)
        ));
    }

    private void purgeExpiredWebCalls() {
        var now = Instant.now();
        pendingWebCalls.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    private String callbackUrl(String callSid, String callbackToken) {
        return publicBaseUrl + "/api/v1/public/vapi/" + url(callSid)
                + "/webhook?token=" + url(callbackToken);
    }

    private String assistantName(Call call) {
        var name = "Sauti " + call.getAgent().getName();
        return name.length() <= 40 ? name : name.substring(0, 40);
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        var normalized = trim(value);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    private record PendingWebCall(
            String callbackToken,
            Map<String, Object> configuration,
            Instant expiresAt
    ) {
    }
}
