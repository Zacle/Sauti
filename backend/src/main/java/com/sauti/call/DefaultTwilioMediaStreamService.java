package com.sauti.call;

import com.sauti.session.CallSessionStore;
import com.sauti.dashboard.DashboardEventPublisher;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultTwilioMediaStreamService implements TwilioMediaStreamService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTwilioMediaStreamService.class);

    private final Map<String, TwilioMediaSession> sessions = new ConcurrentHashMap<>();
    private final CallRepository callRepository;
    private final CallPipelineService callPipelineService;
    private final AudioCodecConverter audioCodecConverter;
    private final RealtimeSpeechToTextProvider sttProvider;
    private final RealtimeTextToSpeechProvider ttsProvider;
    private final TelephonyRealtimeConversationProvider realtimeConversationProvider;
    private final TwilioMediaFrameFactory frameFactory;
    private final TelnyxMediaFrameFactory telnyxFrameFactory;
    private final CallSessionStore callSessionStore;
    private final DashboardEventPublisher dashboardEventPublisher;
    private final CallTransferService callTransferService;
    private final VoiceRuntimeMetrics metrics;
    private final double bargeInConfidenceThreshold;
    private final long bargeInMinAudioMs;
    private final long bargeInGraceMs;
    private final ScheduledExecutorService callMaintenanceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "call-maintenance");
        thread.setDaemon(true);
        return thread;
    });

    public DefaultTwilioMediaStreamService(
            CallRepository callRepository,
            CallPipelineService callPipelineService,
            AudioCodecConverter audioCodecConverter,
            RealtimeSpeechToTextProvider sttProvider,
            RealtimeTextToSpeechProvider ttsProvider,
            TelephonyRealtimeConversationProvider realtimeConversationProvider,
            TwilioMediaFrameFactory frameFactory,
            TelnyxMediaFrameFactory telnyxFrameFactory,
            CallSessionStore callSessionStore,
            DashboardEventPublisher dashboardEventPublisher,
            CallTransferService callTransferService,
            VoiceRuntimeMetrics metrics,
            @Value("${sauti.barge-in.confidence-threshold:0.70}") double bargeInConfidenceThreshold,
            @Value("${sauti.barge-in.min-audio-ms:150}") long bargeInMinAudioMs,
            @Value("${sauti.barge-in.grace-ms:300}") long bargeInGraceMs
    ) {
        this.callRepository = callRepository;
        this.callPipelineService = callPipelineService;
        this.audioCodecConverter = audioCodecConverter;
        this.sttProvider = sttProvider;
        this.ttsProvider = ttsProvider;
        this.realtimeConversationProvider = realtimeConversationProvider;
        this.frameFactory = frameFactory;
        this.telnyxFrameFactory = telnyxFrameFactory;
        this.callSessionStore = callSessionStore;
        this.dashboardEventPublisher = dashboardEventPublisher;
        this.callTransferService = callTransferService;
        this.metrics = metrics;
        this.bargeInConfidenceThreshold = bargeInConfidenceThreshold;
        this.bargeInMinAudioMs = bargeInMinAudioMs;
        this.bargeInGraceMs = bargeInGraceMs;
    }

    @Override
    public void start(
            String callSid,
            String streamSid,
            TwilioMediaFormat mediaFormat,
            Map<String, String> parameters,
            TwilioOutboundMediaSender outboundMediaSender
    ) {
        if (callSid == null || callSid.isBlank() || streamSid == null || streamSid.isBlank()) {
            return;
        }
        var call = callRepository.findByTwilioCallSid(callSid).orElse(null);
        callSessionStore.updateStreamSid(callSid, streamSid);
        var sttSession = openInboundSession(callSid, call);
        var language = call == null ? "" : call.getAgent().getDefaultLanguage();
        var voiceId = call == null ? "" : call.getAgent().getTtsVoiceId();
        TelephonyMediaFrameFactory selectedFrameFactory = "telnyx".equals(parameters.get("_mediaProvider"))
                ? telnyxFrameFactory
                : frameFactory;
        var session = new TwilioMediaSession(
                callSid, streamSid, mediaFormat, Map.copyOf(parameters),
                outboundMediaSender, sttSession, selectedFrameFactory
        );
        session.attachTtsSessionFactory(generation -> {
            var ttsOpenedNanos = System.nanoTime();
            var firstAudio = new AtomicBoolean(false);
            return ttsProvider.open(language, voiceId, new TtsAudioListener() {
            @Override
            public void onPcmAudio(byte[] pcm16kAudio) {
                if (firstAudio.compareAndSet(false, true)) {
                    metrics.recordLatency(
                            "tts_first_audio", "telephony", metricChannel(session),
                            System.nanoTime() - ttsOpenedNanos
                    );
                    LOGGER.info(
                            "Voice latency callSid={} stage=tts_first_audio elapsedMs={}",
                            callSid,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ttsOpenedNanos)
                    );
                }
                session.enqueue(() -> {
                    if (!session.acceptsTtsGeneration(generation)) {
                        return;
                    }
                    var outboundAudio = session.usesL16()
                            ? audioCodecConverter.pcm16kToTelnyxL16(pcm16kAudio)
                            : audioCodecConverter.pcm16kToTwilioMulaw8k(pcm16kAudio);
                    if (outboundAudio.length > 0) {
                        session.sendFrame(selectedFrameFactory.media(session.streamSid, outboundAudio));
                    }
                });
            }

            @Override
            public void onComplete() {
                session.enqueue(() -> {
                    if (session.acceptsTtsGeneration(generation)) {
                        session.completeTtsResponse();
                    }
                });
            }

            @Override
            public void onError(Throwable error) {
                LOGGER.warn("Realtime TTS failed for callSid={}", callSid, error);
                metrics.failure("telephony", metricChannel(session), "tts_stream");
            }
        });
        });
        var previous = sessions.put(callSid, session);
        if (previous != null) {
            previous.close();
            metrics.sessionEnded("telephony", metricChannel(previous));
        }
        metrics.sessionStarted("telephony", metricChannel(session));
        if (call != null) {
            session.attachMaintenanceTask(callMaintenanceExecutor.scheduleAtFixedRate(
                    () -> maintainCall(call, session), 1, 1, TimeUnit.SECONDS
            ));
            CompletableFuture.delayedExecutor(call.getAgent().getMaxCallDurationSeconds(), TimeUnit.SECONDS)
                    .execute(() -> {
                        if (sessions.remove(callSid, session)) {
                            metrics.sessionEnded("telephony", metricChannel(session));
                            var callLanguageCode = callLanguage(call);
                            var farewell = localized(callLanguageCode,
                                    "Asante kwa kupiga simu. Muda wetu umefika mwisho, kwa hiyo nitakata simu sasa. Kwaheri.",
                                    "Merci de votre appel. Nous sommes arrivés à la durée maximale, je vais donc raccrocher. Au revoir.",
                                    "شكرًا لاتصالك. لقد وصلنا إلى الحد الأقصى لمدة المكالمة، لذا سأنهيها الآن. مع السلامة.",
                                    "Thank you for calling. We have reached the maximum call time, so I will end the call now. Goodbye.");
                            callPipelineService.appendAutomatedAgentMessage(callSid, callLanguageCode, farewell);
                            callPipelineService.completeActiveCall(callSid, "max_duration");
                            streamTtsResponse(session, farewell, true);
                        }
                    });
            var transferFallback = parameters.get("transferFallback");
            if (transferFallback != null && !transferFallback.isBlank()) {
                var languageCode = callLanguage(call);
                var fallbackMessage = transferFallbackMessage(languageCode, transferFallback);
                callPipelineService.appendAutomatedAgentMessage(callSid, languageCode, fallbackMessage);
                streamTtsResponse(session, fallbackMessage, false);
            } else if (call.isAfterHours() && !"answer".equals(call.getAgent().getAfterHoursBehavior())) {
                var languageCode = callLanguage(call);
                var afterHoursMessage = afterHoursMessage(call, languageCode);
                callPipelineService.appendAutomatedAgentMessage(callSid, languageCode, afterHoursMessage);
                var closeAfterMessage = "closed".equals(call.getAgent().getAfterHoursBehavior());
                if (closeAfterMessage) callPipelineService.completeActiveCall(callSid, "after_hours");
                streamTtsResponse(session, afterHoursMessage, closeAfterMessage);
            } else {
                var greeting = callPipelineService.openingGreeting(call);
                if (greeting != null && !greeting.isBlank()) {
                    // Play the deterministic greeting immediately while the
                    // Realtime and Cartesia sockets finish warming in parallel.
                    streamTtsResponse(session, greeting, false);
                    sttSession.thenAccept(inbound -> {
                        if (inbound instanceof TelephonyRealtimeConversationProvider.Session realtime) {
                            realtime.seedAssistantText(greeting);
                        }
                    });
                }
            }
        }
    }

    private CompletableFuture<RealtimeSttSession> openInboundSession(String callSid, Call call) {
        if (call != null && realtimeConversationProvider.supports(call)) {
            return realtimeConversationProvider.open(call, new TelephonyRealtimeConversationProvider.Listener() {
                @Override
                public void onCallerSpeechStarted() {
                    handleRealtimeCallerSpeechStarted(callSid);
                }

                @Override
                public void onCallerTranscript(String transcript) {
                    handleRealtimeCallerTranscript(callSid, transcript);
                }

                @Override
                public void onAgentTextDelta(String delta) {
                    handleRealtimeAgentDelta(callSid, delta);
                }

                @Override
                public void onAgentTextComplete(String text, boolean interrupted) {
                    handleRealtimeAgentComplete(callSid, text, interrupted);
                }

                @Override
                public void onError(Throwable error) {
                    LOGGER.warn("Telephony Realtime failed for callSid={}", callSid, error);
                }

                @Override
                public void onDisconnected(Throwable error) {
                    activateCascadeFallback(callSid, call, error);
                }
            }).thenApply(realtime -> (RealtimeSttSession) realtime)
                    .exceptionallyCompose(error -> {
                        LOGGER.warn("Telephony Realtime could not connect for callSid={}; using cascade fallback", callSid, error);
                        metrics.fallback("telephony", metricChannel(callSid), "realtime_connect");
                        return openCascadeStt(callSid, call);
                    });
        }
        return openCascadeStt(callSid, call);
    }

    private CompletableFuture<RealtimeSttSession> openCascadeStt(String callSid, Call call) {
        return sttProvider.open(call == null ? null : call.getAgent(), new RealtimeTranscriptListener() {
            @Override
            public void onPartialTranscript(String transcript, double confidence) {
                handlePartialTranscript(callSid, transcript, confidence);
            }

            @Override
            public void onFinalTranscript(String transcript) {
                handleFinalTranscript(callSid, transcript);
            }

            @Override
            public void onError(Throwable error) {
                LOGGER.warn("Realtime STT failed for callSid={}", callSid, error);
                metrics.failure("telephony", metricChannel(callSid), "stt_stream");
            }
        });
    }

    private void activateCascadeFallback(String callSid, Call call, Throwable error) {
        var session = sessions.get(callSid);
        if (session == null || !session.beginCascadeFallback()) return;
        LOGGER.warn("Telephony Realtime disconnected for callSid={}; activating cascade fallback", callSid, error);
        metrics.fallback("telephony", metricChannel(session), "realtime_disconnect");
        if (session.interruptCurrentTurn()) {
            callSessionStore.markInterrupted(callSid);
            callSessionStore.setSpeaking(callSid, false, "");
            dashboardEventPublisher.agentSpeaking(call, false);
        }
        session.replaceSttSession(openCascadeStt(callSid, call));
    }

    private String afterHoursMessage(Call call, String language) {
        var configured = call.getAgent().getAfterHoursMessage();
        if (configured != null && !configured.isBlank()) return configured;
        if ("closed".equals(call.getAgent().getAfterHoursBehavior())) {
            return localized(language,
                    "Asante kwa kupiga simu. Kwa sasa tumefungwa. Tafadhali tupigie tena wakati wa saa za kazi. Kwaheri.",
                    "Merci de votre appel. Nous sommes actuellement fermés. Veuillez nous rappeler pendant les heures d'ouverture. Au revoir.",
                    "شكرًا لاتصالك. نحن مغلقون حاليًا. يرجى الاتصال بنا خلال ساعات العمل. مع السلامة.",
                    "Thank you for calling. We are currently closed. Please call again during business hours. Goodbye.");
        }
        return localized(language,
                "Kwa sasa tumefungwa, lakini ninaweza kuchukua jina lako, nambari yako, na ujumbe ili timu ikupigie.",
                "Nous sommes actuellement fermés, mais je peux prendre votre nom, votre numéro et un message pour que l'équipe vous rappelle.",
                "نحن مغلقون حاليًا، ولكن يمكنني تسجيل اسمك ورقمك ورسالتك ليتواصل معك الفريق.",
                "We are currently closed, but I can take your name, number, and a message for the team to call you back.");
    }

    @Override
    public void acceptDtmf(String callSid, String digit) {
        if (digit == null || !digit.matches("[0-9*#]")) return;
        var call = callRepository.findByTwilioCallSid(callSid).orElse(null);
        if (call == null || !call.isActive() || !call.getAgent().isDtmfEnabled()) return;
        var session = sessions.get(callSid);
        if (session == null) return;
        session.markCallerResponse();
        session.enqueue(() -> {
            if (session.interruptCurrentTurn()) {
                callSessionStore.markInterrupted(callSid);
                callSessionStore.setSpeaking(callSid, false, "");
                dashboardEventPublisher.agentSpeaking(call, false);
            }
            var input = session.acceptDtmfDigit(
                    digit,
                    call.getAgent().getDtmfTerminationKey(),
                    call.getAgent().getDtmfMaxDigits()
            );
            if (input.complete()) {
                processDtmfDigits(call, session, input.digits());
                return;
            }
            callMaintenanceExecutor.schedule(
                    () -> session.enqueue(() -> {
                        var completed = session.flushDtmfIfVersion(input.version());
                        if (!completed.isBlank()) processDtmfDigits(call, session, completed);
                    }),
                    call.getAgent().getDtmfInputTimeoutSeconds(),
                    TimeUnit.SECONDS
            );
        });
    }

    private void processDtmfDigits(Call call, TwilioMediaSession session, String digits) {
        if (digits == null || digits.isBlank() || !call.isActive()) return;
        var meaning = call.getAgent().getDtmfDigitMappings().get(digits);
        var callerInput = meaning == null || meaning.isBlank()
                ? "Caller entered keypad digits: " + digits + "."
                : "Caller selected keypad option \"" + meaning + "\" (digits: " + digits + ").";
        if (session.sendRealtimeUserText(callerInput)) {
            callPipelineService.recordRealtimeTranscript(
                    call.getTenant().getId(), call.getId(), "caller", callerInput, false
            );
            return;
        }
        var turn = callPipelineService.processLiveTranscriptTurn(call, callerInput);
        if (!turn.text().isBlank()) streamTtsResponse(session, turn.text(), false);
    }

    private void maintainCall(Call call, TwilioMediaSession session) {
        if (sessions.get(call.getTwilioCallSid()) != session || !call.isActive()) return;
        if (session.isAgentBusy()) return;
        long silentSeconds = session.silentSeconds();
        var agent = call.getAgent();
        var reminderSeconds = Math.max(30, agent.getReminderAfterSilenceSeconds());
        var finalGraceSeconds = 30;
        var endSilenceSeconds = Math.max(agent.getEndCallOnSilenceSeconds(), reminderSeconds + finalGraceSeconds);
        if (session.shouldEndAfterFinalReminder(finalGraceSeconds, agent.getMaxReminders())
                || silentSeconds >= endSilenceSeconds) {
            endSilentCall(call, session);
            return;
        }
        if (session.canSendReminder(reminderSeconds, agent.getMaxReminders())) {
            session.markReminderSent();
            var language = callLanguage(call);
            var reminder = localized(language,
                    "Bado uko hapo? Chukua muda wako, kisha uniambie ukiwa tayari.",
                    "Êtes-vous toujours là ? Prenez votre temps et dites-moi quand vous êtes prêt.",
                    "هل ما زلت معي؟ خذ وقتك وأخبرني عندما تكون مستعدًا.",
                    "Are you still there? Take your time, and let me know when you are ready.");
            callPipelineService.appendAutomatedAgentMessage(call.getTwilioCallSid(), language, reminder);
            streamTtsResponse(session, reminder, false);
        }
    }

    private void endSilentCall(Call call, TwilioMediaSession session) {
        if (!sessions.remove(call.getTwilioCallSid(), session)) return;
        metrics.sessionEnded("telephony", metricChannel(session));
        var language = callLanguage(call);
        var farewell = localized(language,
                "Asante kwa kupiga simu. Kwa kuwa sikusikii, nitakata simu sasa. Kwaheri.",
                "Merci de votre appel. Comme je ne vous entends plus, je vais raccrocher maintenant. Au revoir.",
                "شكرًا لاتصالك. بما أنني لا أسمع ردًا، سأنهي المكالمة الآن. مع السلامة.",
                "Thank you for calling. Since I cannot hear a response, I will end the call now. Goodbye.");
        callPipelineService.appendAutomatedAgentMessage(call.getTwilioCallSid(), language, farewell);
        callPipelineService.completeActiveCall(call.getTwilioCallSid(), "no_response");
        streamTtsResponse(session, farewell, true);
    }

    private String callLanguage(Call call) {
        return call.getLanguageDetected() == null || call.getLanguageDetected().isBlank()
                ? call.getAgent().getDefaultLanguage()
                : call.getLanguageDetected();
    }

    private String localized(String language, String swahili, String french, String arabic, String english) {
        return switch (language == null ? "" : language) {
            case "sw" -> swahili;
            case "fr" -> french;
            case "ar" -> arabic;
            default -> english;
        };
    }

    private String transferFallbackMessage(String language, String status) {
        var reason = switch (status) {
            case "busy" -> localized(language,
                    "Mtoa huduma yuko kwenye simu nyingine.",
                    "La ligne de notre équipe est occupée.",
                    "خط موظف الفريق مشغول حاليًا.",
                    "The team member's line is busy.");
            case "no_answer" -> localized(language,
                    "Mtoa huduma hakujibu simu.",
                    "Le membre de l'équipe n'a pas répondu.",
                    "لم يرد موظف الفريق على المكالمة.",
                    "The team member did not answer.");
            default -> localized(language,
                    "Sikuweza kukuunganisha na mtoa huduma.",
                    "Je n'ai pas pu vous mettre en relation avec l'équipe.",
                    "لم أتمكن من توصيلك بأحد موظفي الفريق.",
                    "I could not connect you with a team member.");
        };
        return reason + " " + localized(language,
                "Naweza kuchukua ujumbe au kukusaidia kupanga simu ya kurudishiwa?",
                "Puis-je prendre un message ou organiser un rappel ?",
                "هل يمكنني تدوين رسالة أو ترتيب مكالمة لاحقة؟",
                "May I take a message or arrange a callback?");
    }

    @Override
    public void acceptInboundAudio(
            String callSid,
            String streamSid,
            String sequenceNumber,
            String chunk,
            String timestamp,
            byte[] mulawAudio
    ) {
        if (callSid == null || callSid.isBlank() || mulawAudio == null || mulawAudio.length == 0) {
            return;
        }
        var session = sessions.get(callSid);
        if (session == null) {
            return;
        }
        var pcmAudio = session.usesL16()
                ? audioCodecConverter.telnyxL16ToPcm16k(mulawAudio)
                : audioCodecConverter.twilioMulaw8kToPcm16k(mulawAudio);
        session.recordInboundAudio(mulawAudio.length, pcmAudio.length);
        session.recordInboundTimestamp(timestamp);
        session.sendPcmAudio(pcmAudio);
    }

    @Override
    public void markReceived(String callSid, String streamSid, String markName) {
        var session = sessions.get(callSid);
        if (session != null) {
            session.markReceived(markName);
            var pendingTransfer = session.takePendingTransfer();
            if (pendingTransfer != null) {
                initiateTransfer(session, pendingTransfer);
            }
        }
        callSessionStore.setSpeaking(callSid, false, markName);
        callRepository.findByTwilioCallSid(callSid).ifPresent(call -> dashboardEventPublisher.agentSpeaking(call, false));
    }

    @Override
    public void stop(String callSid, String streamSid) {
        if (callSid != null) {
            var session = sessions.remove(callSid);
            if (session != null) {
                session.close();
                metrics.sessionEnded("telephony", metricChannel(session));
            }
        }
    }

    private void handleRealtimeCallerSpeechStarted(String callSid) {
        var session = sessions.get(callSid);
        if (session == null) return;
        // The provider invokes this only after sustained speech or an accepted
        // final transcript. Run synchronously so model cancellation is ordered
        // before the provider requests the next response.
        session.cancelRealtimeModelResponse();
        if (session.interruptCurrentTurn()) {
            callSessionStore.markInterrupted(callSid);
            callSessionStore.setSpeaking(callSid, false, "");
            callRepository.findByTwilioCallSid(callSid)
                    .ifPresent(call -> dashboardEventPublisher.agentSpeaking(call, false));
            LOGGER.info("Realtime barge-in detected for callSid={}", callSid);
            metrics.interruption("telephony", metricChannel(session));
        }
    }

    private void handleRealtimeCallerTranscript(String callSid, String transcript) {
        if (!CallerTranscriptGuard.accepts(transcript)) return;
        var session = sessions.get(callSid);
        if (session == null) return;
        session.markCallerResponse();
        session.rememberRealtimeCallerEnding(transcript);
        callRepository.findByTwilioCallSid(callSid)
                .filter(Call::isActive)
                .ifPresent(call -> {
                    dashboardEventPublisher.transcriptFinal(call, transcript);
                    callPipelineService.recordRealtimeTranscript(
                            call.getTenant().getId(), call.getId(), "caller", transcript, false
                    );
                });
    }

    private void handleRealtimeAgentDelta(String callSid, String delta) {
        // Provider text is deliberately held until onAgentTextComplete. Sending
        // deltas to external TTS would let an interrupted or tool-only response
        // escape before its complete message and generation have been validated.
    }

    private void handleRealtimeAgentComplete(String callSid, String text, boolean interrupted) {
        if (text == null || text.isBlank()) return;
        var session = sessions.get(callSid);
        if (session == null) return;
        session.enqueue(() -> callRepository.findByTwilioCallSid(callSid)
                .filter(Call::isActive)
                .ifPresent(call -> {
                    if (interrupted) {
                        callPipelineService.recordRealtimeTranscript(
                                call.getTenant().getId(), call.getId(), "agent", text, true
                        );
                        return;
                    }
                    var terminal = session.realtimeCallerWasEnding()
                            && callPipelineService.looksLikeConversationEnding(text);
                    streamTtsResponse(session, text, terminal);
                    callPipelineService.recordRealtimeTranscript(
                            call.getTenant().getId(), call.getId(), "agent", text, false
                    );
                    if (terminal) callPipelineService.completeActiveCall(callSid, "completed");
                }));
    }

    private void handleFinalTranscript(String callSid, String transcript) {
        if (!CallerTranscriptGuard.accepts(transcript)) {
            return;
        }
        var session = sessions.get(callSid);
        if (session == null) {
            return;
        }
        session.markCallerResponse();
        session.enqueue(() -> callRepository.findByTwilioCallSid(callSid)
                .filter(Call::isActive)
                .ifPresent(call -> {
                    var turnStartedNanos = System.nanoTime();
                    dashboardEventPublisher.transcriptFinal(call, transcript);
                    var receivedText = new AtomicBoolean(false);
                    var validatedText = new StringBuilder();
                    var turn = callPipelineService.processLiveTranscriptTurn(call, transcript, delta -> {
                        if (delta == null || delta.isEmpty()) return;
                        validatedText.append(delta);
                        if (receivedText.compareAndSet(false, true)) {
                            metrics.recordLatency(
                                    "llm_first_text", "telephony", metricChannel(session),
                                    System.nanoTime() - turnStartedNanos
                            );
                            LOGGER.info(
                                    "Voice latency callSid={} stage=llm_first_text elapsedMs={}",
                                    callSid,
                                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - turnStartedNanos)
                            );
                        }
                    });
                    if (turn == null) {
                        // Compatibility fallback for alternate implementations and older test doubles.
                        turn = callPipelineService.processLiveTranscriptTurn(call, transcript);
                    }
                    var terminalTurn = turn.outcome() != null && !turn.outcome().isBlank();
                    LOGGER.info(
                            "Voice latency callSid={} stage=turn_complete elapsedMs={} validatedText={}",
                            callSid,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - turnStartedNanos),
                            receivedText.get()
                    );
                    metrics.recordLatency(
                            "turn_complete", "telephony", metricChannel(session),
                            System.nanoTime() - turnStartedNanos
                    );
                    if (callTransferService.isPending(call.getId())) {
                        session.setPendingTransfer(call.getId());
                    }
                    var responseText = validatedText.toString().trim();
                    if (responseText.isBlank()) responseText = turn.text();
                    if (!responseText.isBlank()) {
                        streamTtsResponse(session, responseText, terminalTurn);
                    } else if (session.hasPendingTransfer()) {
                        var pendingTransfer = session.takePendingTransfer();
                        if (pendingTransfer != null) initiateTransfer(session, pendingTransfer);
                    } else if (terminalTurn) {
                        session.closeOutbound();
                    }
                }));
    }

    private void initiateTransfer(TwilioMediaSession session, UUID callId) {
        callTransferService.initiateAsync(callId).thenAccept(started -> {
            if (started) return;
            session.enqueue(() -> callRepository.findById(callId)
                    .filter(Call::isActive)
                    .ifPresent(call -> {
                        var language = callLanguage(call);
                        var message = transferFallbackMessage(language, "failed");
                        call.appendAgentMessage(language, message);
                        callRepository.save(call);
                        streamTtsResponse(session, message, false);
                    }));
        });
    }

    private void streamTtsResponse(TwilioMediaSession session, String responseText, boolean closeAfterPlayback) {
        session.queueTtsResponse(() -> {
            beginTtsResponse(session, closeAfterPlayback);
            session.speak(responseText, true);
        });
    }

    private void beginTtsResponse(TwilioMediaSession session, boolean closeAfterPlayback) {
        var markName = session.nextMarkName();
        session.startAgentTurn(markName);
        session.setCloseAfterCurrentMark(closeAfterPlayback);
        session.ensureTtsSession();
        callSessionStore.setSpeaking(session.callSid, true, markName);
        callRepository.findByTwilioCallSid(session.callSid).ifPresent(call -> dashboardEventPublisher.agentSpeaking(call, true));
    }

    private void handlePartialTranscript(String callSid, String transcript, double confidence) {
        if (transcript == null || transcript.isBlank()) {
            return;
        }
        var session = sessions.get(callSid);
        if (session == null) {
            return;
        }
        session.enqueue(() -> {
            callRepository.findByTwilioCallSid(callSid)
                    .ifPresent(call -> dashboardEventPublisher.transcriptPartial(call, transcript, confidence));
            var agent = callRepository.findByTwilioCallSid(callSid).map(Call::getAgent).orElse(null);
            var sensitivity = agent == null ? bargeInConfidenceThreshold : agent.getBargeInSensitivity();
            var confidenceThreshold = Math.max(0.45, Math.min(0.90, 0.83 - (0.4 * sensitivity)));
            var graceMs = agent == null ? bargeInGraceMs : agent.getBargeInGraceMs();
            if (!session.shouldBargeIn(transcript, confidence, confidenceThreshold, bargeInMinAudioMs, graceMs)) {
                return;
            }
            if (session.interruptCurrentTurn()) {
                callSessionStore.markInterrupted(callSid);
                callSessionStore.setSpeaking(callSid, false, "");
                callRepository.findByTwilioCallSid(callSid).ifPresent(call -> dashboardEventPublisher.agentSpeaking(call, false));
                LOGGER.info("Barge-in detected for callSid={} confidence={} transcript={}", callSid, confidence, transcript);
                metrics.interruption("telephony", metricChannel(session));
            }
        });
    }

    private String metricChannel(String callSid) {
        return metricChannel(sessions.get(callSid));
    }

    private String metricChannel(TwilioMediaSession session) {
        return session != null && session.usesL16() ? "telnyx" : "twilio_compatible";
    }

    private static final class TwilioMediaSession {
        private final String callSid;
        private final String streamSid;
        private final TwilioMediaFormat mediaFormat;
        private final Map<String, String> parameters;
        private final TwilioOutboundMediaSender outboundMediaSender;
        private final TelephonyMediaFrameFactory frameFactory;
        private final ExecutorService turnExecutor;
        private final CompletableFuture<RealtimeSttSession> sttSessionFuture;
        private TtsSessionFactory ttsSessionFactory;
        private CompletableFuture<RealtimeTtsSession> ttsSessionFuture = CompletableFuture.completedFuture(null);
        private final List<byte[]> pendingPcmAudio = new ArrayList<>();
        private RealtimeSttSession sttSession;
        private boolean ttsSessionOpen;
        private long inboundMulawBytes;
        private long inboundPcmBytes;
        private String lastMarkName;
        private String currentMarkName;
        private boolean closeAfterCurrentMark;
        private boolean speaking;
        private int ttsGeneration;
        private long agentTurnStartedMediaTimestampMs = -1L;
        private long lastInboundMediaTimestampMs = -1L;
        private int turnIndex;
        private long lastActivityNanos = System.nanoTime();
        private int remindersSent;
        private ScheduledFuture<?> maintenanceTask;
        private UUID pendingTransferCallId;
        private final StringBuilder dtmfBuffer = new StringBuilder();
        private final ArrayDeque<QueuedTtsResponse> pendingTtsResponses = new ArrayDeque<>();
        private long dtmfVersion;
        private boolean ttsResponseActive;
        private int speechQueueGeneration;
        private boolean realtimeCallerEnding;
        private boolean cascadeFallbackActive;

        private TwilioMediaSession(
                String callSid,
                String streamSid,
                TwilioMediaFormat mediaFormat,
                Map<String, String> parameters,
                TwilioOutboundMediaSender outboundMediaSender,
                CompletableFuture<RealtimeSttSession> sttSessionFuture,
                TelephonyMediaFrameFactory frameFactory
        ) {
            this.callSid = callSid;
            this.streamSid = streamSid;
            this.mediaFormat = mediaFormat;
            this.parameters = parameters;
            this.outboundMediaSender = outboundMediaSender;
            this.frameFactory = frameFactory;
            this.sttSessionFuture = sttSessionFuture;
            this.turnExecutor = Executors.newSingleThreadExecutor(r -> {
                var thread = new Thread(r, "twilio-call-" + streamSid);
                thread.setDaemon(true);
                return thread;
            });
            this.sttSessionFuture.whenComplete((session, error) -> {
                if (error != null) {
                    return;
                }
                List<byte[]> queued;
                synchronized (this) {
                    sttSession = session;
                    queued = List.copyOf(pendingPcmAudio);
                    pendingPcmAudio.clear();
                }
                for (var pcmAudio : queued) {
                    enqueue(() -> session.sendPcmAudio(pcmAudio));
                }
            });
        }

        private boolean usesL16() {
            return "L16".equalsIgnoreCase(mediaFormat.encoding())
                    && mediaFormat.sampleRate() == 16000;
        }

        private void attachTtsSessionFactory(TtsSessionFactory ttsSessionFactory) {
            this.ttsSessionFactory = ttsSessionFactory;
        }

        private void ensureTtsSession() {
            if (ttsSessionFactory == null) {
                return;
            }
            int generation;
            synchronized (this) {
                if (ttsSessionOpen) {
                    return;
                }
                generation = ++ttsGeneration;
                ttsSessionOpen = true;
            }
            ttsSessionFuture = ttsSessionFactory.open(generation);
        }

        private void recordInboundAudio(long mulawBytes, long pcmBytes) {
            inboundMulawBytes += mulawBytes;
            inboundPcmBytes += pcmBytes;
        }

        private synchronized void markActivity() {
            lastActivityNanos = System.nanoTime();
        }

        private synchronized void markCallerResponse() {
            remindersSent = 0;
            markActivity();
        }

        private synchronized long silentSeconds() {
            return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastActivityNanos);
        }

        private synchronized boolean isAgentBusy() {
            return speaking || ttsResponseActive;
        }

        private synchronized boolean canSendReminder(int afterSeconds, int maximum) {
            return maximum > remindersSent && silentSeconds() >= afterSeconds;
        }

        private synchronized void markReminderSent() {
            remindersSent++;
            lastActivityNanos = System.nanoTime();
        }

        private synchronized boolean shouldEndAfterFinalReminder(int graceSeconds, int maximum) {
            return maximum > 0
                    && remindersSent >= maximum
                    && silentSeconds() >= graceSeconds;
        }

        private synchronized void attachMaintenanceTask(ScheduledFuture<?> task) {
            maintenanceTask = task;
        }

        private void recordInboundTimestamp(String timestamp) {
            if (timestamp == null || timestamp.isBlank()) {
                return;
            }
            try {
                synchronized (this) {
                    lastInboundMediaTimestampMs = Long.parseLong(timestamp);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        private void sendPcmAudio(byte[] pcmAudio) {
            RealtimeSttSession resolvedSttSession;
            synchronized (this) {
                resolvedSttSession = sttSession;
                if (resolvedSttSession == null) {
                    pendingPcmAudio.add(pcmAudio);
                    return;
                }
            }
            enqueue(() -> resolvedSttSession.sendPcmAudio(pcmAudio));
        }

        private synchronized boolean beginCascadeFallback() {
            if (cascadeFallbackActive) return false;
            cascadeFallbackActive = true;
            return true;
        }

        private void replaceSttSession(CompletableFuture<RealtimeSttSession> replacement) {
            RealtimeSttSession previous;
            synchronized (this) {
                previous = sttSession;
                sttSession = null;
            }
            if (previous != null) previous.close();
            replacement.whenComplete((next, error) -> {
                if (error != null || next == null) {
                    LOGGER.warn("Cascade STT fallback failed for callSid={}", callSid, error);
                    return;
                }
                List<byte[]> queued;
                synchronized (this) {
                    sttSession = next;
                    queued = List.copyOf(pendingPcmAudio);
                    pendingPcmAudio.clear();
                }
                for (var audio : queued) enqueue(() -> next.sendPcmAudio(audio));
            });
        }

        private void speak(String text, boolean flush) {
            ttsSessionFuture.thenAccept(ttsSession -> {
                if (ttsSession != null) {
                    enqueue(() -> ttsSession.speak(text, flush));
                }
            });
        }

        private void queueTtsResponse(Runnable response) {
            QueuedTtsResponse next = null;
            synchronized (this) {
                pendingTtsResponses.addLast(new QueuedTtsResponse(speechQueueGeneration, response));
                if (!ttsResponseActive) {
                    ttsResponseActive = true;
                    next = pendingTtsResponses.removeFirst();
                }
            }
            enqueueTtsResponse(next);
        }

        private void completeTtsResponse() {
            sendCurrentMark();
            QueuedTtsResponse next = null;
            synchronized (this) {
                ttsResponseActive = false;
                if (!pendingTtsResponses.isEmpty()) {
                    ttsResponseActive = true;
                    next = pendingTtsResponses.removeFirst();
                }
            }
            enqueueTtsResponse(next);
        }

        private void enqueueTtsResponse(QueuedTtsResponse response) {
            if (response == null) return;
            enqueue(() -> {
                synchronized (this) {
                    if (response.generation() != speechQueueGeneration) return;
                }
                response.start().run();
            });
        }

        private synchronized boolean sendRealtimeUserText(String text) {
            if (!(sttSession instanceof TelephonyRealtimeConversationProvider.Session realtime)) return false;
            interruptCurrentTurn();
            realtime.sendUserText(text);
            return true;
        }

        private synchronized void cancelRealtimeModelResponse() {
            if (sttSession instanceof TelephonyRealtimeConversationProvider.Session realtime) {
                realtime.cancelResponse();
            }
        }

        private synchronized void rememberRealtimeCallerEnding(String transcript) {
            realtimeCallerEnding = transcript != null && !transcript.isBlank()
                    && looksLikeEndingText(transcript);
        }

        private synchronized boolean realtimeCallerWasEnding() {
            return realtimeCallerEnding;
        }

        private boolean looksLikeEndingText(String transcript) {
            var normalized = java.text.Normalizer.normalize(transcript, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "")
                    .toLowerCase(java.util.Locale.ROOT);
            return normalized.matches(".*\\b(goodbye|bye|no thanks|no thank you|nothing else|that is all|all good)\\b.*")
                    || normalized.contains("au revoir")
                    || normalized.contains("bonne journee")
                    || normalized.contains("non merci")
                    || normalized.contains("kwaheri")
                    || normalized.contains("orevoir");
        }

        private synchronized boolean acceptsTtsGeneration(int generation) {
            return generation == ttsGeneration;
        }

        private void sendFrame(String frame) {
            outboundMediaSender.send(frame);
        }

        private synchronized void markReceived(String markName) {
            lastMarkName = markName;
            speaking = false;
            markActivity();
        }

        private synchronized void setPendingTransfer(UUID callId) {
            pendingTransferCallId = callId;
        }

        private synchronized boolean hasPendingTransfer() {
            return pendingTransferCallId != null;
        }

        private synchronized UUID takePendingTransfer() {
            var callId = pendingTransferCallId;
            pendingTransferCallId = null;
            return callId;
        }

        private synchronized DtmfInput acceptDtmfDigit(String digit, String terminationKey, int maxDigits) {
            if (digit.equals(terminationKey)) {
                var completed = drainDtmf();
                return new DtmfInput(completed, dtmfVersion, true);
            }
            if (dtmfBuffer.length() < maxDigits) dtmfBuffer.append(digit);
            dtmfVersion++;
            if (dtmfBuffer.length() >= maxDigits) {
                return new DtmfInput(drainDtmf(), dtmfVersion, true);
            }
            return new DtmfInput("", dtmfVersion, false);
        }

        private synchronized String flushDtmfIfVersion(long expectedVersion) {
            return expectedVersion == dtmfVersion ? drainDtmf() : "";
        }

        private String drainDtmf() {
            var digits = dtmfBuffer.toString();
            dtmfBuffer.setLength(0);
            dtmfVersion++;
            return digits;
        }

        private String nextMarkName() {
            turnIndex++;
            return "turn-" + turnIndex + "-end";
        }

        private synchronized void startAgentTurn(String markName) {
            this.currentMarkName = markName;
            this.speaking = true;
            this.agentTurnStartedMediaTimestampMs = lastInboundMediaTimestampMs;
            markActivity();
        }

        private void setCloseAfterCurrentMark(boolean closeAfterCurrentMark) {
            this.closeAfterCurrentMark = closeAfterCurrentMark;
        }

        private void sendCurrentMark() {
            if (currentMarkName != null && !currentMarkName.isBlank()) {
                sendFrame(frameFactory.mark(streamSid, currentMarkName));
                currentMarkName = "";
            }
            if (closeAfterCurrentMark) {
                closeAfterCurrentMark = false;
                closeOutbound();
            }
        }

        private void closeOutbound() {
            outboundMediaSender.close();
        }

        private synchronized boolean shouldBargeIn(
                String transcript,
                double confidence,
                double confidenceThreshold,
                long minAudioMs,
                long graceMs
        ) {
            if (!speaking || transcript == null || transcript.isBlank()) {
                return false;
            }
            if (confidence < confidenceThreshold) {
                return false;
            }
            if (agentTurnStartedMediaTimestampMs >= 0 && lastInboundMediaTimestampMs >= 0) {
                var elapsedStreamMs = lastInboundMediaTimestampMs - agentTurnStartedMediaTimestampMs;
                return elapsedStreamMs >= graceMs && elapsedStreamMs >= minAudioMs;
            }
            if (graceMs > 0 || minAudioMs > 0) {
                return false;
            }
            return true;
        }

        private boolean interruptCurrentTurn() {
            CompletableFuture<RealtimeTtsSession> sessionToClose;
            synchronized (this) {
                if (!speaking && !ttsResponseActive && pendingTtsResponses.isEmpty()) {
                    return false;
                }
                speaking = false;
                currentMarkName = "";
                closeAfterCurrentMark = false;
                ttsGeneration++;
                ttsSessionOpen = false;
                ttsResponseActive = false;
                speechQueueGeneration++;
                pendingTtsResponses.clear();
                sessionToClose = ttsSessionFuture;
            }
            sessionToClose.thenAccept(ttsSession -> {
                if (ttsSession != null) {
                    ttsSession.close();
                }
            });
            sendFrame(frameFactory.clear(streamSid));
            return true;
        }

        private void enqueue(Runnable task) {
            try {
                turnExecutor.execute(task);
            } catch (RejectedExecutionException ignored) {
            }
        }

        private void close() {
            if (maintenanceTask != null) maintenanceTask.cancel(false);
            RealtimeSttSession currentStt;
            synchronized (this) {
                currentStt = sttSession;
                sttSession = null;
                pendingTtsResponses.clear();
                ttsResponseActive = false;
                speechQueueGeneration++;
            }
            if (currentStt != null) currentStt.close();
            sttSessionFuture.thenAccept(initialStt -> {
                if (initialStt != currentStt) initialStt.close();
            });
            ttsSessionFuture.thenAccept(ttsSession -> {
                if (ttsSession != null) {
                    ttsSession.close();
                }
            });
            turnExecutor.shutdown();
        }

        private record QueuedTtsResponse(int generation, Runnable start) { }
    }

    private interface TtsSessionFactory {
        CompletableFuture<RealtimeTtsSession> open(int generation);
    }

    private record DtmfInput(String digits, long version, boolean complete) {
    }
}
