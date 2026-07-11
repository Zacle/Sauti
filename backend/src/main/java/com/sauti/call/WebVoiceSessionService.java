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
                .filter(candidate -> "web".equals(candidate.getDirection()))
                .filter(candidate -> publicAgentId.equals(candidate.getAgent().getWebVoicePublicId()))
                .orElseThrow(() -> new IllegalArgumentException("Web Voice session is unavailable"));
        var state = new BrowserSession(call, socket);
        var previous = sessions.put(callSid, state);
        if (previous != null) previous.close(false);
        sendJson(state, Map.of("type", "connected", "sessionId", callSid, "sampleRate", 16000));
        state.sttSession = openRealtimeStt(call.getAgent(), new RealtimeTranscriptListener() {
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
                LOGGER.warn("Web Voice STT failed for session={}", callSid, error);
                sendJson(state, Map.of("type", "error", "message", "Speech recognition became unavailable"));
            }
        });
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

    private CompletableFuture<RealtimeSttSession> openRealtimeStt(
            com.sauti.agent.Agent agent,
            RealtimeTranscriptListener listener
    ) {
        if (openAiRealtimeTranscriptionService.isConfigured()
                && agent != null
                && (agent.getSupportedLanguages().size() > 1 || !"en".equals(agent.getDefaultLanguage()))) {
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

    public void stop(String callSid) {
        var state = sessions.remove(callSid);
        if (state == null) return;
        state.close(true);
        persistRecording(state);
        callPipelineService.completeActiveCall(callSid, "completed");
    }

    private void onPartial(BrowserSession state, String transcript, double confidence) {
        if (transcript == null || transcript.isBlank()) return;
        state.markCallerActivity();
        sendJson(state, Map.of("type", "transcript_partial", "text", transcript, "confidence", confidence));
        var sensitivity = state.call.getAgent().getBargeInSensitivity();
        var threshold = Math.max(0.35, Math.min(0.98, 0.98 - (0.4 * sensitivity)));
        if (state.speaking
                && confidence >= threshold
                && state.callerSpeechMillis() >= state.call.getAgent().getBargeInGraceMs()) {
            state.interruptSpeech();
            sendJson(state, Map.of("type", "clear_audio"));
            sendJson(state, Map.of("type", "speaking", "value", false));
        }
    }

    private void onFinal(BrowserSession state, String transcript) {
        if (transcript == null || transcript.isBlank() || !state.call.isActive()) return;
        state.markCallerActivity();
        state.finishCallerSpeech();
        sendJson(state, Map.of("type", "transcript_final", "text", transcript));
        try {
            var streamed = new AtomicBoolean(false);
            var turn = callPipelineService.processLiveTranscriptTurn(
                    state.call.getTwilioCallSid(),
                    transcript,
                    delta -> {
                        if (delta == null || delta.isEmpty()) return;
                        if (streamed.compareAndSet(false, true)) beginSpeech(state, state.language, false);
                        state.ttsSession.thenAccept(tts -> tts.speak(delta, false));
                    }
            );
            state.language = turn.language();
            if (!turn.text().isBlank()) {
                sendJson(state, Map.of("type", "agent_response", "text", turn.text(), "language", turn.language()));
                state.closeOutcome = turn.outcome();
                if (streamed.get()) {
                    state.closeAfterSpeech = !turn.outcome().isBlank();
                    state.ttsSession.thenAccept(tts -> tts.speak("", true));
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

    private void speak(BrowserSession state, String text, String language, boolean closeAfterSpeech) {
        beginSpeech(state, language, closeAfterSpeech);
        state.ttsSession.thenAccept(tts -> {
            for (var chunk : sentenceChunker.chunks(text)) tts.speak(chunk, false);
            tts.speak("", true);
        });
    }

    private void beginSpeech(BrowserSession state, String language, boolean closeAfterSpeech) {
        state.openTts(language);
        state.closeAfterSpeech = closeAfterSpeech;
        if (!closeAfterSpeech) state.closeOutcome = "";
        else if (state.closeOutcome == null || state.closeOutcome.isBlank()) state.closeOutcome = "completed";
        state.speaking = true;
        state.markActivity();
        sendJson(state, Map.of("type", "speaking", "value", true));
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
        if (state.shouldEndAfterFinalReminder(agent.getReminderAfterSilenceSeconds(), agent.getMaxReminders())
                || silence >= agent.getEndCallOnSilenceSeconds()) {
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
        if (state.canSendReminder(agent.getReminderAfterSilenceSeconds(), agent.getMaxReminders())) {
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
            ttsSession = ttsProvider.open(language, call.getAgent().getTtsVoiceId(), new TtsAudioListener() {
                @Override
                public void onPcmAudio(byte[] pcm16kAudio) {
                    try {
                        if (recording != null) recording.appendAgent(pcm16kAudio);
                        sendBinary(pcm16kAudio);
                    } catch (Exception exception) {
                        LOGGER.debug("Could not send Web Voice audio", exception);
                    }
                }

                @Override
                public void onComplete() {
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
            closeAfterSpeech = false;
            closeOutcome = "";
            if (recording != null) recording.clearPendingAgentAudio();
        }

        private synchronized void markCallerActivity() {
            markActivity();
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
