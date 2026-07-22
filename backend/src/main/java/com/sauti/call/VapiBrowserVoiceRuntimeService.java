package com.sauti.call;

import com.sauti.agent.AgentBusinessIdentity;
import com.sauti.llm.ConversationOrchestrator;
import com.sauti.tool.AgentToolLoader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VapiBrowserVoiceRuntimeService implements BrowserVoiceRuntimeProvider {
    private static final Set<String> VAPI_SCHEMA_FORMATS = Set.of(
            "date-time", "time", "date", "duration", "email", "hostname",
            "ipv4", "ipv6", "uuid"
    );
    private final ConversationOrchestrator conversationOrchestrator;
    private final AgentToolLoader agentToolLoader;
    private final String publicKey;
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
            @Value("${sauti.vapi.public-key:}") String publicKey,
            @Value("${sauti.vapi.public-base-url:${sauti.telephony.public-base-url:http://localhost:8080}}") String publicBaseUrl,
            @Value("${sauti.vapi.model-provider:openai}") String modelProvider,
            @Value("${sauti.vapi.model:gpt-4.1-mini}") String model,
            @Value("${sauti.vapi.transcriber-provider:deepgram}") String transcriberProvider,
            @Value("${sauti.vapi.transcriber-model:nova-3}") String transcriberModel,
            @Value("${sauti.vapi.transcriber-language:agent}") String transcriberLanguage,
            @Value("${sauti.vapi.voice-provider:vapi}") String voiceProvider,
            @Value("${sauti.vapi.voice-id:Savannah}") String voiceId,
            @Value("${sauti.vapi.voice-version:2}") int voiceVersion,
            @Value("${sauti.vapi.voice-language:auto}") String voiceLanguage,
            @Value("${sauti.vapi.tool-timeout-seconds:30}") int toolTimeoutSeconds,
            @Value("${sauti.vapi.delayed-message-ms:1000}") int delayedMessageMs
    ) {
        this.conversationOrchestrator = conversationOrchestrator;
        this.agentToolLoader = agentToolLoader;
        this.publicKey = trim(publicKey);
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
        return !publicKey.isBlank();
    }

    public String webCallPublicKey() {
        if (!isConfigured()) throw new IllegalStateException("Vapi browser calls are not configured. Set VAPI_PUBLIC_KEY.");
        return publicKey;
    }

    @Override
    public BrowserVoiceRuntimeSession prepare(Call call, String greeting, String callbackToken) {
        if (!isConfigured()) throw new IllegalStateException("Vapi browser calls are not configured. Set VAPI_PUBLIC_KEY.");
        var callbackUrl = callbackUrl(call.getTwilioCallSid(), callbackToken);
        var callbackServer = Map.<String, Object>of(
                "url", callbackUrl,
                "timeoutSeconds", toolTimeoutSeconds
        );
        var loadedTools = agentToolLoader.loadForAgent(call.getAgent().getId());
        var hasEndCall = loadedTools.stream().anyMatch(tool -> "end_call".equals(tool.name()));
        var tools = new java.util.ArrayList<>(loadedTools.stream()
                // Vapi's built-in endCall tool reliably drains the farewell and
                // closes the provider session. Sauti still persists completion
                // from the authenticated browser call-end event.
                .filter(tool -> !"end_call".equals(tool.name()))
                .map(tool -> Map.<String, Object>of(
                        "type", "function",
                        "async", false,
                        "function", Map.of(
                                "name", tool.name(),
                                "description", tool.description() == null ? "" : tool.description(),
                                "parameters", vapiParameters(tool.inputSchema())
                        ),
                        "server", callbackServer,
                        // Vapi supplies and translates the wording. Fast tools
                        // return normally; delayed tools receive one apology
                        // without Sauti maintaining language-specific phrases.
                        "messages", toolMessages(tool)
                ))
                .toList());
        if (hasEndCall) tools.add(Map.of("type", "endCall"));

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
                "content", vapiInstructions(call, language, hasEndCall)
        )));
        if (!tools.isEmpty()) modelConfig.put("tools", tools);

        var transcriber = new LinkedHashMap<String, Object>();
        transcriber.put("provider", transcriberProvider);
        transcriber.put("model", transcriberModel);
        transcriber.put("language", resolvedTranscriberLanguage(call));
        if ("deepgram".equalsIgnoreCase(transcriberProvider)) {
            transcriber.put("smartFormat", true);
            transcriber.put("numerals", true);
            transcriber.put("endpointing", Math.max(10, Math.min(500, call.getAgent().getSttEndpointingMs())));
            var keyterms = boostedKeyterms(call);
            if (!keyterms.isEmpty()) transcriber.put("keyterm", keyterms);
        }

        var voice = new LinkedHashMap<String, Object>();
        voice.put("provider", voiceProvider);
        voice.put("voiceId", voiceId);
        if ("vapi".equalsIgnoreCase(voiceProvider)) {
            voice.put("version", voiceVersion);
            voice.put("language", voiceLanguage.isBlank() ? "auto" : voiceLanguage);
        }
        voice.put("cachingEnabled", true);
        voice.put("chunkPlan", Map.of(
                "enabled", true,
                "minCharacters", 80,
                "punctuationBoundaries", List.of(".", "!", "?", "。", "۔", "।", "॥")
        ));

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
        assistant.put("startSpeakingPlan", Map.of(
                "waitSeconds", 0.1,
                "transcriptionEndpointingPlan", Map.of(
                        "onPunctuationSeconds", 0.1,
                        "onNoPunctuationSeconds", 1.0,
                        "onNumberSeconds", 0.6
                )
        ));
        assistant.put("stopSpeakingPlan", Map.of("numWords", 1, "backoffSeconds", 0.5));
        assistant.put("modelOutputInMessagesEnabled", true);
        assistant.put("clientMessages", List.of(
                "transcript", "speech-update", "assistant.speechStarted", "status-update",
                "tool-calls-result", "user-interrupted", "voice-input"
        ));
        // Call lifecycle and transcripts are persisted from the authenticated
        // browser session. The public callback accepts business tools plus a
        // metrics-only end report; it never trusts provider transcript writes.
        assistant.put("serverMessages", List.of("tool-calls", "end-of-call-report"));
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

    private List<Map<String, Object>> toolMessages(com.sauti.llm.LlmToolDefinition tool) {
        var messages = new java.util.ArrayList<Map<String, Object>>();
        if (tool.callerWaitExpected()) {
            // Omitting content deliberately selects Vapi's generated filler in
            // the active call language. Sauti must not maintain phrase tables.
            messages.add(Map.of("type", "request-start", "blocking", false));
        }
        messages.add(Map.of(
                "type", "request-response-delayed",
                "timingMilliseconds", delayedMessageMs
        ));
        return List.copyOf(messages);
    }

    private String resolvedTranscriberLanguage(Call call) {
        if (!"agent".equalsIgnoreCase(transcriberLanguage)
                && !"auto".equalsIgnoreCase(transcriberLanguage)) {
            return transcriberLanguage;
        }
        var supported = call.getAgent().getSupportedLanguages();
        if (supported != null && supported.stream().filter(value -> value != null && !value.isBlank()).distinct().count() > 1) {
            return "multi";
        }
        var configured = trim(call.getAgent().getDefaultLanguage());
        return configured.isBlank() ? "multi" : configured;
    }

    private List<String> boostedKeyterms(Call call) {
        var configured = trim(call.getAgent().getSttBoostedKeywords());
        var businessName = AgentBusinessIdentity.fromPrompt(call.getAgent());
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(businessName),
                        java.util.Arrays.stream(configured.split(","))
                )
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(100)
                .toList();
    }

    private String vapiInstructions(Call call, String language, boolean hasEndCall) {
        var instructions = conversationOrchestrator.realtimeInstructions(call, language);
        if (hasEndCall) instructions = instructions.replace("end_call", "endCall");
        return instructions + """

                VAPI EXECUTION CONTRACT — HIGHEST PRIORITY:
                - When a business tool is needed, emit that tool call as the first and only output. Do not speak, acknowledge, summarize, or begin a sentence before the tool result arrives.
                - Treat the complete tool result as authoritative. If actionPerformed is false, the action did not happen. Never continue a sentence that says it succeeded.
                - A booking is saved only when the successful book_slot result contains a customer-facing booking number. State that exact number once. Without it, never say booked, confirmed, saved, or scheduled.
                - Keep one spoken response continuous. Do not split a sentence around a tool call and do not emit sentence fragments.
                - Never repeat the configured opening greeting after the caller begins speaking.
                """ + (hasEndCall ? """
                - When the caller clearly finishes, give one brief respectful farewell, invoke endCall immediately, and produce no further speech.
                """ : "");
    }

    /**
     * Vapi validates JSON Schema formats against a smaller allow-list than the
     * schemas Sauti uses internally. Unsupported annotations such as
     * {@code format: phone} must not prevent the entire browser call from
     * starting; the property's type, description, and all validation fields
     * remain intact.
     */
    private static Map<String, Object> vapiParameters(Map<String, Object> schema) {
        var source = schema == null || schema.isEmpty()
                ? Map.<String, Object>of("type", "object")
                : schema;
        return normalizeSchemaMap(source);
    }

    private static Map<String, Object> normalizeSchemaMap(Map<?, ?> source) {
        var normalized = new LinkedHashMap<String, Object>();
        source.forEach((rawKey, rawValue) -> {
            if (rawKey == null || rawValue == null) return;
            var key = rawKey.toString();
            if ("format".equals(key)
                    && (!((rawValue instanceof String format)) || !VAPI_SCHEMA_FORMATS.contains(format))) {
                return;
            }
            normalized.put(key, normalizeSchemaValue(rawValue));
        });
        return Map.copyOf(normalized);
    }

    private static Object normalizeSchemaValue(Object value) {
        if (value instanceof Map<?, ?> map) return normalizeSchemaMap(map);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(VapiBrowserVoiceRuntimeService::normalizeSchemaValue)
                    .toList();
        }
        return value;
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
