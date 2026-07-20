package com.sauti.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends phone audio directly to OpenAI Realtime and streams response text to the
 * existing Cartesia telephony output. This removes Deepgram and chat-completions
 * from the normal phone turn while keeping them available as a runtime fallback.
 */
@Service
public class OpenAiTelephonyRealtimeConversationProvider implements TelephonyRealtimeConversationProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiTelephonyRealtimeConversationProvider.class);

    private final ObjectMapper objectMapper;
    private final OpenAiRealtimeService realtimeService;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String websocketUrl;
    private final String model;
    private final boolean enabled;
    private final ScheduledExecutorService keepAliveExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "openai-telephony-realtime-keepalive");
        thread.setDaemon(true);
        return thread;
    });

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiTelephonyRealtimeConversationProvider(
            ObjectMapper objectMapper,
            OpenAiRealtimeService realtimeService,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${sauti.realtime.openai.websocket-url:wss://api.openai.com/v1/realtime}") String websocketUrl,
            @Value("${sauti.realtime.openai.model:gpt-realtime-1.5}") String model,
            @Value("${sauti.realtime.openai.telephony-enabled:true}") boolean enabled
    ) {
        this(objectMapper, realtimeService, apiKey, websocketUrl, model, enabled,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    OpenAiTelephonyRealtimeConversationProvider(
            ObjectMapper objectMapper,
            OpenAiRealtimeService realtimeService,
            String apiKey,
            String websocketUrl,
            String model,
            boolean enabled,
            HttpClient httpClient
    ) {
        this.objectMapper = objectMapper;
        this.realtimeService = realtimeService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.websocketUrl = websocketUrl;
        this.model = model;
        this.enabled = enabled;
        this.httpClient = httpClient;
    }

    @Override
    public boolean supports(Call call) {
        return enabled
                && !apiKey.isBlank()
                && call != null
                && call.isActive()
                && realtimeService.usesCartesiaVoice(call);
    }

    @Override
    public CompletableFuture<Session> open(Call call, Listener listener) {
        if (!supports(call)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Telephony Realtime is not available"));
        }
        var eventListener = new RealtimeWebSocketListener(
                objectMapper,
                realtimeService,
                call,
                listener,
                realtimeService.telephonySessionConfiguration(call)
        );
        var uri = URI.create(websocketUrl + (websocketUrl.contains("?") ? "&" : "?")
                + "model=" + URLEncoder.encode(model, StandardCharsets.UTF_8));
        return httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .buildAsync(uri, eventListener)
                .thenApply(webSocket -> {
                    var session = new OpenAiTelephonySession(
                            webSocket,
                            objectMapper,
                            keepAliveExecutor,
                            eventListener::markCurrentResponseCancelled
                    );
                    eventListener.attach(session);
                    return session;
                });
    }

    static final class RealtimeWebSocketListener implements WebSocket.Listener {
        private final ObjectMapper objectMapper;
        private final OpenAiRealtimeService realtimeService;
        private final Call call;
        private final Listener listener;
        private final Map<String, Object> sessionConfiguration;
        private final boolean availabilityToolEnabled;
        private final StringBuilder payload = new StringBuilder();
        private final StringBuilder agentText = new StringBuilder();
        private volatile OpenAiTelephonySession session;
        private boolean responseActive;
        private boolean responseInterrupted;
        private boolean textCompleted;
        private boolean structuredTextCandidate;
        private boolean requiredAvailabilityToolPending;
        private String currentOutputItemId = "";
        private int pendingCallerTranscriptions;
        private boolean callerSpeechActive;
        private boolean callerSpeechNotified;
        private final java.util.LinkedHashSet<String> processedCallerItemIds = new java.util.LinkedHashSet<>();
        private final java.util.Set<String> ignoredResponseIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private String lastCallerTranscript = "";
        private long lastCallerTranscriptAt;
        private volatile String currentResponseId = "";
        private volatile boolean discardCurrentResponseOutput;
        private volatile boolean responseCancellationPending;
        private final java.util.ArrayDeque<AgentCompletion> deferredAgentCompletions = new java.util.ArrayDeque<>();

        RealtimeWebSocketListener(
                ObjectMapper objectMapper,
                OpenAiRealtimeService realtimeService,
                Call call,
                Listener listener,
                Map<String, Object> sessionConfiguration
        ) {
            this.objectMapper = objectMapper;
            this.realtimeService = realtimeService;
            this.call = call;
            this.listener = listener;
            this.sessionConfiguration = sessionConfiguration;
            this.availabilityToolEnabled = hasConfiguredTool(sessionConfiguration, "check_availability");
        }

        void attach(OpenAiTelephonySession session) {
            this.session = session;
        }

        synchronized void markCurrentResponseCancelled() {
            if (!currentResponseId.isBlank()) ignoredResponseIds.add(currentResponseId);
            discardCurrentResponseOutput = true;
            var activeSession = session;
            responseCancellationPending = responseActive
                    || (activeSession != null && activeSession.hasResponseOutstanding());
            responseActive = false;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            send(webSocket, Map.of("type", "session.update", "session", sessionConfiguration));
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            payload.append(data);
            if (last) {
                var completePayload = payload.toString();
                payload.setLength(0);
                handle(webSocket, completePayload);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onDisconnected(error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (statusCode != WebSocket.NORMAL_CLOSURE) {
                listener.onDisconnected(new IllegalStateException(
                        "OpenAI telephony Realtime closed with status " + statusCode + ": " + reason
                ));
            }
            return null;
        }

        private void handle(WebSocket webSocket, String value) {
            try {
                var event = objectMapper.readTree(value);
                var type = event.path("type").asText("");
                var eventResponseId = responseId(event);
                if ("response.created".equals(type)) {
                    var activeSession = session;
                    if (activeSession != null && !activeSession.consumeExpectedResponse()) {
                        if (!eventResponseId.isBlank()) ignoredResponseIds.add(eventResponseId);
                        discardCurrentResponseOutput = true;
                        activeSession.cancelResponse();
                        responseActive = false;
                        return;
                    }
                    currentResponseId = eventResponseId;
                    if (responseCancellationPending) {
                        if (!eventResponseId.isBlank()) ignoredResponseIds.add(eventResponseId);
                        discardCurrentResponseOutput = true;
                        activeSession.cancelResponse();
                        return;
                    }
                    discardCurrentResponseOutput = false;
                }
                var ignoredResponse = !eventResponseId.isBlank() && ignoredResponseIds.contains(eventResponseId);
                if (ignoredResponse && "response.done".equals(type)) {
                    ignoredResponseIds.remove(eventResponseId);
                    if (eventResponseId.equals(currentResponseId)) {
                        currentResponseId = "";
                        discardCurrentResponseOutput = false;
                        responseCancellationPending = false;
                        responseActive = false;
                        agentText.setLength(0);
                    }
                    var activeSession = session;
                    if (activeSession != null) activeSession.responseFinished();
                    return;
                }
                if (ignoredResponse || (discardCurrentResponseOutput && type.startsWith("response."))) return;
                switch (type) {
                    case "response.created" -> {
                        responseActive = true;
                        responseInterrupted = false;
                        textCompleted = false;
                        structuredTextCandidate = false;
                        currentOutputItemId = "";
                        agentText.setLength(0);
                    }
                    case "response.output_item.added" -> {
                        var item = event.path("item");
                        if ("message".equals(item.path("type").asText(""))) {
                            currentOutputItemId = item.path("id").asText("");
                        }
                    }
                    case "input_audio_buffer.speech_started" -> {
                        pendingCallerTranscriptions++;
                        if (responseActive) responseInterrupted = true;
                        synchronized (this) {
                            callerSpeechActive = true;
                            callerSpeechNotified = false;
                        }
                    }
                    case "input_audio_buffer.speech_stopped" -> {
                        synchronized (this) {
                            callerSpeechActive = false;
                        }
                    }
                    case "conversation.item.input_audio_transcription.delta" -> {
                        var transcriptDelta = event.path("delta").asText("");
                        if (CallerTranscriptGuard.accepts(transcriptDelta)) notifyActiveCallerSpeechStarted();
                    }
                    case "conversation.item.input_audio_transcription.completed" -> {
                        var transcript = event.path("transcript").asText("").trim();
                        if (CallerTranscriptGuard.accepts(transcript) && acceptCallerTranscript(event.path("item_id").asText(""), transcript)) {
                            notifyCallerSpeechStarted();
                            listener.onCallerTranscript(transcript);
                            var activeSession = session;
                            if (activeSession != null) {
                                activeSession.updateInstructions(realtimeService.realtimeInstructions(call, transcript));
                                requiredAvailabilityToolPending = false;
                                activeSession.requestResponse();
                            }
                        }
                        finishCallerTurn();
                        pendingCallerTranscriptions = Math.max(0, pendingCallerTranscriptions - 1);
                        flushDeferredAgentCompletions();
                    }
                    case "conversation.item.input_audio_transcription.failed" -> {
                        finishCallerTurn();
                        pendingCallerTranscriptions = Math.max(0, pendingCallerTranscriptions - 1);
                        flushDeferredAgentCompletions();
                    }
                    case "response.output_text.delta" -> {
                        var delta = event.path("delta").asText("");
                        if (!delta.isEmpty()) {
                            if (agentText.length() == 0) {
                                var leading = delta.stripLeading();
                                structuredTextCandidate = leading.startsWith("{") || leading.startsWith("[");
                            }
                            agentText.append(delta);
                            if (!responseInterrupted && !requiredAvailabilityToolPending && !structuredTextCandidate) {
                                listener.onAgentTextDelta(delta);
                            }
                        }
                    }
                    case "response.output_text.done" -> {
                        var providerText = event.path("text").asText("");
                        if (requiredAvailabilityToolPending
                                || VoiceOutputGuard.isStructuredPayload(providerText.isBlank() ? agentText.toString() : providerText)) {
                            recoverRequiredAvailabilityTool(webSocket, event.path("text").asText(""));
                        } else {
                            completeText(providerText);
                        }
                    }
                    case "response.function_call_arguments.done" -> {
                        requiredAvailabilityToolPending = false;
                        executeTool(webSocket, event);
                    }
                    case "response.done" -> {
                        responseActive = false;
                        responseCancellationPending = false;
                        if (eventResponseId.isBlank() || eventResponseId.equals(currentResponseId)) {
                            currentResponseId = "";
                        }
                        if (!textCompleted && agentText.length() > 0) {
                            if (requiredAvailabilityToolPending
                                    || VoiceOutputGuard.isStructuredPayload(agentText.toString())) {
                                recoverRequiredAvailabilityTool(webSocket, "");
                            } else {
                                completeText("");
                            }
                        }
                        var activeSession = session;
                        if (activeSession != null) activeSession.responseFinished();
                    }
                    case "error" -> {
                        var error = event.path("error");
                        var message = error.path("message").asText("OpenAI telephony Realtime failed");
                        var normalizedMessage = message.toLowerCase(java.util.Locale.ROOT);
                        if (normalizedMessage.contains("conversation already has an active response")
                                || normalizedMessage.contains("active response in progress")) {
                            var activeSession = session;
                            if (activeSession != null) activeSession.responseCreationRejectedAsBusy();
                        } else if (!normalizedMessage.contains("no active response")) {
                            listener.onError(new IllegalStateException(message));
                        }
                    }
                    default -> { }
                }
            } catch (Exception exception) {
                listener.onError(exception);
            }
        }

        private boolean acceptCallerTranscript(String itemId, String transcript) {
            if (itemId != null && !itemId.isBlank()) {
                if (!processedCallerItemIds.add(itemId)) return false;
                if (processedCallerItemIds.size() > 128) {
                    var iterator = processedCallerItemIds.iterator();
                    if (iterator.hasNext()) {
                        iterator.next();
                        iterator.remove();
                    }
                }
            }
            var normalized = transcript.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
            var now = System.currentTimeMillis();
            if (normalized.equals(lastCallerTranscript) && now - lastCallerTranscriptAt < 3_000) return false;
            lastCallerTranscript = normalized;
            lastCallerTranscriptAt = now;
            return true;
        }

        private String responseId(JsonNode event) {
            var direct = event.path("response_id").asText("").trim();
            return direct.isBlank() ? event.path("response").path("id").asText("").trim() : direct;
        }

        private synchronized void notifyActiveCallerSpeechStarted() {
            if (!callerSpeechActive) return;
            notifyCallerSpeechStarted();
        }

        private synchronized void notifyCallerSpeechStarted() {
            if (callerSpeechNotified) return;
            callerSpeechNotified = true;
            listener.onCallerSpeechStarted();
        }

        private synchronized void finishCallerTurn() {
            callerSpeechActive = false;
            callerSpeechNotified = false;
        }

        private void completeText(String providerText) {
            if (textCompleted) return;
            var finalText = providerText == null || providerText.isBlank()
                    ? agentText.toString().trim()
                    : providerText.trim();
            agentText.setLength(0);
            textCompleted = true;
            if (finalText.isBlank()) return;
            if (pendingCallerTranscriptions > 0) {
                deferredAgentCompletions.addLast(new AgentCompletion(finalText, responseInterrupted));
            } else {
                listener.onAgentTextComplete(finalText, responseInterrupted);
            }
        }

        private void flushDeferredAgentCompletions() {
            if (pendingCallerTranscriptions > 0) return;
            while (!deferredAgentCompletions.isEmpty()) {
                var completion = deferredAgentCompletions.removeFirst();
                listener.onAgentTextComplete(completion.text(), completion.interrupted());
            }
        }

        private void executeTool(WebSocket webSocket, JsonNode event) {
            executeTool(
                    webSocket,
                    event.path("call_id").asText(""),
                    event.path("name").asText(""),
                    event.path("arguments").asText("{}")
            );
        }

        private void recoverRequiredAvailabilityTool(WebSocket webSocket, String providerText) {
            var finalText = providerText == null || providerText.isBlank()
                    ? agentText.toString().trim()
                    : providerText.trim();
            agentText.setLength(0);
            textCompleted = true;
            requiredAvailabilityToolPending = false;
            if (!currentOutputItemId.isBlank()) {
                send(webSocket, Map.of("type", "conversation.item.delete", "item_id", currentOutputItemId));
            }
            var arguments = VoiceOutputGuard.parseObject(objectMapper, finalText);
            if (arguments.isEmpty()) {
                listener.onAgentTextComplete(
                        VoiceOutputGuard.safeAvailabilityClarification(responseLanguage()),
                        false
                );
                return;
            }
            var toolName = recoveredToolName(arguments.get());
            if (toolName == null) {
                listener.onAgentTextComplete(VoiceOutputGuard.safeAvailabilityClarification(responseLanguage()), false);
                return;
            }
            var callId = VoiceOutputGuard.realtimeCallId("tool");
            var argumentsJson = write(arguments.get());
            send(webSocket, Map.of(
                    "type", "conversation.item.create",
                    "item", Map.of(
                            "type", "function_call",
                            "call_id", callId,
                            "name", toolName,
                            "arguments", argumentsJson
                    )
            ));
            executeTool(webSocket, callId, toolName, argumentsJson);
        }

        private String recoveredToolName(Map<String, Object> arguments) {
            if (arguments.containsKey("booking_number") && arguments.containsKey("appointment_at")
                    && hasConfiguredTool(sessionConfiguration, "reschedule_booking")) return "reschedule_booking";
            if (arguments.containsKey("booking_number") && hasConfiguredTool(sessionConfiguration, "cancel_booking")) {
                return "cancel_booking";
            }
            if (arguments.containsKey("appointment_at") && arguments.containsKey("caller_name")
                    && hasConfiguredTool(sessionConfiguration, "book_slot")) return "book_slot";
            if (arguments.containsKey("date") && hasConfiguredTool(sessionConfiguration, "check_availability")) {
                return "check_availability";
            }
            return null;
        }

        private String responseLanguage() {
            var detected = call.getLanguageDetected();
            return detected == null || detected.isBlank()
                    ? call.getAgent().getDefaultLanguage()
                    : detected;
        }

        private void executeTool(WebSocket webSocket, String callId, String name, String arguments) {
            CompletableFuture.runAsync(() -> {
                com.sauti.llm.LlmToolResult result;
                try {
                    result = realtimeService.executeTool(call, callId, name, arguments);
                } catch (RuntimeException exception) {
                    result = new com.sauti.llm.LlmToolResult(
                            callId, name, false, Map.of(), "Tool execution failed"
                    );
                }
                send(webSocket, Map.of(
                        "type", "conversation.item.create",
                        "item", Map.of(
                                "type", "function_call_output",
                                "call_id", callId,
                                "output", write(result)
                        )
                ));
                if (!result.success()) {
                    var safeText = switch (name) {
                        case "check_availability" -> VoiceOutputGuard.safeAvailabilityFailure(responseLanguage());
                        case "book_slot" -> VoiceOutputGuard.safeBookingFailure(responseLanguage());
                        default -> VoiceOutputGuard.safeAvailabilityClarification(responseLanguage());
                    };
                    listener.onAgentTextDelta(safeText);
                    listener.onAgentTextComplete(safeText, false);
                    return;
                }
                var deterministicResponse = toolSpokenResponse(result);
                if (!deterministicResponse.isBlank()) {
                    var activeSession = session;
                    if (activeSession != null) activeSession.requestExactResponse(deterministicResponse);
                    return;
                }
                var activeSession = session;
                if (activeSession != null) activeSession.requestToolResultResponse();
                else send(webSocket, Map.of("type", "response.create"));
            });
        }

        private String toolSpokenResponse(com.sauti.llm.LlmToolResult result) {
            var value = result.result().get("spokenResponse");
            return value == null ? "" : value.toString().trim();
        }

        private String write(Object value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception exception) {
                return "{\"success\":false}";
            }
        }

        private void send(WebSocket webSocket, Map<String, ?> event) {
            try {
                webSocket.sendText(objectMapper.writeValueAsString(event), true);
            } catch (Exception exception) {
                listener.onError(exception);
            }
        }

        private record AgentCompletion(String text, boolean interrupted) { }

        private static boolean hasConfiguredTool(Map<String, Object> configuration, String name) {
            var configuredTools = configuration.get("tools");
            if (!(configuredTools instanceof Iterable<?> tools)) return false;
            for (var tool : tools) {
                if (tool instanceof Map<?, ?> definition && name.equals(definition.get("name"))) return true;
            }
            return false;
        }
    }

    static final class OpenAiTelephonySession implements Session {
        private final WebSocket webSocket;
        private final ObjectMapper objectMapper;
        private final ScheduledFuture<?> keepAliveTask;
        private final Runnable onCancel;
        private final java.util.concurrent.atomic.AtomicInteger expectedResponses = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.ArrayDeque<Map<String, ?>> pendingResponses = new java.util.ArrayDeque<>();
        private boolean responseOutstanding;
        private Map<String, ?> inFlightResponse;
        private CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);

        OpenAiTelephonySession(
                WebSocket webSocket,
                ObjectMapper objectMapper,
                ScheduledExecutorService keepAliveExecutor
        ) {
            this(webSocket, objectMapper, keepAliveExecutor, () -> { });
        }

        OpenAiTelephonySession(
                WebSocket webSocket,
                ObjectMapper objectMapper,
                ScheduledExecutorService keepAliveExecutor,
                Runnable onCancel
        ) {
            this.webSocket = webSocket;
            this.objectMapper = objectMapper;
            this.onCancel = onCancel;
            this.keepAliveTask = keepAliveExecutor.scheduleAtFixedRate(
                    () -> webSocket.sendPing(ByteBuffer.wrap(new byte[] {1})),
                    8,
                    8,
                    TimeUnit.SECONDS
            );
        }

        @Override
        public void sendPcmAudio(byte[] pcm16kAudio) {
            if (pcm16kAudio == null || pcm16kAudio.length == 0) return;
            send(Map.of(
                    "type", "input_audio_buffer.append",
                    "audio", Base64.getEncoder().encodeToString(pcm16kToPcm24k(pcm16kAudio))
            ));
        }

        @Override
        public void seedAssistantText(String text) {
            if (text == null || text.isBlank()) return;
            send(Map.of(
                    "type", "conversation.item.create",
                    "item", Map.of(
                            "type", "message",
                            "role", "assistant",
                            "content", java.util.List.of(Map.of("type", "output_text", "text", text.trim()))
                    )
            ));
        }

        @Override
        public void sendUserText(String text) {
            if (text == null || text.isBlank()) return;
            send(Map.of(
                    "type", "conversation.item.create",
                    "item", Map.of(
                            "type", "message",
                            "role", "user",
                            "content", java.util.List.of(Map.of("type", "input_text", "text", text.trim()))
                    )
            ));
            requestResponse();
        }

        @Override
        public void cancelResponse() {
            onCancel.run();
            send(Map.of("type", "response.cancel"));
        }

        void requestResponse() {
            // updateInstructions(...) installs the full personalized prompt just
            // before this call. Supplying response.instructions here would
            // override it for this turn and drop exact owner-configured facts.
            enqueueResponse(Map.of("type", "response.create"));
        }

        void requestToolResultResponse() {
            enqueueResponse(Map.of(
                    "type", "response.create",
                    "response", Map.of(
                            "tool_choice", "none"
                    )
            ));
        }

        void requestResponseWithRequiredTool(String toolName) {
            enqueueResponse(Map.of(
                    "type", "response.create",
                    "response", Map.of(
                            "tool_choice", Map.of("type", "function", "name", toolName)
                    )
            ));
        }

        void requestExactResponse(String text) {
            if (text == null || text.isBlank()) return;
            enqueueResponse(Map.of(
                    "type", "response.create",
                    "response", Map.of(
                            "instructions", "Say exactly this text with no additions or omissions: " + text.trim(),
                            "tool_choice", "none"
                    )
            ));
        }

        private synchronized void enqueueResponse(Map<String, ?> response) {
            pendingResponses.addLast(response);
            dispatchNextResponse();
        }

        private synchronized void dispatchNextResponse() {
            if (responseOutstanding || pendingResponses.isEmpty()) return;
            responseOutstanding = true;
            expectedResponses.incrementAndGet();
            inFlightResponse = pendingResponses.removeFirst();
            send(inFlightResponse);
        }

        synchronized void responseFinished() {
            responseOutstanding = false;
            inFlightResponse = null;
            dispatchNextResponse();
        }

        synchronized void responseCreationRejectedAsBusy() {
            if (inFlightResponse != null) {
                pendingResponses.addFirst(inFlightResponse);
                inFlightResponse = null;
            }
            expectedResponses.updateAndGet(current -> Math.max(0, current - 1));
            // Keep responseOutstanding true until the provider finishes the
            // response it reported as active. responseFinished then retries
            // the preserved request without overlapping response.create calls.
        }

        synchronized boolean hasResponseOutstanding() {
            return responseOutstanding;
        }

        void updateInstructions(String instructions) {
            if (instructions == null || instructions.isBlank()) return;
            send(Map.of(
                    "type", "session.update",
                    "session", Map.of("type", "realtime", "instructions", instructions)
            ));
        }

        boolean consumeExpectedResponse() {
            while (true) {
                var current = expectedResponses.get();
                if (current <= 0) return false;
                if (expectedResponses.compareAndSet(current, current - 1)) return true;
            }
        }

        private synchronized void send(Map<String, ?> event) {
            try {
                var json = objectMapper.writeValueAsString(event);
                sendChain = sendChain.handle((ignored, error) -> null)
                        .thenCompose(ignored -> webSocket.sendText(json, true).thenApply(ws -> null));
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to send OpenAI telephony Realtime event", exception);
            }
        }

        @Override
        public void close() {
            keepAliveTask.cancel(false);
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended");
        }

        static byte[] pcm16kToPcm24k(byte[] input) {
            if (input.length < 4) return input;
            int inputSamples = input.length / 2;
            int outputSamples = (int) Math.ceil(inputSamples * 1.5);
            var output = new byte[outputSamples * 2];
            for (int index = 0; index < outputSamples; index++) {
                double source = index / 1.5;
                int left = Math.min((int) source, inputSamples - 1);
                int right = Math.min(left + 1, inputSamples - 1);
                double fraction = source - left;
                short a = sample(input, left);
                short b = sample(input, right);
                short interpolated = (short) Math.round(a + ((b - a) * fraction));
                output[index * 2] = (byte) (interpolated & 0xff);
                output[(index * 2) + 1] = (byte) ((interpolated >>> 8) & 0xff);
            }
            return output;
        }

        private static short sample(byte[] input, int index) {
            int offset = index * 2;
            return (short) ((input[offset] & 0xff) | (input[offset + 1] << 8));
        }
    }
}
