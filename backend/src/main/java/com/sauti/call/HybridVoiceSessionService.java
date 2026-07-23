package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Bridges complete, generation-scoped OpenAI responses to serialized Cartesia contexts. */
@Service
public class HybridVoiceSessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HybridVoiceSessionService.class);
    private static final int TTS_IDLE_TIMEOUT_SECONDS = 8;
    private static final int PCM_FORWARD_BYTES = 1_280; // 40 ms of 16 kHz mono s16le.
    private static final ScheduledExecutorService TTS_WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "hybrid-tts-watchdog");
        thread.setDaemon(true);
        return thread;
    });

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
                    case "tts_complete" -> completeLegacy(state);
                    case "speak" -> acceptSpeech(
                            state,
                            message.path("generation").asLong(-1L),
                            message.path("id").asText(""),
                            message.path("text").asText("")
                    );
                    case "interrupt" -> interrupt(
                            state,
                            message.has("generation")
                                    ? message.path("generation").asLong(state.clientGeneration + 1L)
                                    : state.clientGeneration + 1L
                    );
                    case "stop_playback" -> stopPlayback(state);
                    case "playback_stalled" -> recoverPlaybackStall(state);
                    case "playback_underrun" -> metrics.playbackUnderrun(
                            "hybrid",
                            state.call.getDirection(),
                            message.path("targetBufferMs").asInt(0)
                    );
                    case "turn_started" -> rememberTurnStart(state, message.path("generation").asLong(-1L));
                    case "playback_started" -> recordPlaybackStart(
                            state,
                            message.path("generation").asLong(-1L),
                            message.path("id").asText("")
                    );
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

    private void synthesize(HybridSession state, String text) {
        // The message has already been held and validated in full. Sending one
        // final Cartesia generation preserves prosody and avoids audible seams
        // from needlessly re-splitting a complete utterance into continuations.
        writeTts(state, text, true);
    }

    private void completeLegacy(HybridSession state) {
        var safeText = VoiceOutputGuard.speechText(state.rawResponseText.toString());
        state.rawResponseText.setLength(0);
        if (safeText.isBlank()) return;
        acceptSpeech(state, state.clientGeneration, "legacy-" + (++state.legacySpeechSequence), safeText);
    }

    private void acceptSpeech(HybridSession state, long generation, String speechId, String text) {
        if (generation < 0L || speechId == null || speechId.isBlank() || generation < state.clientGeneration) return;
        if (generation > state.clientGeneration) {
            var hadOldOutput = state.currentSpeech != null || !state.pendingSpeech.isEmpty() || state.speaking;
            state.clientGeneration = generation;
            state.pendingSpeech.clear();
            state.currentSpeech = null;
            state.acceptedSpeechIds.clear();
            state.acceptedSpeechFingerprints.clear();
            state.speechTimings.clear();
            state.turnStartedNanos.entrySet().removeIf(entry -> entry.getKey() != generation);
            state.rawResponseText.setLength(0);
            if (hadOldOutput) resetTts(state, true);
        }
        var safeText = VoiceOutputGuard.speechText(text);
        if (safeText.isBlank()) return;
        var idKey = generation + ":" + speechId.trim();
        var fingerprint = generation + ":" + speechFingerprint(safeText);
        if (!state.acceptedSpeechIds.add(idKey) || !state.acceptedSpeechFingerprints.add(fingerprint)) return;
        var acceptedNanos = System.nanoTime();
        var turnStartedNanos = state.turnStartedNanos.remove(generation);
        var timingKey = speechKey(generation, speechId);
        state.speechTimings.put(timingKey, new SpeechTiming(generation, acceptedNanos, turnStartedNanos));
        trimTimings(state);
        if (turnStartedNanos != null) {
            metrics.recordLatency(
                    "transcript_to_speech_ready", "hybrid", state.call.getDirection(),
                    acceptedNanos - turnStartedNanos
            );
        }
        state.pendingSpeech.addLast(new Speech(generation, speechId.trim(), safeText, timingKey));
        startNextSpeech(state);
    }

    private void startNextSpeech(HybridSession state) {
        if (state.currentSpeech != null || state.pendingSpeech.isEmpty()) return;
        var next = state.pendingSpeech.removeFirst();
        if (next.generation() != state.clientGeneration) {
            startNextSpeech(state);
            return;
        }
        if (state.ttsUnavailable) resetTts(state, false);
        state.currentSpeech = next;
        state.currentSpeechFirstAudioRecorded = false;
        armSpeechWatchdog(state, next, state.ttsGeneration);
        synthesize(state, next.text());
    }

    private void interrupt(HybridSession state, long generation) {
        if (generation <= state.clientGeneration) return;
        var hadOldOutput = state.currentSpeech != null || !state.pendingSpeech.isEmpty() || state.speaking;
        state.clientGeneration = generation;
        state.pendingSpeech.clear();
        state.currentSpeech = null;
        state.acceptedSpeechIds.clear();
        state.acceptedSpeechFingerprints.clear();
        state.turnStartedNanos.entrySet().removeIf(entry -> entry.getKey() < generation);
        state.speechTimings.entrySet().removeIf(entry -> entry.getValue().generation() < generation);
        state.rawResponseText.setLength(0);
        if (hadOldOutput) {
            metrics.interruption("hybrid", state.call.getDirection());
            resetTts(state, true);
        }
    }

    private void stopPlayback(HybridSession state) {
        var hadOldOutput = state.currentSpeech != null || !state.pendingSpeech.isEmpty() || state.speaking;
        state.pendingSpeech.clear();
        state.currentSpeech = null;
        state.rawResponseText.setLength(0);
        if (hadOldOutput) resetTts(state, true);
    }

    private void recoverPlaybackStall(HybridSession state) {
        if (state.currentSpeech == null && !state.speaking) return;
        LOGGER.warn("Hybrid browser playback drained before Cartesia completed for call={}",
                state.call.getTwilioCallSid());
        metrics.failure("hybrid", state.call.getDirection(), "playback_stalled");
        state.currentSpeech = null;
        state.pendingSpeech.clear();
        resetTts(state, true);
    }

    private void resetTts(HybridSession state, boolean clearAudio) {
        cancelSpeechWatchdog(state);
        state.ttsGeneration++;
        state.pcmForwardBuffer.reset();
        closeTts(state);
        if (clearAudio) sendJson(state, Map.of("type", "clear_audio"));
        if (state.speaking) {
            state.speaking = false;
            sendJson(state, Map.of("type", "speaking", "value", false));
        }
        openTts(state);
    }

    private void openTts(HybridSession state) {
        var generation = state.ttsGeneration;
        state.ttsUnavailable = false;
        var language = state.call.getLanguageDetected() == null
                ? state.call.getAgent().getDefaultLanguage()
                : state.call.getLanguageDetected();
        state.tts = ttsProvider.open(language, state.call.getAgent().getTtsVoiceId(), new TtsAudioListener() {
            @Override
            public void onPcmAudio(byte[] audio) {
                synchronized (state) {
                    if (!state.current(generation)) return;
                    if (state.currentSpeech != null) {
                        armSpeechWatchdog(state, state.currentSpeech, generation);
                    }
                    state.pcmForwardBuffer.writeBytes(audio);
                    if (state.pcmForwardBuffer.size() >= PCM_FORWARD_BYTES) {
                        flushPcm(state, generation);
                    }
                }
            }

            @Override
            public void onComplete() {
                synchronized (state) {
                    if (!state.current(generation)) return;
                    flushPcm(state, generation);
                    cancelSpeechWatchdog(state);
                    state.speaking = false;
                    state.currentSpeech = null;
                    state.currentSpeechFirstAudioRecorded = false;
                    sendJson(state, Map.of("type", "speaking", "value", false));
                    startNextSpeech(state);
                }
            }

            @Override
            public void onError(Throwable error) {
                synchronized (state) {
                    if (!state.current(generation)) return;
                    LOGGER.warn("Hybrid Cartesia stream failed for call={}", state.call.getTwilioCallSid(), error);
                    failCurrentSpeech(
                            state,
                            generation,
                            "tts_stream",
                            "The selected voice stopped unexpectedly. Please try again."
                    );
                }
            }
        }).whenComplete((tts, error) -> {
            synchronized (state) {
                if (!state.current(generation) && tts != null) tts.close();
                if (error != null && state.current(generation)) {
                    failCurrentSpeech(
                            state,
                            generation,
                            "tts_connect",
                            "The selected voice could not be connected. Please try again."
                    );
                }
            }
        });
    }

    private void armSpeechWatchdog(HybridSession state, Speech speech, int generation) {
        cancelSpeechWatchdog(state);
        state.speechWatchdog = TTS_WATCHDOG.schedule(() -> {
            synchronized (state) {
                if (!state.current(generation) || state.currentSpeech != speech) return;
                LOGGER.warn("Hybrid Cartesia completion timed out for call={}", state.call.getTwilioCallSid());
                failCurrentSpeech(
                        state,
                        generation,
                        "tts_timeout",
                        "Voice playback stopped before the response completed. Please try again."
                );
            }
        }, TTS_IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelSpeechWatchdog(HybridSession state) {
        if (state.speechWatchdog != null) {
            state.speechWatchdog.cancel(false);
            state.speechWatchdog = null;
        }
    }

    private void failCurrentSpeech(HybridSession state, int generation, String stage, String message) {
        if (!state.current(generation) || state.ttsUnavailable) return;
        cancelSpeechWatchdog(state);
        state.currentSpeech = null;
        state.pendingSpeech.clear();
        state.pcmForwardBuffer.reset();
        state.speaking = false;
        metrics.failure("hybrid", state.call.getDirection(), stage);
        sendJson(state, Map.of("type", "speaking", "value", false));
        sendJson(state, Map.of("type", "error", "message", message));
        state.ttsGeneration++;
        state.ttsUnavailable = true;
        closeTts(state);
    }

    private void writeTts(HybridSession state, String text, boolean flush) {
        var generation = state.ttsGeneration;
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

    private void flushPcm(HybridSession state, int generation) {
        if (!state.current(generation) || state.pcmForwardBuffer.size() == 0) return;
        var audio = state.pcmForwardBuffer.toByteArray();
        state.pcmForwardBuffer.reset();
        if (state.currentSpeech != null && !state.currentSpeechFirstAudioRecorded) {
            state.currentSpeechFirstAudioRecorded = true;
            recordFirstAudio(state, state.currentSpeech);
        }
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

    private void rememberTurnStart(HybridSession state, long generation) {
        if (generation < state.clientGeneration || generation < 0L) return;
        state.turnStartedNanos.put(generation, System.nanoTime());
        while (state.turnStartedNanos.size() > 32) {
            var oldest = state.turnStartedNanos.keySet().iterator().next();
            state.turnStartedNanos.remove(oldest);
        }
    }

    private void recordFirstAudio(HybridSession state, Speech speech) {
        var timing = state.speechTimings.get(speech.timingKey());
        if (timing == null) return;
        var now = System.nanoTime();
        metrics.recordLatency(
                "speech_ready_to_first_audio", "hybrid", state.call.getDirection(),
                now - timing.acceptedNanos()
        );
        if (timing.turnStartedNanos() != null) {
            metrics.recordLatency(
                    "transcript_to_first_audio", "hybrid", state.call.getDirection(),
                    now - timing.turnStartedNanos()
            );
        }
    }

    private void recordPlaybackStart(HybridSession state, long generation, String speechId) {
        if (generation < 0L || speechId == null || speechId.isBlank()) return;
        var timing = state.speechTimings.remove(speechKey(generation, speechId));
        if (timing == null) return;
        var now = System.nanoTime();
        metrics.recordLatency(
                "speech_ready_to_playback", "hybrid", state.call.getDirection(),
                now - timing.acceptedNanos()
        );
        if (timing.turnStartedNanos() != null) {
            metrics.recordLatency(
                    "transcript_to_playback", "hybrid", state.call.getDirection(),
                    now - timing.turnStartedNanos()
            );
        }
    }

    private void trimTimings(HybridSession state) {
        while (state.speechTimings.size() > 32) {
            var oldest = state.speechTimings.keySet().iterator().next();
            state.speechTimings.remove(oldest);
        }
    }

    private static String speechKey(long generation, String speechId) {
        return generation + ":" + speechId.trim();
    }

    private static final class HybridSession {
        private final Call call;
        private final WebSocketSession socket;
        private final StringBuilder rawResponseText = new StringBuilder();
        private final ArrayDeque<Speech> pendingSpeech = new ArrayDeque<>();
        private final Set<String> acceptedSpeechIds = new HashSet<>();
        private final Set<String> acceptedSpeechFingerprints = new HashSet<>();
        private final Map<Long, Long> turnStartedNanos = new LinkedHashMap<>();
        private final Map<String, SpeechTiming> speechTimings = new LinkedHashMap<>();
        private final ByteArrayOutputStream pcmForwardBuffer =
                new ByteArrayOutputStream(PCM_FORWARD_BYTES * 2);
        private final Runnable onClose;
        private final long startedNanos = System.nanoTime();
        private CompletableFuture<RealtimeTtsSession> tts = CompletableFuture.completedFuture(null);
        private CompletableFuture<Void> ttsWrites = CompletableFuture.completedFuture(null);
        private long clientGeneration;
        private long legacySpeechSequence;
        private int ttsGeneration;
        private Speech currentSpeech;
        private boolean speaking;
        private boolean firstAudioRecorded;
        private boolean currentSpeechFirstAudioRecorded;
        private ScheduledFuture<?> speechWatchdog;
        private boolean ttsUnavailable;
        private boolean closed;

        private HybridSession(Call call, WebSocketSession socket, Runnable onClose) {
            this.call = call;
            this.socket = socket;
            this.onClose = onClose;
        }

        private boolean current(int expectedGeneration) {
            return !closed && ttsGeneration == expectedGeneration && socket.isOpen();
        }

        private synchronized void close() {
            if (closed) return;
            closed = true;
            if (speechWatchdog != null) speechWatchdog.cancel(false);
            speechWatchdog = null;
            ttsGeneration++;
            pendingSpeech.clear();
            currentSpeech = null;
            pcmForwardBuffer.reset();
            turnStartedNanos.clear();
            speechTimings.clear();
            onClose.run();
            tts.thenAccept(session -> { if (session != null) session.close(); });
            tts = CompletableFuture.completedFuture(null);
            ttsWrites = CompletableFuture.completedFuture(null);
        }
    }

    private static String speechFingerprint(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private record Speech(long generation, String id, String text, String timingKey) { }

    private record SpeechTiming(long generation, long acceptedNanos, Long turnStartedNanos) { }
}
