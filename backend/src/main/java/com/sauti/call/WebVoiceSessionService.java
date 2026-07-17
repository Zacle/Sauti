package com.sauti.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class WebVoiceSessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebVoiceSessionService.class);
    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();
    private final CallRepository callRepository;
    private final CallPipelineService callPipelineService;
    private final RealtimeSpeechToTextProvider sttProvider;
    private final OpenAiRealtimeTranscriptionService openAiRealtimeTranscriptionService;
    private final RealtimeTextToSpeechProvider ttsProvider;
    private final SentenceChunker sentenceChunker;
    private final ObjectMapper objectMapper;
    private final CallRecordingService recordingService;
    private final ScheduledExecutorService maintenanceExecutor = Executors.newScheduledThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "web-voice-maintenance");
        thread.setDaemon(true);
        return thread;
    });

    public WebVoiceSessionService(
            CallRepository callRepository,
            CallPipelineService callPipelineService,
            RealtimeSpeechToTextProvider sttProvider,
            OpenAiRealtimeTranscriptionService openAiRealtimeTranscriptionService,
            RealtimeTextToSpeechProvider ttsProvider,
            SentenceChunker sentenceChunker,
            ObjectMapper objectMapper,
            CallRecordingService recordingService
    ) {
        this.callRepository = callRepository;
        this.callPipelineService = callPipelineService;
        this.sttProvider = sttProvider;
        this.openAiRealtimeTranscriptionService = openAiRealtimeTranscriptionService;
        this.ttsProvider = ttsProvider;
        this.sentenceChunker = sentenceChunker;
        this.objectMapper = objectMapper;
        this.recordingService = recordingService;
    }

    public void start(String callSid, String publicAgentId, WebSocketSession socket) {
        var call = callRepository.findByTwilioCallSid(callSid)
                .filter(Call::isActive)
                .filter(candidate -> "web".equals(candidate.getDirection()) || "test".equals(candidate.getDirection()))
                .filter(candidate -> publicAgentId.equals(candidate.getAgent().getWebVoicePublicId())
                        || publicAgentId.equals(candidate.getAgent().getId().toString()))
                .orElseThrow(() -> new IllegalArgumentException("Web Voice session is unavailable"));
        var state = new BrowserSession(call, socket);
        var previous = sessions.put(callSid, state);
        if (previous != null) previous.close(false);
        sendJson(state, Map.of("type", "connected", "sessionId", callSid, "sampleRate", 16000));
        connectRealtimeStt(state, false);
        state.maintenanceTask = maintenanceExecutor.scheduleAtFixedRate(
                () -> state.enqueue(() -> maintain(state)),
                1,
                1,
                TimeUnit.SECONDS
        );
        var greeting = callPipelineService.openingGreeting(call);
        if (!greeting.isBlank()) {
            sendJson(state, Map.of("type", "agent_response", "text", greeting, "language", state.language));
            speak(state, greeting, state.language, false);
        }
    }

    private void connectRealtimeStt(BrowserSession state, boolean forceFallback) {
        var listener = new RealtimeTranscriptListener() {
            @Override
            public void onPartialTranscript(String transcript, double confidence) {
                state.enqueue(() -> onPartial(state, transcript, confidence));
            }

            @Override
            public void onFinalTranscript(String transcript) {
                state.enqueue(() -> onFinal(state, transcript));
            }

            @Override
            public void onError(Throwable error) {
                recoverRealtimeStt(state, error);
            }
        };
        state.sttSession = forceFallback
                ? sttProvider.open(state.call.getAgent(), listener)
                : openRealtimeStt(state.call.getAgent(), listener);
        state.sttSession.whenComplete((session, error) -> {
            if (error != null) recoverRealtimeStt(state, error);
            else state.sttRecovering.set(false);
        });
    }

    private void recoverRealtimeStt(BrowserSession state, Throwable error) {
        if (state.terminating || !state.socket.isOpen() || !state.sttRecovering.compareAndSet(false, true)) return;
        LOGGER.warn("Web Voice STT failed for session={}; reconnecting with fallback", state.call.getTwilioCallSid(), error);
        state.enqueue(() -> {
            if (state.sttRecoveryAttempts++ >= 2) {
                sendJson(state, Map.of("type", "error", "message", "Speech recognition became unavailable"));
                return;
            }
            connectRealtimeStt(state, true);
        });
    }

    private CompletableFuture<RealtimeSttSession> openRealtimeStt(
            com.sauti.agent.Agent agent,
            RealtimeTranscriptListener listener
    ) {
        var requiresOpenAi = agent != null && agent.getSupportedLanguages().stream()
                .anyMatch(language -> !java.util.Set.of("en", "fr").contains(language));
        if (openAiRealtimeTranscriptionService.isConfigured() && requiresOpenAi) {
            return openAiRealtimeTranscriptionService.open(agent, listener);
        }
        return sttProvider.open(agent, listener);
    }

    public void acceptAudio(String callSid, byte[] pcm16Audio) {
        var state = sessions.get(callSid);
        if (state == null || pcm16Audio == null || pcm16Audio.length == 0 || pcm16Audio.length > 65_536) return;
        if (state.recording != null) state.recording.appendCaller(pcm16Audio);
        state.sttSession.thenAccept(session -> session.sendPcmAudio(pcm16Audio));
    }

    public void interrupt(String callSid) {
        var state = sessions.get(callSid);
        if (state == null) return;
        if (!state.speaking && !state.awaitingAudio) return;
        state.interruptSpeech();
        state.markCallerActivity();
        sendJson(state, Map.of("type", "clear_audio"));
        sendJson(state, Map.of("type", "speaking", "value", false));
    }

    public void stop(String callSid) {
        var state = sessions.remove(callSid);
        if (state == null) return;
        state.close(true);
        persistRecording(state);
        callPipelineService.completeActiveCall(callSid, "completed");
    }

    private void onPartial(BrowserSession state, String transcript, double confidence) {
        if (transcript == null || transcript.isBlank()) return;
        state.beginCallerSpeech();
        sendJson(state, Map.of("type", "transcript_partial", "text", transcript, "confidence", confidence));
        var sensitivity = state.call.getAgent().getBargeInSensitivity();
        var threshold = Math.max(0.45, Math.min(0.90, 0.83 - (0.4 * sensitivity)));
        if (state.speaking
                && confidence >= threshold
                && state.callerSpeechMillis() >= state.call.getAgent().getBargeInGraceMs()) {
            state.interruptSpeech();
            sendJson(state, Map.of("type", "clear_audio"));
            sendJson(state, Map.of("type", "speaking", "value", false));
        }
    }

    private void onFinal(BrowserSession state, String transcript) {
        if (!CallerTranscriptGuard.accepts(transcript) || !state.call.isActive()) return;
        state.markCallerActivity();
        state.finishCallerSpeech();
        sendJson(state, Map.of("type", "transcript_final", "text", transcript));
        try {
            var streamed = new AtomicBoolean(false);
            var spokenText = new StringBuilder();
            var speechBuffer = new StringBuilder();
            var turn = callPipelineService.processLiveTranscriptTurn(
                    state.call.getTwilioCallSid(),
                    transcript,
                    delta -> {
                        if (delta == null || delta.isEmpty()) return;
                        spokenText.append(delta);
                        speechBuffer.append(delta);
                        var speakable = takeSpeakablePhrase(speechBuffer, false);
                        if (speakable.isBlank()) return;
                        if (streamed.compareAndSet(false, true)) beginSpeech(state, state.language, false);
                        state.ttsSession.thenAccept(tts -> {
                            if (tts != null) tts.speak(speakable, false);
                        });
                    }
            );
            state.language = turn.language();
            if (!turn.text().isBlank()) {
                var remainingSpeech = takeSpeakablePhrase(speechBuffer, true);
                if (!remainingSpeech.isBlank()) {
                    if (streamed.compareAndSet(false, true)) beginSpeech(state, turn.language(), false);
                    state.ttsSession.thenAccept(tts -> {
                        if (tts != null) tts.speak(remainingSpeech, false);
                    });
                }
                var displayedText = spokenText.toString().trim();
                if (displayedText.isBlank()) displayedText = turn.text();
                state.awaitingDictatedDetail = requestsDictatedDetails(displayedText);
                sendJson(state, Map.of("type", "agent_response", "text", displayedText, "language", turn.language()));
                state.closeOutcome = turn.outcome();
                if (streamed.get()) {
                    state.closeAfterSpeech = !turn.outcome().isBlank();
                    state.ttsSession.thenAccept(tts -> {
                        if (tts != null) tts.speak("", true);
                    });
                } else {
                    speak(state, turn.text(), turn.language(), !turn.outcome().isBlank());
                }
            } else if (!turn.outcome().isBlank()) {
                sendJson(state, Map.of("type", "ended", "outcome", turn.outcome()));
                closeSocket(state);
            }
        } catch (Exception exception) {
            LOGGER.warn("Web Voice turn failed for session={}", state.call.getTwilioCallSid(), exception);
            sendJson(state, Map.of("type", "error", "message", "The agent could not complete that turn"));
        }
    }

    static String takeSpeakablePhrase(StringBuilder buffer, boolean complete) {
        if (buffer.isEmpty()) return "";
        if (complete) {
            var remaining = buffer.toString().trim();
            buffer.setLength(0);
            return remaining.isBlank() ? "" : remaining + " ";
        }
        var boundary = -1;
        for (int index = 0; index < buffer.length(); index++) {
            var current = buffer.charAt(index);
            if (SentenceChunker.isSentenceBoundary(buffer, index, false)
                    || (index >= 72 && (current == ';' || current == ':' || current == '—'))
                    || (index >= 120 && current == ',')) {
                boundary = index + 1;
                break;
            }
        }
        if (boundary < 0 && buffer.length() >= 170) {
            boundary = buffer.lastIndexOf(" ", 150);
            if (boundary < 100) boundary = -1;
        }
        if (boundary < 0) return "";
        while (boundary < buffer.length() && Character.isWhitespace(buffer.charAt(boundary))) {
            boundary++;
        }
        var phrase = buffer.substring(0, boundary).trim();
        buffer.delete(0, boundary);
        return phrase.isBlank() ? "" : phrase + " ";
    }

    private void speak(BrowserSession state, String text, String language, boolean closeAfterSpeech) {
        beginSpeech(state, language, closeAfterSpeech);
        state.ttsSession.thenAccept(tts -> {
            if (tts == null) return;
            for (var chunk : sentenceChunker.chunks(text)) tts.speak(chunk, false);
            tts.speak("", true);
        });
    }

    private void beginSpeech(BrowserSession state, String language, boolean closeAfterSpeech) {
        state.openTts(language);
        state.closeAfterSpeech = closeAfterSpeech;
        if (!closeAfterSpeech) state.closeOutcome = "";
        else if (state.closeOutcome == null || state.closeOutcome.isBlank()) state.closeOutcome = "completed";
        state.markActivity();
        var requestVersion = ++state.audioRequestVersion;
        state.awaitingAudio = true;
        maintenanceExecutor.schedule(
                () -> state.enqueue(() -> {
                    if (state.awaitingAudio && state.audioRequestVersion == requestVersion && !state.terminating) {
                        state.interruptSpeech();
                        sendJson(state, Map.of("type", "error", "message", "Voice playback did not start. Please try again."));
                    }
                }),
                6,
                TimeUnit.SECONDS
        );
    }

    private void maintain(BrowserSession state) {
        if (state.terminating || !state.socket.isOpen()) return;
        var agent = state.call.getAgent();
        if (state.elapsedSeconds() >= agent.getMaxCallDurationSeconds()) {
            finishWithMessage(
                    state,
                    localized(state,
                            "Asante kwa kupiga simu. Muda wetu umefika mwisho, kwa hiyo nitamaliza mazungumzo sasa. Kwaheri.",
                            "Merci de votre appel. Nous sommes arrivés à la durée maximale, je vais donc terminer la conversation. Au revoir.",
                            "شكرًا لاتصالك. لقد وصلنا إلى الحد الأقصى لمدة المحادثة، لذا سأنهيها الآن. مع السلامة.",
                            "Thank you for calling. We have reached the maximum conversation time, so I will end now. Goodbye."),
                    "max_duration"
            );
            return;
        }
        if (state.speaking) return;
        long silence = state.silentSeconds();
        var reminderSeconds = Math.max(30, agent.getReminderAfterSilenceSeconds());
        var finalGraceSeconds = 30;
        var endSilenceSeconds = Math.max(agent.getEndCallOnSilenceSeconds(), reminderSeconds + finalGraceSeconds);
        if (state.shouldEndAfterFinalReminder(finalGraceSeconds, agent.getMaxReminders())
                || silence >= endSilenceSeconds) {
            finishWithMessage(
                    state,
                    localized(state,
                            "Asante kwa kuwasiliana nasi. Kwa kuwa sikusikii, nitamaliza mazungumzo sasa. Kwaheri.",
                            "Merci de nous avoir contactés. Comme je ne vous entends plus, je vais terminer la conversation. Au revoir.",
                            "شكرًا لتواصلك معنا. بما أنني لا أسمع ردًا، سأنهي المحادثة الآن. مع السلامة.",
                            "Thank you for contacting us. Since I cannot hear a response, I will end the conversation now. Goodbye."),
                    "no_response"
            );
            return;
        }
        if (state.canSendReminder(reminderSeconds, agent.getMaxReminders())) {
            state.markReminderSent();
            var message = localized(state,
                    "Bado uko hapo? Chukua muda wako, kisha uniambie ukiwa tayari.",
                    "Êtes-vous toujours là ? Prenez votre temps et dites-moi quand vous êtes prêt.",
                    "هل ما زلت معي؟ خذ وقتك وأخبرني عندما تكون مستعدًا.",
                    "Are you still there? Take your time, and let me know when you are ready.");
            callPipelineService.appendAutomatedAgentMessage(state.call.getTwilioCallSid(), callLanguage(state), message);
            sendJson(state, Map.of("type", "agent_response", "text", message, "language", callLanguage(state)));
            speak(state, message, callLanguage(state), false);
        }
    }

    private void finishWithMessage(BrowserSession state, String message, String outcome) {
        if (state.terminating) return;
        state.terminating = true;
        callPipelineService.appendAutomatedAgentMessage(state.call.getTwilioCallSid(), callLanguage(state), message);
        callPipelineService.completeActiveCall(state.call.getTwilioCallSid(), outcome);
        sendJson(state, Map.of("type", "agent_response", "text", message, "language", callLanguage(state)));
        state.closeOutcome = outcome;
        speak(state, message, callLanguage(state), true);
    }

    private String callLanguage(BrowserSession state) {
        return state.language == null || state.language.isBlank()
                ? state.call.getAgent().getDefaultLanguage()
                : state.language;
    }

    private boolean requestsDictatedDetails(String text) {
        if (text == null || text.isBlank()) return false;
        var normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
        return normalized.matches(".*\\b(nom|prenom|telephone|numero|adresse|email|courriel|date de naissance|name|phone|number|address|birth date|digits?)\\b.*[?].*");
    }

    private String localized(BrowserSession state, String swahili, String french, String arabic, String english) {
        return switch (callLanguage(state)) {
            case "sw" -> swahili;
            case "fr" -> french;
            case "ar" -> arabic;
            default -> english;
        };
    }

    private void persistRecording(BrowserSession state) {
        if (state.recording == null) return;
        try {
            recordingService.completeWebVoiceRecording(
                    state.call.getTenant().getId(),
                    state.call.getId(),
                    state.recording
            );
        } catch (Exception exception) {
            LOGGER.warn("Could not persist Web Voice recording for call={}", state.call.getId(), exception);
        }
    }

    private void sendJson(BrowserSession state, Map<String, ?> payload) {
        try {
            state.sendText(objectMapper.writeValueAsString(payload));
        } catch (Exception exception) {
            LOGGER.debug("Could not send Web Voice event", exception);
        }
    }

    private void closeSocket(BrowserSession state) {
        try {
            if (state.socket.isOpen()) state.socket.close(CloseStatus.NORMAL);
        } catch (Exception ignored) {
        }
    }

    private final class BrowserSession {
        private final Call call;
        private final WebSocketSession socket;
        private final ExecutorService turns;
        private CompletableFuture<RealtimeSttSession> sttSession;
        private CompletableFuture<RealtimeTtsSession> ttsSession = CompletableFuture.completedFuture(null);
        private String ttsLanguage = "";
        private volatile boolean speaking;
        private volatile boolean closeAfterSpeech;
        private volatile String closeOutcome = "";
        private volatile boolean terminating;
        private volatile String language;
        private volatile boolean awaitingAudio;
        private volatile boolean awaitingDictatedDetail;
        private long audioRequestVersion;
        private volatile long ttsSessionVersion;
        private final AtomicBoolean sttRecovering = new AtomicBoolean(false);
        private int sttRecoveryAttempts;
        private final long startedNanos = System.nanoTime();
        private long lastActivityNanos = startedNanos;
        private long callerSpeechStartedNanos;
        private int remindersSent;
        private ScheduledFuture<?> maintenanceTask;
        private final WebVoiceRecordingWriter recording;

        private BrowserSession(Call call, WebSocketSession socket) {
            this.call = call;
            this.socket = socket;
            this.language = call.getLanguageDetected() == null
                    ? call.getAgent().getDefaultLanguage()
                    : call.getLanguageDetected();
            this.recording = call.getAgent().isRecordCalls()
                    ? recordingService.startWebVoiceRecording(call.getId())
                    : null;
            this.turns = Executors.newSingleThreadExecutor(runnable -> {
                var thread = new Thread(runnable, "web-voice-" + call.getTwilioCallSid());
                thread.setDaemon(true);
                return thread;
            });
        }

        private synchronized void openTts(String language) {
            if (ttsSession != null && language.equals(ttsLanguage) && !ttsSession.isCompletedExceptionally()) return;
            interruptSpeech();
            ttsLanguage = language;
            var listenerVersion = ++ttsSessionVersion;
            ttsSession = ttsProvider.open(language, call.getAgent().getTtsVoiceId(), new TtsAudioListener() {
                @Override
                public void onPcmAudio(byte[] pcm16kAudio) {
                    if (listenerVersion != ttsSessionVersion) return;
                    try {
                        if (!speaking) {
                            awaitingAudio = false;
                            speaking = true;
                            markActivity();
                            sendJson(BrowserSession.this, Map.of("type", "speaking", "value", true));
                        }
                        if (recording != null) recording.appendAgent(pcm16kAudio);
                        sendBinary(pcm16kAudio);
                    } catch (Exception exception) {
                        LOGGER.debug("Could not send Web Voice audio", exception);
                    }
                }

                @Override
                public void onComplete() {
                    if (listenerVersion != ttsSessionVersion) return;
                    awaitingAudio = false;
                    speaking = false;
                    markActivity();
                    sendJson(BrowserSession.this, Map.of("type", "speaking", "value", false));
                    if (closeAfterSpeech) {
                        sendJson(BrowserSession.this, Map.of(
                                "type", "ended",
                                "outcome", closeOutcome == null || closeOutcome.isBlank() ? "completed" : closeOutcome
                        ));
                        closeSocket(BrowserSession.this);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    if (listenerVersion != ttsSessionVersion) return;
                    awaitingAudio = false;
                    speaking = false;
                    LOGGER.warn("Web Voice TTS failed for session={}", call.getTwilioCallSid(), error);
                    sendJson(BrowserSession.this, Map.of("type", "error", "message", "Voice playback became unavailable"));
                }
            });
        }

        private synchronized void interruptSpeech() {
            if (ttsSession != null) {
                ttsSession.thenAccept(session -> {
                    if (session != null) session.close();
                });
            }
            ttsSession = CompletableFuture.completedFuture(null);
            ttsLanguage = "";
            speaking = false;
            awaitingAudio = false;
            audioRequestVersion++;
            ttsSessionVersion++;
            closeAfterSpeech = false;
            closeOutcome = "";
            if (recording != null) recording.clearPendingAgentAudio();
        }

        private synchronized void markCallerActivity() {
            markActivity();
            remindersSent = 0;
            if (callerSpeechStartedNanos == 0) callerSpeechStartedNanos = System.nanoTime();
        }

        private synchronized void beginCallerSpeech() {
            if (callerSpeechStartedNanos == 0) callerSpeechStartedNanos = System.nanoTime();
        }

        private synchronized void finishCallerSpeech() {
            callerSpeechStartedNanos = 0;
        }

        private synchronized long callerSpeechMillis() {
            return callerSpeechStartedNanos == 0
                    ? 0
                    : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - callerSpeechStartedNanos);
        }

        private synchronized void markActivity() {
            lastActivityNanos = System.nanoTime();
        }

        private synchronized long silentSeconds() {
            return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastActivityNanos);
        }

        private long elapsedSeconds() {
            return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedNanos);
        }

        private synchronized boolean canSendReminder(int afterSeconds, int maximum) {
            return maximum > remindersSent && silentSeconds() >= afterSeconds;
        }

        private synchronized void markReminderSent() {
            remindersSent++;
            markActivity();
        }

        private synchronized boolean shouldEndAfterFinalReminder(int graceSeconds, int maximum) {
            return maximum > 0 && remindersSent >= maximum && silentSeconds() >= graceSeconds;
        }

        private void enqueue(Runnable task) {
            try {
                turns.execute(task);
            } catch (RejectedExecutionException ignored) {
            }
        }

        private void sendText(String text) throws Exception {
            synchronized (socket) {
                if (socket.isOpen()) socket.sendMessage(new TextMessage(text));
            }
        }

        private void sendBinary(byte[] bytes) throws Exception {
            synchronized (socket) {
                if (socket.isOpen()) socket.sendMessage(new BinaryMessage(ByteBuffer.wrap(bytes)));
            }
        }

        private void close(boolean closeSocket) {
            if (maintenanceTask != null) maintenanceTask.cancel(false);
            if (sttSession != null) sttSession.thenAccept(RealtimeSttSession::close);
            if (ttsSession != null) {
                ttsSession.thenAccept(session -> {
                    if (session != null) session.close();
                });
            }
            speaking = false;
            turns.shutdown();
            if (closeSocket) WebVoiceSessionService.this.closeSocket(this);
        }
    }
}
