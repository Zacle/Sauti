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
import java.util.Set;
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
    static final int REALTIME_CALL_ID_MAX_LENGTH = 32;
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiTelephonyRealtimeConversationProvider.class);
    private static final String RESPONSE_REQUEST_ID_METADATA = "sauti_request_id";
    private static final String RESPONSE_PURPOSE_CONVERSATION = "conversation";
    private static final String RESPONSE_PURPOSE_BUSINESS_PROGRESS = "business_progress";
    private static final String RESPONSE_PURPOSE_POST_BOOKING_GUIDANCE = "post_booking_guidance";
    private static final long BUSINESS_ACTION_PROGRESS_DELAY_MILLIS = 1_500L;
    private static final long BUSINESS_ACTION_PROGRESS_REPEAT_MILLIS = 8_000L;

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

    static String newRealtimeChainedCallId(String parentCallId, String toolName) {
        var source = (parentCallId == null ? "" : parentCallId)
                + "\u0000"
                + (toolName == null ? "" : toolName);
        var random = java.util.UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return "sc_" + random.substring(0, REALTIME_CALL_ID_MAX_LENGTH - 3);
    }

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

    static boolean supportsBusinessActionProgress(String toolName) {
        if (toolName == null || toolName.isBlank()) return false;
        return !Set.of("update_conversation_state", "get_business_hours", "end_call")
                .contains(toolName.trim().toLowerCase(java.util.Locale.ROOT));
    }

    static boolean isAuxiliaryResponsePurpose(String purpose) {
        return RESPONSE_PURPOSE_BUSINESS_PROGRESS.equals(purpose)
                || RESPONSE_PURPOSE_POST_BOOKING_GUIDANCE.equals(purpose);
    }

    static String businessActionProgressInstruction(String toolName) {
        var operation = switch (toolName == null ? "" : toolName) {
            case "book_slot" -> "saving the appointment";
            case "check_availability" -> "checking the live schedule";
            case "reschedule_booking" -> "rescheduling the appointment";
            case "cancel_booking" -> "cancelling the appointment";
            default -> "working on the caller's request";
        };
        return "The system is still " + operation + " and the caller has been waiting longer than expected. "
                + "Give one brief, natural, professional progress update in the caller's current language. "
                + "Include a short apology for the wait and make clear that you are still working on it. "
                + "Do not claim success or failure, invent a result, repeat booking details, ask a question, or call a tool.";
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
        private static final ScheduledExecutorService CALLER_TURN_WATCHDOG =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    var thread = new Thread(runnable, "openai-telephony-caller-turn-watchdog");
                    thread.setDaemon(true);
                    return thread;
                });
        private static final long RESPONSE_CANCELLATION_TIMEOUT_SECONDS = 2L;
        private final ObjectMapper objectMapper;
        private final OpenAiRealtimeService realtimeService;
        private final Call call;
        private final String diagnosticCallId;
        private final Listener listener;
        private final Map<String, Object> sessionConfiguration;
        private final StringBuilder payload = new StringBuilder();
        private final StringBuilder agentText = new StringBuilder();
        private volatile OpenAiTelephonySession session;
        private boolean responseActive;
        private boolean responseInterrupted;
        private boolean textCompleted;
        private int protocolRecoveryAttempts;
        private String currentOutputItemId = "";
        private final java.util.Map<String, String> outputItemTypes = new java.util.HashMap<>();
        private boolean responseHasToolCall;
        private boolean callerSpeechActive;
        private boolean callerSpeechNotified;
        private boolean callerTurnAgentWasResponding;
        private String activeCallerTurnKey = "";
        private long callerTurnSequence;
        private final java.util.LinkedHashMap<String, ScheduledFuture<?>> pendingCallerTurns =
                new java.util.LinkedHashMap<>();
        private final java.util.LinkedHashSet<String> processedCallerItemIds = new java.util.LinkedHashSet<>();
        private final java.util.LinkedHashSet<String> processedToolCallIds = new java.util.LinkedHashSet<>();
        private final java.util.Set<String> ignoredResponseIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final java.util.Set<String> abandonedResponseRequestIds =
                java.util.concurrent.ConcurrentHashMap.newKeySet();
        private String lastCallerTranscript = "";
        private long lastCallerTranscriptAt;
        private volatile String currentResponseId = "";
        private volatile long currentResponseCreatedAtNanos;
        private volatile long turnGeneration;
        private volatile long currentResponseGeneration;
        private volatile boolean discardCurrentResponseOutput;
        private volatile boolean responseCancellationPending;
        private ScheduledFuture<?> responseCancellationWatchdog;
        private final java.util.ArrayDeque<AgentCompletion> deferredAgentCompletions = new java.util.ArrayDeque<>();
        private final java.util.Set<String> deliveredSpeechIds = new java.util.HashSet<>();
        private final java.util.Set<String> deliveredSpeechFingerprints = new java.util.HashSet<>();
        private final java.util.Set<Long> toolFollowupGenerations = new java.util.HashSet<>();
        private final java.util.Set<Long> toolSpeechGenerations = new java.util.HashSet<>();
        private final java.util.Set<Long> failedResponseRetryGenerations = new java.util.HashSet<>();
        private final java.util.Map<String, CompletableFuture<com.sauti.llm.LlmToolResult>> toolExecutions =
                new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Map<String, ScheduledFuture<?>> businessActionProgressTimers =
                new java.util.HashMap<>();
        private final java.util.Set<String> activeBusinessActionProgress = new java.util.HashSet<>();
        private String currentResponsePurpose = RESPONSE_PURPOSE_CONVERSATION;
        private String currentBusinessActionProgressKey = "";

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
            this.diagnosticCallId = call == null || call.getId() == null ? "-" : call.getId().toString();
            this.listener = listener;
            this.sessionConfiguration = sessionConfiguration;
        }

        void attach(OpenAiTelephonySession session) {
            this.session = session;
        }

        synchronized void markCurrentResponseCancelled() {
            var activeSession = session;
            var providerResponseActive = responseActive;
            turnGeneration = Math.max(turnGeneration + 1L, activeSession == null ? 0L : activeSession.currentGeneration());
            if (!currentResponseId.isBlank()) ignoredResponseIds.add(currentResponseId);
            discardCurrentResponseOutput = true;
            responseCancellationPending = responseActive
                    || (activeSession != null && activeSession.hasResponseOutstanding());
            responseActive = false;
            deferredAgentCompletions.clear();
            deliveredSpeechIds.clear();
            deliveredSpeechFingerprints.clear();
            toolFollowupGenerations.clear();
            toolSpeechGenerations.clear();
            failedResponseRetryGenerations.clear();
            // Caller speech cancels output, not an accepted external action.
            // Keep active futures so a provider retry cannot execute the same
            // operation a second time while the first one is still running.
            toolExecutions.entrySet().removeIf(entry -> entry.getValue().isDone());
            businessActionProgressTimers.values().forEach(timer -> timer.cancel(false));
            businessActionProgressTimers.clear();
            activeBusinessActionProgress.clear();
            if (activeSession != null) {
                activeSession.discardResponsesBefore(turnGeneration);
                if (responseCancellationPending) armResponseCancellationWatchdog();
                if (providerResponseActive) activeSession.cancelProviderResponse(currentResponseId);
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
            lifecycle("connection_opened", "status=ready");
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
            cancelCallerTranscriptionWatchdog();
            cancelResponseCancellationWatchdog();
            lifecycle("connection_error", "errorType=" + error.getClass().getSimpleName());
            listener.onDisconnected(error);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            cancelCallerTranscriptionWatchdog();
            cancelResponseCancellationWatchdog();
            lifecycle("connection_closed", "statusCode=" + statusCode);
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
                    var createdRequestId = responseRequestId(event);
                    if (!createdRequestId.isBlank() && abandonedResponseRequestIds.remove(createdRequestId)) {
                        if (!eventResponseId.isBlank()) ignoredResponseIds.add(eventResponseId);
                        if (activeSession != null) activeSession.cancelProviderResponse(eventResponseId);
                        return;
                    }
                    if (activeSession != null && !createdRequestId.isBlank()
                            && !activeSession.isDispatchedRequest(createdRequestId)) {
                        if (!eventResponseId.isBlank()) ignoredResponseIds.add(eventResponseId);
                        activeSession.cancelProviderResponse(eventResponseId);
                        return;
                    }
                    if (activeSession != null && !activeSession.consumeExpectedResponse()) {
                        if (!eventResponseId.isBlank()) ignoredResponseIds.add(eventResponseId);
                        activeSession.cancelProviderResponse(eventResponseId);
                        return;
                    }
                    currentResponseGeneration = activeSession == null
                            ? turnGeneration
                            : activeSession.dispatchedGeneration();
                    currentResponsePurpose = activeSession == null
                            ? RESPONSE_PURPOSE_CONVERSATION
                            : activeSession.dispatchedPurpose();
                    currentBusinessActionProgressKey = activeSession == null
                            ? ""
                            : activeSession.dispatchedProgressKey();
                    currentResponseId = eventResponseId;
                    currentResponseCreatedAtNanos = System.nanoTime();
                    lifecycle(
                            "response_created",
                            "requestId=" + safeDiagnosticToken(createdRequestId)
                                    + " generation=" + currentResponseGeneration
                                    + " purpose=" + currentResponsePurpose
                    );
                    if (responseCancellationPending || currentResponseGeneration != turnGeneration) {
                        if (!eventResponseId.isBlank()) ignoredResponseIds.add(eventResponseId);
                        discardCurrentResponseOutput = true;
                        responseCancellationPending = true;
                        armResponseCancellationWatchdog();
                        if (activeSession != null) activeSession.cancelProviderResponse(eventResponseId);
                        return;
                    }
                    cancelResponseCancellationWatchdog();
                    discardCurrentResponseOutput = false;
                }
                var responseTerminal = "response.done".equals(type) || "response.cancelled".equals(type);
                var ignoredResponse = !eventResponseId.isBlank() && ignoredResponseIds.contains(eventResponseId);
                if (ignoredResponse && responseTerminal) {
                    if (eventResponseId.equals(currentResponseId)) {
                        currentResponseId = "";
                        discardCurrentResponseOutput = false;
                        responseCancellationPending = false;
                        responseActive = false;
                        agentText.setLength(0);
                        cancelResponseCancellationWatchdog();
                        var activeSession = session;
                        if (activeSession != null) activeSession.responseFinished();
                    }
                    return;
                }
                if (ignoredResponse || (discardCurrentResponseOutput && type.startsWith("response."))) return;
                switch (type) {
                    case "response.created" -> {
                        responseActive = true;
                        responseInterrupted = false;
                        textCompleted = false;
                        currentOutputItemId = "";
                        outputItemTypes.clear();
                        responseHasToolCall = false;
                        agentText.setLength(0);
                    }
                    case "response.output_item.added", "response.output_item.created" -> {
                        var item = event.path("item");
                        var itemId = item.path("id").asText("");
                        var itemType = item.path("type").asText("");
                        if (!itemId.isBlank()) outputItemTypes.put(itemId, itemType);
                        if ("message".equals(itemType)) currentOutputItemId = itemId;
                        if ("function_call".equals(itemType)) responseHasToolCall = true;
                    }
                    case "input_audio_buffer.speech_started" -> {
                        synchronized (this) {
                            var pendingTurnKey = beginPendingCallerTurn(event.path("item_id").asText(""));
                            callerSpeechActive = true;
                            callerSpeechNotified = false;
                            callerTurnAgentWasResponding = responseActive;
                            armCallerTranscriptionWatchdog(pendingTurnKey, 15);
                        }
                    }
                    case "input_audio_buffer.speech_stopped" -> {
                        synchronized (this) {
                            callerSpeechActive = false;
                            var pendingTurnKey = event.path("item_id").asText("").trim();
                            if (pendingTurnKey.isBlank()) pendingTurnKey = activeCallerTurnKey;
                            armCallerTranscriptionWatchdog(pendingTurnKey, 4);
                        }
                    }
                    case "conversation.item.input_audio_transcription.delta" -> {
                        var transcriptDelta = event.path("delta").asText("");
                        if (CallerTranscriptGuard.accepts(transcriptDelta)) notifyActiveCallerSpeechStarted();
                    }
                    case "conversation.item.input_audio_transcription.completed" -> {
                        var transcript = event.path("transcript").asText("").trim();
                        lifecycle(
                                "caller_transcription_completed",
                                "textChars=" + transcript.length()
                                        + " accepted=" + CallerTranscriptGuard.accepts(transcript)
                        );
                        if (CallerTranscriptGuard.accepts(transcript) && acceptCallerTranscript(event.path("item_id").asText(""), transcript)) {
                            protocolRecoveryAttempts = 0;
                            notifyCallerSpeechStarted();
                            listener.onCallerTranscript(transcript);
                            var activeSession = session;
                            if (activeSession != null) {
                                activeSession.mirrorCallerTranscript(transcript);
                                activeSession.requestResponse();
                            }
                        }
                        finishCallerTurn();
                        finishPendingCallerTranscription(event.path("item_id").asText(""));
                    }
                    case "conversation.item.input_audio_transcription.failed" -> {
                        lifecycle("caller_transcription_failed", "status=failed");
                        finishCallerTurn();
                        finishPendingCallerTranscription(event.path("item_id").asText(""));
                    }
                    case "response.output_text.delta" -> {
                        if (!isOutputItem(event, "message")) break;
                        var delta = event.path("delta").asText("");
                        if (!delta.isEmpty()) agentText.append(delta);
                    }
                    case "response.output_text.done" -> {
                        var providerText = event.path("text").asText("");
                        var finalText = providerText.isBlank() ? agentText.toString() : providerText;
                        if (!isOutputItem(event, "message")) {
                            agentText.setLength(0);
                            break;
                        }
                        // Wait for response.done before validating or releasing
                        // the message. A later item may establish that the same
                        // response is a tool turn, making all message text silent.
                        agentText.setLength(0);
                        agentText.append(finalText);
                    }
                    case "response.function_call_arguments.done" -> {
                        if (executeCompletedToolCallItem(webSocket, completedToolCallItem(event), currentResponseGeneration)) {
                            responseHasToolCall = true;
                        }
                    }
                    case "response.output_item.done" -> {
                        if (executeCompletedToolCallItem(webSocket, event.path("item"), currentResponseGeneration)) {
                            responseHasToolCall = true;
                        }
                    }
                    case "response.done" -> {
                        var completedGeneration = currentResponseGeneration;
                        var businessProgressResponse = RESPONSE_PURPOSE_BUSINESS_PROGRESS.equals(
                                currentResponsePurpose
                        );
                        var postBookingGuidanceResponse = RESPONSE_PURPOSE_POST_BOOKING_GUIDANCE.equals(
                                currentResponsePurpose
                        );
                        var auxiliaryResponse = businessProgressResponse || postBookingGuidanceResponse;
                        if (responseContainsToolCall(event)) {
                            responseHasToolCall = true;
                            executeCompletedToolCalls(webSocket, event, completedGeneration);
                        }
                        var phasedFinalText = phasedFinalAnswerText(event);
                        if (phasedFinalText != null) {
                            // Realtime 2 can return commentary and final_answer
                            // items together. Only the final user-facing phase
                            // is eligible for the external Cartesia speech path.
                            agentText.setLength(0);
                            agentText.append(phasedFinalText);
                            textCompleted = phasedFinalText.isBlank();
                        }
                        var completedResponseId = eventResponseId.isBlank() ? currentResponseId : eventResponseId;
                        var completedStatus = event.path("response").path("status").asText("");
                        var statusDetails = event.path("response").path("status_details");
                        var providerError = statusDetails.path("error");
                        var responseDurationMillis = currentResponseCreatedAtNanos == 0L
                                ? 0L
                                : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - currentResponseCreatedAtNanos);
                        lifecycle(
                                "response_completed",
                                "status=" + safeDiagnosticToken(completedStatus)
                                        + " durationMs=" + responseDurationMillis
                                        + " hasToolCall=" + responseHasToolCall
                                        + " outputTextChars=" + agentText.length()
                                        + " reason=" + safeDiagnosticToken(statusDetails.path("reason").asText(""))
                                        + " errorType=" + safeDiagnosticToken(providerError.path("type").asText(""))
                                        + " errorCode=" + safeDiagnosticToken(providerError.path("code").asText(""))
                        );
                        var retryFailedResponse = !auxiliaryResponse
                                && ("failed".equals(completedStatus) || "incomplete".equals(completedStatus))
                                && !responseHasToolCall
                                && agentText.length() == 0
                                && completedGeneration == turnGeneration
                                && failedResponseRetryGenerations.add(completedGeneration);
                        responseActive = false;
                        responseCancellationPending = false;
                        cancelResponseCancellationWatchdog();
                        if (eventResponseId.isBlank() || eventResponseId.equals(currentResponseId)) {
                            currentResponseId = "";
                        }
                        if (responseHasToolCall) {
                            agentText.setLength(0);
                            textCompleted = true;
                        } else if (businessProgressResponse) {
                            if (isBusinessActionProgressActive(currentBusinessActionProgressKey)
                                    && agentText.length() > 0) {
                                completeText("", completedGeneration, completedResponseId);
                            } else {
                                if (!currentOutputItemId.isBlank()) {
                                    send(webSocket, Map.of(
                                            "type", "conversation.item.delete",
                                            "item_id", currentOutputItemId
                                    ));
                                }
                                agentText.setLength(0);
                                textCompleted = true;
                            }
                        } else if (postBookingGuidanceResponse) {
                            if (agentText.length() > 0) {
                                completeText("", completedGeneration, completedResponseId);
                            } else {
                                textCompleted = true;
                            }
                        } else if (!textCompleted && agentText.length() > 0) {
                            if (VoiceOutputGuard.isProtocolPayload(agentText.toString())) {
                                recoverProtocolOutput(webSocket, completedGeneration);
                            } else {
                                completeText("", completedGeneration, completedResponseId);
                            }
                        } else if (!auxiliaryResponse
                                && ("failed".equals(completedStatus) || "incomplete".equals(completedStatus))
                                && completedGeneration == turnGeneration) {
                            var failedSession = session;
                            if (retryFailedResponse && failedSession != null) {
                                // Keep the accepted caller turn and retry it
                                // once. The caller should not have to repeat a
                                // transcript that Sauti already received.
                                failedSession.requestResponse();
                                textCompleted = true;
                            } else {
                                deliverAgentCompletion(new AgentCompletion(
                                        VoiceOutputGuard.safeResponseFailure(responseLanguage()),
                                        false,
                                        completedGeneration,
                                        "response-failed:" + completedResponseId
                                ));
                            }
                        }
                        if ("completed".equals(completedStatus)) {
                            failedResponseRetryGenerations.remove(completedGeneration);
                        }
                        var activeSession = session;
                        if (activeSession != null) activeSession.responseFinished();
                        currentResponsePurpose = RESPONSE_PURPOSE_CONVERSATION;
                        currentBusinessActionProgressKey = "";
                    }
                    case "response.cancelled" -> {
                        lifecycle(
                                "response_cancelled",
                                "eventResponseId=" + safeDiagnosticToken(eventResponseId)
                        );
                        if (!eventResponseId.isBlank() && !eventResponseId.equals(currentResponseId)
                                && !responseCancellationPending) break;
                        currentResponseId = "";
                        discardCurrentResponseOutput = false;
                        responseCancellationPending = false;
                        responseActive = false;
                        cancelResponseCancellationWatchdog();
                        responseInterrupted = false;
                        textCompleted = true;
                        agentText.setLength(0);
                        var activeSession = session;
                        if (activeSession != null) activeSession.responseFinished();
                        currentResponsePurpose = RESPONSE_PURPOSE_CONVERSATION;
                        currentBusinessActionProgressKey = "";
                    }
                    case "error" -> {
                        var error = event.path("error");
                        var message = error.path("message").asText("OpenAI telephony Realtime failed");
                        lifecycle(
                                "provider_error",
                                "errorType=" + safeDiagnosticToken(error.path("type").asText(""))
                                        + " errorCode=" + safeDiagnosticToken(error.path("code").asText(""))
                                        + " errorParam=" + safeDiagnosticToken(error.path("param").asText(""))
                        );
                        var normalizedMessage = message.toLowerCase(java.util.Locale.ROOT);
                        if (normalizedMessage.contains("conversation already has an active response")
                                || normalizedMessage.contains("active response in progress")) {
                            var activeSession = session;
                            if (activeSession != null) {
                                activeSession.responseCreationRejectedAsBusy();
                                responseCancellationPending = true;
                                armResponseCancellationWatchdog();
                                activeSession.cancelProviderResponse(currentResponseId);
                            }
                        } else if (normalizedMessage.contains("no active response")) {
                            if (responseCancellationPending) {
                                currentResponseId = "";
                                discardCurrentResponseOutput = false;
                                responseCancellationPending = false;
                                responseActive = false;
                                cancelResponseCancellationWatchdog();
                                var activeSession = session;
                                if (activeSession != null) activeSession.responseFinished();
                            }
                        } else {
                            var activeSession = session;
                            var activePurpose = activeSession == null
                                    ? RESPONSE_PURPOSE_CONVERSATION
                                    : activeSession.dispatchedPurpose();
                            var auxiliaryError = isAuxiliaryResponsePurpose(currentResponsePurpose)
                                    || isAuxiliaryResponsePurpose(activePurpose);
                            if (auxiliaryError) {
                                currentResponseId = "";
                                responseActive = false;
                                agentText.setLength(0);
                                textCompleted = true;
                                currentResponsePurpose = RESPONSE_PURPOSE_CONVERSATION;
                                currentBusinessActionProgressKey = "";
                                if (activeSession != null) activeSession.responseFinished();
                            } else {
                                listener.onError(new IllegalStateException(message));
                            }
                        }
                    }
                    default -> { }
                }
            } catch (Exception exception) {
                lifecycle("event_handler_error", "errorType=" + exception.getClass().getSimpleName());
                listener.onError(exception);
            }
        }

        private void lifecycle(String event, String details) {
            LOGGER.info(
                    "voice_lifecycle component=openai_telephony callId={} event={} generation={} responseId={} "
                            + "purpose={} {}",
                    diagnosticCallId,
                    event,
                    turnGeneration,
                    safeDiagnosticToken(currentResponseId),
                    currentResponsePurpose,
                    details == null ? "" : details
            );
        }

        private String safeDiagnosticToken(String value) {
            if (value == null || value.isBlank()) return "-";
            var sanitized = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
            return sanitized.substring(0, Math.min(96, sanitized.length()));
        }

        private String responseText(JsonNode event) {
            var parts = new java.util.ArrayList<String>();
            for (var item : event.path("response").path("output")) {
                for (var content : item.path("content")) {
                    var text = "output_audio".equals(content.path("type").asText(""))
                            ? content.path("transcript").asText("")
                            : content.path("text").asText("");
                    if (!text.isBlank()) parts.add(text.trim());
                }
            }
            return VoiceOutputGuard.speechText(String.join("\n", parts));
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

        private String responseRequestId(JsonNode event) {
            return event.path("response").path("metadata")
                    .path(RESPONSE_REQUEST_ID_METADATA).asText("").trim();
        }

        private boolean responseContainsToolCall(JsonNode event) {
            var output = event.path("response").path("output");
            if (!output.isArray()) return false;
            for (var item : output) {
                if ("function_call".equals(item.path("type").asText(""))) return true;
            }
            return false;
        }

        private void executeCompletedToolCalls(WebSocket webSocket, JsonNode event, long generation) {
            var output = event.path("response").path("output");
            if (!output.isArray()) return;
            for (var item : output) {
                if (!"function_call".equals(item.path("type").asText(""))) continue;
                var callId = item.path("call_id").asText("");
                var name = item.path("name").asText("");
                if (callId.isBlank() || name.isBlank() || !acceptToolCall(callId)) continue;
                executeTool(
                        webSocket,
                        callId,
                        name,
                        item.path("arguments").asText("{}"),
                        generation
                );
            }
        }

        /**
         * Returns null when the response has no phase metadata, preserving the
         * legacy streamed-text fallback. Returns an empty string when phases are
         * present but no final_answer text exists, which intentionally keeps
         * commentary silent without treating it as malformed protocol.
         */
        private String phasedFinalAnswerText(JsonNode event) {
            var output = event.path("response").path("output");
            if (!output.isArray()) return null;
            var hasPhases = false;
            var finalText = new StringBuilder();
            for (var item : output) {
                var phase = item.path("phase").asText("").trim()
                        .toLowerCase(java.util.Locale.ROOT)
                        .replace('-', '_');
                if (phase.isBlank()) continue;
                hasPhases = true;
                var itemType = item.path("type").asText("");
                if (!"final_answer".equals(phase)
                        || (!itemType.isBlank() && !"message".equals(itemType))) continue;
                var content = item.path("content");
                if (!content.isArray()) continue;
                for (var part : content) {
                    var contentType = part.path("type").asText("");
                    var text = switch (contentType) {
                        case "output_text" -> part.path("text").asText("");
                        case "output_audio" -> part.path("transcript").asText("");
                        default -> "";
                    };
                    if (text.isBlank()) continue;
                    if (!finalText.isEmpty()) finalText.append('\n');
                    finalText.append(text.trim());
                }
            }
            return hasPhases ? finalText.toString().trim() : null;
        }

        private synchronized void notifyActiveCallerSpeechStarted() {
            if (!callerSpeechActive) return;
            notifyCallerSpeechStarted();
        }

        private synchronized void notifyCallerSpeechStarted() {
            if (callerSpeechNotified) return;
            callerSpeechNotified = true;
            responseInterrupted = callerTurnAgentWasResponding || responseActive;
            listener.onCallerSpeechStarted();
        }

        private synchronized void finishCallerTurn() {
            callerSpeechActive = false;
            callerSpeechNotified = false;
            callerTurnAgentWasResponding = false;
        }

        private synchronized void deliverDirectResponse(String text) {
            var speech = VoiceOutputGuard.speechText(text);
            if (speech.isBlank()) return;
            var completion = new AgentCompletion(
                    speech, false, turnGeneration, "direct:" + turnGeneration + ":" + speechFingerprint(speech)
            );
            if (hasPendingCallerTranscriptions()) {
                deferredAgentCompletions.addLast(completion);
            } else {
                deliverAgentCompletion(completion);
            }
        }

        private synchronized String beginPendingCallerTurn(String itemId) {
            var key = itemId == null ? "" : itemId.trim();
            if (key.isBlank()) key = "caller-turn-" + (++callerTurnSequence);
            activeCallerTurnKey = key;
            pendingCallerTurns.putIfAbsent(key, null);
            return key;
        }

        private synchronized void armCallerTranscriptionWatchdog(String key, long timeoutSeconds) {
            if (key == null || key.isBlank() || !pendingCallerTurns.containsKey(key)) return;
            var existing = pendingCallerTurns.get(key);
            if (existing != null) existing.cancel(false);
            var watchdog = CALLER_TURN_WATCHDOG.schedule(() -> {
                synchronized (RealtimeWebSocketListener.this) {
                    pendingCallerTurns.remove(key);
                    if (key.equals(activeCallerTurnKey)) {
                        activeCallerTurnKey = "";
                        finishCallerTurn();
                    }
                    flushDeferredAgentCompletions();
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
            pendingCallerTurns.put(key, watchdog);
        }

        private synchronized void finishPendingCallerTranscription(String itemId) {
            var key = itemId == null ? "" : itemId.trim();
            if (key.isBlank() || !pendingCallerTurns.containsKey(key)) {
                key = !activeCallerTurnKey.isBlank() && pendingCallerTurns.containsKey(activeCallerTurnKey)
                        ? activeCallerTurnKey
                        : pendingCallerTurns.keySet().stream().findFirst().orElse("");
            }
            if (!key.isBlank()) {
                var watchdog = pendingCallerTurns.remove(key);
                if (watchdog != null) watchdog.cancel(false);
                if (key.equals(activeCallerTurnKey)) activeCallerTurnKey = "";
            }
            flushDeferredAgentCompletions();
        }

        private synchronized void cancelCallerTranscriptionWatchdog() {
            pendingCallerTurns.values().forEach(watchdog -> {
                if (watchdog != null) watchdog.cancel(false);
            });
            pendingCallerTurns.clear();
            activeCallerTurnKey = "";
        }

        private synchronized void armResponseCancellationWatchdog() {
            cancelResponseCancellationWatchdog();
            responseCancellationWatchdog = CALLER_TURN_WATCHDOG.schedule(() -> {
                synchronized (RealtimeWebSocketListener.this) {
                    responseCancellationWatchdog = null;
                    if (!responseCancellationPending) return;
                    if (!currentResponseId.isBlank()) ignoredResponseIds.add(currentResponseId);
                    var activeSession = session;
                    if (activeSession != null) {
                        var abandonedRequestId = activeSession.abandonOutstandingResponse();
                        if (!abandonedRequestId.isBlank()) {
                            abandonedResponseRequestIds.add(abandonedRequestId);
                        }
                    }
                    currentResponseId = "";
                    discardCurrentResponseOutput = false;
                    responseCancellationPending = false;
                    responseActive = false;
                    responseInterrupted = false;
                    textCompleted = true;
                    agentText.setLength(0);
                }
            }, RESPONSE_CANCELLATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        private synchronized void cancelResponseCancellationWatchdog() {
            if (responseCancellationWatchdog != null) {
                responseCancellationWatchdog.cancel(false);
                responseCancellationWatchdog = null;
            }
        }

        private synchronized void completeText(String providerText, long generation, String speechId) {
            if (textCompleted) return;
            var rawText = providerText == null || providerText.isBlank()
                    ? agentText.toString().trim()
                    : providerText.trim();
            var finalText = VoiceOutputGuard.speechText(rawText);
            agentText.setLength(0);
            textCompleted = true;
            if (finalText.isBlank()) return;
            var completion = new AgentCompletion(
                    finalText,
                    responseInterrupted,
                    generation,
                    speechId == null || speechId.isBlank() ? "response-unknown" : speechId
            );
            if (hasPendingCallerTranscriptions()) {
                deferredAgentCompletions.addLast(completion);
            } else {
                deliverAgentCompletion(completion);
            }
        }

        private synchronized boolean hasPendingCallerTranscriptions() {
            return !pendingCallerTurns.isEmpty();
        }

        private synchronized void flushDeferredAgentCompletions() {
            if (hasPendingCallerTranscriptions()) return;
            while (!deferredAgentCompletions.isEmpty()) {
                deliverAgentCompletion(deferredAgentCompletions.removeFirst());
            }
        }

        private synchronized void deliverAgentCompletion(AgentCompletion completion) {
            // Model output is released only after the complete message has passed
            // the speech/protocol guard. The telephony service receives one atomic
            // completion and serializes it as one external TTS context.
            if (completion.interrupted() || completion.generation() != turnGeneration) return;
            var id = completion.generation() + ":" + completion.id();
            var fingerprint = completion.generation() + ":" + speechFingerprint(completion.text());
            if (!deliveredSpeechIds.add(id) || !deliveredSpeechFingerprints.add(fingerprint)) return;
            listener.onAgentTextComplete(completion.text(), completion.interrupted());
        }

        private JsonNode completedToolCallItem(JsonNode event) {
            var item = event.path("item");
            return item.isObject() ? item : event;
        }

        private boolean executeCompletedToolCallItem(WebSocket webSocket, JsonNode item, long generation) {
            if (item == null || !item.isObject()) return false;
            var itemType = item.path("type").asText("");
            if (!itemType.isBlank()
                    && !"function_call".equals(itemType)
                    && !"response.function_call_arguments.done".equals(itemType)) return false;
            var callId = item.path("call_id").asText("");
            var name = item.path("name").asText("");
            if (callId.isBlank() || name.isBlank() || !acceptToolCall(callId)) return false;
            executeTool(webSocket, callId, name, item.path("arguments").asText("{}"), generation);
            return true;
        }

        private void recoverProtocolOutput(WebSocket webSocket, long generation) {
            agentText.setLength(0);
            textCompleted = true;
            if (!currentOutputItemId.isBlank()) {
                send(webSocket, Map.of("type", "conversation.item.delete", "item_id", currentOutputItemId));
            }
            LOGGER.warn("Suppressed provider protocol output from caller-facing audio callId={}", call.getId());
            var activeSession = session;
            if (protocolRecoveryAttempts++ == 0 && activeSession != null && generation == turnGeneration) {
                // Retry once with tools disabled. The complete session prompt and
                // configured business facts remain authoritative for the answer.
                activeSession.requestToolResultResponse(generation);
                return;
            }
            var safeText = VoiceOutputGuard.safeResponseFailure(responseLanguage());
            deliverAgentCompletion(new AgentCompletion(safeText, false, generation, "protocol-recovery"));
        }

        private String responseLanguage() {
            var detected = call.getLanguageDetected();
            return detected == null || detected.isBlank()
                    ? call.getAgent().getDefaultLanguage()
                    : detected;
        }

        private void executeTool(
                WebSocket webSocket,
                String callId,
                String name,
                String arguments,
                long generation
        ) {
            var executionKey = name + ":" + canonicalJson(arguments);
            armBusinessActionProgress(executionKey, name, generation);
            var executionObservedAt = System.nanoTime();
            var existingExecution = toolExecutions.containsKey(executionKey);
            lifecycle(
                    existingExecution ? "tool_execution_reused" : "tool_execution_started",
                    "toolCallId=" + safeDiagnosticToken(callId)
                            + " toolName=" + safeDiagnosticToken(name)
                            + " generation=" + generation
                            + " argumentsBytes=" + arguments.getBytes(StandardCharsets.UTF_8).length
            );
            var execution = toolExecutions.computeIfAbsent(executionKey, ignored -> CompletableFuture.supplyAsync(() -> {
                try {
                    return realtimeService.executeTool(call, callId, name, arguments);
                } catch (RuntimeException exception) {
                    return new com.sauti.llm.LlmToolResult(
                            callId, name, false, Map.of(), "Tool execution failed"
                    );
                }
            }));
            execution.thenAccept(result -> {
                clearBusinessActionProgress(executionKey);
                lifecycle(
                        "tool_execution_completed",
                        "toolCallId=" + safeDiagnosticToken(callId)
                                + " toolName=" + safeDiagnosticToken(name)
                                + " generation=" + generation
                                + " activeGeneration=" + turnGeneration
                                + " durationMs=" + TimeUnit.NANOSECONDS.toMillis(
                                        System.nanoTime() - executionObservedAt
                                )
                                + " success=" + result.success()
                );
                send(webSocket, Map.of(
                        "type", "conversation.item.create",
                        "item", Map.of(
                                "type", "function_call_output",
                                "call_id", callId,
                                "output", write(result)
                        )
                ));
                if (!isCurrentGeneration(generation)) {
                    // The accepted operation may finish after caller speech has
                    // advanced the conversation. Its output is retained above
                    // for factual history, but old-turn speech must never be
                    // rebound to the newer generation or talk over the caller.
                    return;
                }
                if (!result.success()) {
                    var safeText = safeToolFailure(name);
                    deliverAgentCompletion(new AgentCompletion(
                            safeText, false, generation, "tool-failure:" + callId
                    ));
                    return;
                }
                if ("end_call".equals(name)
                        && Boolean.TRUE.equals(result.result().get("ended"))) {
                    listener.onCallEndAuthorized(
                            result.result().getOrDefault("outcome", "completed").toString()
                    );
                }
                var nextTool = toolNextTool(result);
                if (!nextTool.isBlank()) {
                    var nextArguments = toolNextToolArguments(result);
                    if (!nextArguments.isBlank()) {
                        var chainedCallId = newRealtimeChainedCallId(callId, nextTool);
                        publishAuthorizedFunctionCall(
                                webSocket, chainedCallId, nextTool, nextArguments
                        );
                        executeTool(
                                webSocket,
                                chainedCallId,
                                nextTool,
                                nextArguments,
                                generation
                        );
                    } else {
                        var activeSession = session;
                        if (activeSession != null) activeSession.requestResponseWithRequiredTool(nextTool);
                    }
                    return;
                }
                var deterministicResponse = toolSpokenResponse(result);
                if (!deterministicResponse.isBlank()) {
                    var activeSession = session;
                    if (activeSession != null && claimToolSpeech(generation)) {
                        // The semantic/tool response is already the final guarded
                        // caller-facing text. Seed it into Realtime history and
                        // send it straight to external TTS instead of paying for
                        // a second model response that can add latency or duplicate
                        // speech.
                        activeSession.seedAssistantText(deterministicResponse);
                        deliverDirectResponse(deterministicResponse);
                        var guidance = toolCallerGuidanceInstruction(name, result);
                        if (!guidance.isBlank()) {
                            activeSession.requestPostBookingGuidance(guidance, generation);
                        }
                    }
                    return;
                }
                if (toolRequiresBusinessAction(result)) {
                    var activeSession = session;
                    if (activeSession != null) activeSession.requestResponse();
                    return;
                }
                var activeSession = session;
                if (activeSession != null) {
                    if (claimToolFollowup(generation)) {
                        activeSession.requestToolResultResponse(generation);
                    }
                } else {
                    send(webSocket, Map.of("type", "response.create"));
                }
            }).exceptionally(error -> {
                var cause = error instanceof java.util.concurrent.CompletionException
                        && error.getCause() != null ? error.getCause() : error;
                lifecycle(
                        "tool_completion_handler_failed",
                        "toolCallId=" + safeDiagnosticToken(callId)
                                + " toolName=" + safeDiagnosticToken(name)
                                + " errorType=" + cause.getClass().getSimpleName()
                );
                return null;
            });
        }

        private void publishAuthorizedFunctionCall(
                WebSocket webSocket,
                String callId,
                String name,
                String arguments
        ) {
            send(webSocket, Map.of(
                    "type", "conversation.item.create",
                    "item", Map.of(
                            "type", "function_call",
                            "status", "completed",
                            "call_id", callId,
                            "name", name,
                            "arguments", arguments
                    )
            ));
        }

        private synchronized void armBusinessActionProgress(
                String executionKey,
                String name,
                long generation
        ) {
            armBusinessActionProgress(executionKey, name, generation, BUSINESS_ACTION_PROGRESS_DELAY_MILLIS);
        }

        private synchronized void armBusinessActionProgress(
                String executionKey,
                String name,
                long generation,
                long delayMillis
        ) {
            if (!supportsBusinessActionProgress(name)
                    || businessActionProgressTimers.containsKey(executionKey)) return;
            var timer = CALLER_TURN_WATCHDOG.schedule(() -> {
                synchronized (RealtimeWebSocketListener.this) {
                    businessActionProgressTimers.remove(executionKey);
                    if (generation != turnGeneration) return;
                    activeBusinessActionProgress.add(executionKey);
                    var activeSession = session;
                    if (activeSession != null) {
                        activeSession.requestBusinessActionProgress(name, executionKey, generation);
                    }
                    armBusinessActionProgress(
                            executionKey,
                            name,
                            generation,
                            BUSINESS_ACTION_PROGRESS_REPEAT_MILLIS
                    );
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
            businessActionProgressTimers.put(executionKey, timer);
        }

        private synchronized void clearBusinessActionProgress(String executionKey) {
            var timer = businessActionProgressTimers.remove(executionKey);
            if (timer != null) timer.cancel(false);
            activeBusinessActionProgress.remove(executionKey);
            var activeSession = session;
            if (activeSession == null) return;
            var abandonedRequestId = activeSession.cancelBusinessActionProgress(executionKey);
            if (abandonedRequestId != null && !abandonedRequestId.isBlank()) {
                abandonedResponseRequestIds.add(abandonedRequestId);
            }
            if (RESPONSE_PURPOSE_BUSINESS_PROGRESS.equals(currentResponsePurpose)
                    && executionKey.equals(currentBusinessActionProgressKey)) {
                if (!currentResponseId.isBlank()) ignoredResponseIds.add(currentResponseId);
                activeSession.cancelProviderResponse(currentResponseId);
                currentResponseId = "";
                discardCurrentResponseOutput = false;
                responseCancellationPending = false;
                responseActive = false;
                responseInterrupted = false;
                textCompleted = true;
                agentText.setLength(0);
                currentResponsePurpose = RESPONSE_PURPOSE_CONVERSATION;
                currentBusinessActionProgressKey = "";
            }
        }

        private synchronized boolean isBusinessActionProgressActive(String executionKey) {
            return executionKey != null && activeBusinessActionProgress.contains(executionKey);
        }

        private String toolSpokenResponse(com.sauti.llm.LlmToolResult result) {
            var value = result.result().get("spokenResponse");
            return value == null ? "" : value.toString().trim();
        }

        private String safeToolFailure(String name) {
            return switch (name) {
                case "check_availability" -> VoiceOutputGuard.safeAvailabilityFailure(responseLanguage());
                case "book_slot" -> VoiceOutputGuard.safeBookingFailure(responseLanguage());
                case "reschedule_booking", "cancel_booking" ->
                        VoiceOutputGuard.safeBookingMutationFailure(responseLanguage(), name);
                default -> VoiceOutputGuard.safeResponseFailure(responseLanguage());
            };
        }

        private String toolCallerGuidanceInstruction(
                String toolName,
                com.sauti.llm.LlmToolResult result
        ) {
            if (!"book_slot".equals(toolName) || !result.success()) return "";
            var value = result.result().get("callerGuidanceInstruction");
            if (value == null) return "";
            var instruction = value.toString().trim();
            return instruction.length() <= 1_200 ? instruction : "";
        }

        private String toolNextTool(com.sauti.llm.LlmToolResult result) {
            var value = result.result().get("nextTool");
            return Boolean.TRUE.equals(result.result().get("nextToolAuthorized"))
                    && value != null
                    && value.toString().matches("[A-Za-z][A-Za-z0-9_]{1,63}")
                    ? value.toString()
                    : "";
        }

        private String toolNextToolArguments(com.sauti.llm.LlmToolResult result) {
            if (!Boolean.TRUE.equals(result.result().get("nextToolAuthorized"))) return "";
            var value = result.result().get("nextToolArguments");
            return value instanceof Map<?, ?> ? write(value) : "";
        }

        private boolean toolRequiresBusinessAction(com.sauti.llm.LlmToolResult result) {
            return "use_business_tool".equals(result.result().get("nextAction"));
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

        private String canonicalJson(String value) {
            try {
                return canonicalJsonNode(objectMapper.readTree(value));
            } catch (Exception ignored) {
                return value == null ? "" : value.trim();
            }
        }

        private String canonicalJsonNode(JsonNode node) throws Exception {
            if (node == null || node.isNull()) return "null";
            if (node.isArray()) {
                var values = new java.util.ArrayList<String>();
                for (var value : node) values.add(canonicalJsonNode(value));
                return "[" + String.join(",", values) + "]";
            }
            if (node.isObject()) {
                var names = new java.util.ArrayList<String>();
                node.fieldNames().forEachRemaining(names::add);
                names.sort(String::compareTo);
                var fields = new java.util.ArrayList<String>();
                for (var name : names) {
                    fields.add(objectMapper.writeValueAsString(name) + ":" + canonicalJsonNode(node.get(name)));
                }
                return "{" + String.join(",", fields) + "}";
            }
            return node.toString();
        }

        private synchronized boolean isCurrentGeneration(long generation) {
            return generation == turnGeneration;
        }

        private synchronized boolean claimToolSpeech(long generation) {
            return generation == turnGeneration && toolSpeechGenerations.add(generation);
        }

        private synchronized boolean claimToolFollowup(long generation) {
            return generation == turnGeneration && toolFollowupGenerations.add(generation);
        }

        private String speechFingerprint(String value) {
            return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                    .strip()
                    .replaceAll("\\s+", " ")
                    .toLowerCase(java.util.Locale.ROOT);
        }

        private record AgentCompletion(String text, boolean interrupted, long generation, String id) { }

        private boolean isOutputItem(JsonNode event, String expectedType) {
            var itemId = event.path("item_id").asText("");
            if (!itemId.isBlank()) return expectedType.equals(outputItemTypes.get(itemId));
            if (!currentOutputItemId.isBlank()) return expectedType.equals(outputItemTypes.get(currentOutputItemId));
            return false;
        }

        private boolean acceptToolCall(String callId) {
            if (callId == null || callId.isBlank() || !processedToolCallIds.add(callId)) return false;
            return true;
        }
    }

    static final class OpenAiTelephonySession implements Session {
        private final WebSocket webSocket;
        private final ObjectMapper objectMapper;
        private final ScheduledFuture<?> keepAliveTask;
        private final Runnable onCancel;
        private final java.util.concurrent.atomic.AtomicInteger expectedResponses = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.ArrayDeque<QueuedResponse> pendingResponses = new java.util.ArrayDeque<>();
        private boolean responseOutstanding;
        private QueuedResponse inFlightResponse;
        private long generation;
        private long responseRequestSequence;
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

        void mirrorCallerTranscript(String transcript) {
            if (transcript == null || transcript.isBlank()) return;
            send(Map.of(
                    "type", "conversation.item.create",
                    "item", Map.of(
                            "type", "message",
                            "role", "user",
                            "content", java.util.List.of(Map.of(
                                    "type", "input_text",
                                    "text", "SAUTI_INPUT_TRANSCRIPT: Text mirror of the immediately preceding caller audio, not a new turn. Prefer it for exact names, digits, emails, dates, and times; combine it with audio for meaning. If it is incoherent or not a clear answer or choice, do not update state, reuse a stored choice, or call a business tool; ask for repetition.\n"
                                            + transcript.trim()
                            ))
                    )
            ));
        }

        @Override
        public void sendUserText(String text) {
            if (text == null || text.isBlank()) return;
            cancelResponse();
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
            synchronized (this) {
                generation++;
                pendingResponses.removeIf(response -> response.generation() != generation);
            }
            onCancel.run();
        }

        void cancelProviderResponse(String responseId) {
            if (responseId == null || responseId.isBlank()) {
                send(Map.of("type", "response.cancel"));
            } else {
                send(Map.of("type", "response.cancel", "response_id", responseId));
            }
        }

        void requestResponse() {
            // updateInstructions(...) installs the full personalized prompt just
            // before this call. Supplying response.instructions here would
            // override it for this turn and drop exact owner-configured facts.
            enqueueResponse(
                    Map.of("type", "response.create"),
                    currentGeneration(), RESPONSE_PURPOSE_CONVERSATION, ""
            );
        }

        void requestToolResultResponse() {
            requestToolResultResponse(currentGeneration());
        }

        void requestToolResultResponse(long expectedGeneration) {
            enqueueResponse(Map.of(
                    "type", "response.create",
                    "response", Map.of(
                            "tool_choice", "none"
                    )
            ), expectedGeneration);
        }

        void requestResponseWithRequiredTool(String toolName) {
            enqueueResponse(Map.of(
                    "type", "response.create",
                    "response", Map.of(
                            "tool_choice", Map.of("type", "function", "name", toolName)
                    )
            ), currentGeneration(), RESPONSE_PURPOSE_CONVERSATION, "");
        }

        void requestExactResponse(String text) {
            requestExactResponse(text, currentGeneration());
        }

        void requestExactResponse(String text, long expectedGeneration) {
            if (text == null || text.isBlank()) return;
            enqueueResponse(Map.of(
                    "type", "response.create",
                    "response", Map.of(
                            "instructions", "Say exactly this text with no additions or omissions: " + text.trim(),
                            "tool_choice", "none"
                    )
            ), expectedGeneration);
        }

        void requestBusinessActionProgress(String toolName, String progressKey, long expectedGeneration) {
            if (progressKey == null || progressKey.isBlank()) return;
            enqueueResponse(Map.of(
                    "type", "response.create",
                    "response", Map.of(
                            "conversation", "none",
                            "instructions", businessActionProgressInstruction(toolName),
                            "tool_choice", "none",
                            "output_modalities", java.util.List.of("text")
                    )
            ), expectedGeneration, RESPONSE_PURPOSE_BUSINESS_PROGRESS, progressKey);
        }

        void requestPostBookingGuidance(String instruction, long expectedGeneration) {
            if (instruction == null || instruction.isBlank()) return;
            enqueueResponse(Map.of(
                    "type", "response.create",
                    "response", Map.of(
                            "instructions", instruction.trim(),
                            "tool_choice", "none",
                            "output_modalities", java.util.List.of("text")
                    )
            ), expectedGeneration, RESPONSE_PURPOSE_POST_BOOKING_GUIDANCE, "");
        }

        private void enqueueResponse(Map<String, ?> response) {
            enqueueResponse(response, currentGeneration());
        }

        private synchronized void enqueueResponse(Map<String, ?> response, long expectedGeneration) {
            enqueueResponse(response, expectedGeneration, RESPONSE_PURPOSE_CONVERSATION, "");
        }

        private synchronized void enqueueResponse(
                Map<String, ?> response,
                long expectedGeneration,
                String purpose,
                String progressKey
        ) {
            if (expectedGeneration != generation) return;
            var requestId = "phone-" + expectedGeneration + "-" + (++responseRequestSequence);
            pendingResponses.addLast(new QueuedResponse(
                    expectedGeneration,
                    requestId,
                    purpose,
                    progressKey,
                    withRequestMetadata(response, requestId)
            ));
            dispatchNextResponse();
        }

        private synchronized void dispatchNextResponse() {
            if (responseOutstanding) return;
            while (!pendingResponses.isEmpty() && pendingResponses.peekFirst().generation() != generation) {
                pendingResponses.removeFirst();
            }
            if (pendingResponses.isEmpty()) return;
            responseOutstanding = true;
            expectedResponses.incrementAndGet();
            inFlightResponse = pendingResponses.removeFirst();
            send(inFlightResponse.payload());
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

        synchronized String abandonOutstandingResponse() {
            if (!responseOutstanding) return "";
            var abandonedRequestId = inFlightResponse == null ? "" : inFlightResponse.requestId();
            if (inFlightResponse != null) {
                expectedResponses.updateAndGet(current -> Math.max(0, current - 1));
            }
            responseOutstanding = false;
            inFlightResponse = null;
            dispatchNextResponse();
            return abandonedRequestId;
        }

        synchronized boolean hasResponseOutstanding() {
            return responseOutstanding;
        }

        synchronized long currentGeneration() {
            return generation;
        }

        synchronized long dispatchedGeneration() {
            return inFlightResponse == null ? generation : inFlightResponse.generation();
        }

        synchronized String dispatchedPurpose() {
            return inFlightResponse == null ? RESPONSE_PURPOSE_CONVERSATION : inFlightResponse.purpose();
        }

        synchronized String dispatchedProgressKey() {
            return inFlightResponse == null ? "" : inFlightResponse.progressKey();
        }

        synchronized String cancelBusinessActionProgress(String progressKey) {
            if (progressKey == null || progressKey.isBlank()) return "";
            pendingResponses.removeIf(response -> RESPONSE_PURPOSE_BUSINESS_PROGRESS.equals(response.purpose())
                    && progressKey.equals(response.progressKey()));
            if (inFlightResponse == null
                    || !RESPONSE_PURPOSE_BUSINESS_PROGRESS.equals(inFlightResponse.purpose())
                    || !progressKey.equals(inFlightResponse.progressKey())) {
                return "";
            }
            var abandonedRequestId = inFlightResponse.requestId();
            expectedResponses.updateAndGet(current -> Math.max(0, current - 1));
            responseOutstanding = false;
            inFlightResponse = null;
            dispatchNextResponse();
            return abandonedRequestId;
        }

        synchronized boolean isDispatchedRequest(String requestId) {
            return requestId == null || requestId.isBlank()
                    || (inFlightResponse != null && requestId.equals(inFlightResponse.requestId()));
        }

        synchronized void discardResponsesBefore(long minimumGeneration) {
            if (minimumGeneration > generation) generation = minimumGeneration;
            pendingResponses.removeIf(response -> response.generation() < generation);
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

        private Map<String, ?> withRequestMetadata(Map<String, ?> event, String requestId) {
            var taggedEvent = new java.util.LinkedHashMap<String, Object>();
            event.forEach(taggedEvent::put);
            var response = new java.util.LinkedHashMap<String, Object>();
            var existingResponse = event.get("response");
            if (existingResponse instanceof Map<?, ?> existing) {
                existing.forEach((key, value) -> {
                    if (key instanceof String name) response.put(name, value);
                });
            }
            var metadata = new java.util.LinkedHashMap<String, Object>();
            var existingMetadata = response.get("metadata");
            if (existingMetadata instanceof Map<?, ?> existing) {
                existing.forEach((key, value) -> {
                    if (key instanceof String name) metadata.put(name, value);
                });
            }
            metadata.put(RESPONSE_REQUEST_ID_METADATA, requestId);
            response.put("metadata", metadata);
            taggedEvent.put("response", response);
            return taggedEvent;
        }

        private record QueuedResponse(
                long generation,
                String requestId,
                String purpose,
                String progressKey,
                Map<String, ?> payload
        ) { }

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
