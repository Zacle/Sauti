package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Bridges OpenAI Realtime text deltas to one warm Cartesia Sonic context. */
@Service
public class HybridVoiceSessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HybridVoiceSessionService.class);

    private final CallRepository callRepository;
    private final RealtimeTextToSpeechProvider ttsProvider;
    private final ObjectMapper objectMapper;
    private final VoiceRuntimeMetrics metrics;
    private final Map<String, HybridSession> sessions = new ConcurrentHashMap<>();

    public HybridVoiceSessionService(
            CallRepository callRepository,
            RealtimeTextToSpeechProvider ttsProvider,
            ObjectMapper objectMapper,
            VoiceRuntimeMetrics metrics
    ) {
        this.callRepository = callRepository;
        this.ttsProvider = ttsProvider;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public void start(String callSid, String agentKey, WebSocketSession socket) {
        var call = callRepository.findByTwilioCallSid(callSid)
                .filter(Call::isActive)
                .filter(candidate -> "test".equals(candidate.getDirection()) || "web".equals(candidate.getDirection()))
                .filter(candidate -> agentKey.equals(candidate.getAgent().getId().toString())
                        || agentKey.equals(candidate.getAgent().getWebVoicePublicId()))
                .orElseThrow(() -> new IllegalArgumentException("Hybrid voice call is unavailable"));
        var state = new HybridSession(call, socket, () -> metrics.sessionEnded("hybrid", call.getDirection()));
        var previous = sessions.put(callSid, state);
        if (previous != null) previous.close();
        metrics.sessionStarted("hybrid", call.getDirection());
        synchronized (state) {
            openTts(state);
        }
        sendJson(state, Map.of("type", "connected", "mode", "hybrid_realtime"));
    }

    public void accept(String callSid, String payload) {
        var state = sessions.get(callSid);
        if (state == null) return;
        try {
            var message = objectMapper.readTree(payload);
            var type = message.path("type").asText("");
            synchronized (state) {
                if (state.closed) return;
                switch (type) {
                    case "tts_delta" -> appendDelta(state, message.path("text").asText(""));
                    case "tts_complete" -> complete(state);
                    case "interrupt" -> interrupt(state);
                    default -> { }
                }
            }
        } catch (Exception exception) {
            LOGGER.debug("Ignoring malformed hybrid voice message for call={}", callSid, exception);
        }
    }

    public void stop(String callSid) {
        var state = sessions.remove(callSid);
        if (state != null) state.close();
    }

    private void appendDelta(HybridSession state, String delta) {
        if (delta == null || delta.isEmpty()) return;
        state.rawResponseText.append(delta);
    }

    private void appendValidatedDelta(HybridSession state, String delta) {
        if (delta == null || delta.isEmpty()) return;
        state.textBuffer.append(delta);
        String phrase;
        while (!(phrase = WebVoiceSessionService.takeSpeakablePhrase(state.textBuffer, false)).isBlank()) {
            speak(state, phrase, false);
        }
    }

    private void complete(HybridSession state) {
        var safeText = VoiceOutputGuard.speechText(state.rawResponseText.toString());
        state.rawResponseText.setLength(0);
        if (safeText.isBlank()) {
            state.textBuffer.setLength(0);
            return;
        }
        appendValidatedDelta(state, safeText);
        var remaining = WebVoiceSessionService.takeSpeakablePhrase(state.textBuffer, true);
        if (!remaining.isBlank()) speak(state, remaining, false);
        speak(state, "", true);
    }

    private void interrupt(HybridSession state) {
        metrics.interruption("hybrid", state.call.getDirection());
        state.generation++;
        state.textBuffer.setLength(0);
        state.rawResponseText.setLength(0);
        closeTts(state);
        sendJson(state, Map.of("type", "clear_audio"));
        if (state.speaking) {
            state.speaking = false;
            sendJson(state, Map.of("type", "speaking", "value", false));
        }
        openTts(state);
    }

    private void openTts(HybridSession state) {
        var generation = state.generation;
        var language = state.call.getLanguageDetected() == null
                ? state.call.getAgent().getDefaultLanguage()
                : state.call.getLanguageDetected();
        state.tts = ttsProvider.open(language, state.call.getAgent().getTtsVoiceId(), new TtsAudioListener() {
            @Override
            public void onPcmAudio(byte[] audio) {
                synchronized (state) {
                    if (!state.current(generation)) return;
                    if (!state.speaking) {
                        state.speaking = true;
                        if (!state.firstAudioRecorded) {
                            state.firstAudioRecorded = true;
                            metrics.recordLatency(
                                    "session_to_first_audio", "hybrid", state.call.getDirection(),
                                    System.nanoTime() - state.startedNanos
                            );
                        }
                        sendJson(state, Map.of("type", "speaking", "value", true));
                    }
                    sendBinary(state, audio);
                }
            }

            @Override
            public void onComplete() {
                synchronized (state) {
                    if (!state.current(generation)) return;
                    state.speaking = false;
                    sendJson(state, Map.of("type", "speaking", "value", false));
                }
            }

            @Override
            public void onError(Throwable error) {
                synchronized (state) {
                    if (!state.current(generation)) return;
                    LOGGER.warn("Hybrid Cartesia stream failed for call={}", state.call.getTwilioCallSid(), error);
                    metrics.failure("hybrid", state.call.getDirection(), "tts_stream");
                    sendJson(state, Map.of("type", "error", "message", "The selected voice could not speak. Please try again."));
                }
            }
        }).whenComplete((tts, error) -> {
            synchronized (state) {
                if (!state.current(generation) && tts != null) tts.close();
                if (error != null && state.current(generation)) {
                    metrics.failure("hybrid", state.call.getDirection(), "tts_connect");
                    sendJson(state, Map.of("type", "error", "message", "The selected voice could not be connected."));
                }
            }
        });
    }

    private void speak(HybridSession state, String text, boolean flush) {
        var generation = state.generation;
        var targetTts = state.tts;
        // A response can produce several phrases before Cartesia's WebSocket
        // finishes opening. CompletableFuture dependants are not FIFO, so chain
        // every write explicitly and guarantee that the final flush is last.
        state.ttsWrites = state.ttsWrites
                .handle((ignored, error) -> null)
                .thenCompose(ignored -> targetTts.thenAccept(tts -> {
                    synchronized (state) {
                        if (tts == null || !state.current(generation)) return;
                        try {
                            tts.speak(text, flush);
                        } catch (Exception exception) {
                            LOGGER.warn("Unable to stream hybrid speech for call={}", state.call.getTwilioCallSid(), exception);
                            metrics.failure("hybrid", state.call.getDirection(), "tts_write");
                            sendJson(state, Map.of("type", "error", "message", "The selected voice could not speak."));
                        }
                    }
                }));
    }

    private void closeTts(HybridSession state) {
        state.tts.thenAccept(tts -> {
            if (tts != null) tts.close();
        });
        state.tts = CompletableFuture.completedFuture(null);
        state.ttsWrites = CompletableFuture.completedFuture(null);
    }

    private void sendJson(HybridSession state, Map<String, ?> payload) {
        try {
            if (state.socket.isOpen()) state.socket.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception exception) {
            LOGGER.debug("Hybrid event delivery failed for call={}", state.call.getTwilioCallSid(), exception);
        }
    }

    private void sendBinary(HybridSession state, byte[] audio) {
        try {
            if (state.socket.isOpen()) state.socket.sendMessage(new BinaryMessage(audio));
        } catch (Exception exception) {
            LOGGER.debug("Hybrid audio delivery failed for call={}", state.call.getTwilioCallSid(), exception);
        }
    }

    private static final class HybridSession {
        private final Call call;
        private final WebSocketSession socket;
        private final StringBuilder textBuffer = new StringBuilder();
        private final StringBuilder rawResponseText = new StringBuilder();
        private final Runnable onClose;
        private final long startedNanos = System.nanoTime();
        private CompletableFuture<RealtimeTtsSession> tts = CompletableFuture.completedFuture(null);
        private CompletableFuture<Void> ttsWrites = CompletableFuture.completedFuture(null);
        private int generation;
        private boolean speaking;
        private boolean firstAudioRecorded;
        private boolean closed;

        private HybridSession(Call call, WebSocketSession socket, Runnable onClose) {
            this.call = call;
            this.socket = socket;
            this.onClose = onClose;
        }

        private boolean current(int expectedGeneration) {
            return !closed && generation == expectedGeneration && socket.isOpen();
        }

        private synchronized void close() {
            if (closed) return;
            closed = true;
            generation++;
            textBuffer.setLength(0);
            onClose.run();
            tts.thenAccept(session -> { if (session != null) session.close(); });
            tts = CompletableFuture.completedFuture(null);
            ttsWrites = CompletableFuture.completedFuture(null);
        }
    }
}
