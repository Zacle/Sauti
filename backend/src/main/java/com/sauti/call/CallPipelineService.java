package com.sauti.call;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.agent.AgentVariableRepository;
import com.sauti.call.CallDtos.SimulatedTurnResponse;
import com.sauti.dashboard.DashboardEventPublisher;
import com.sauti.llm.ConversationOrchestrator;
import com.sauti.session.CallSession;
import com.sauti.nlp.LanguageDetector;
import com.sauti.session.CallSessionStore;
import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CallPipelineService {
    private final CallRepository callRepository;
    private final CallTurnRepository callTurnRepository;
    private final AgentRepository agentRepository;
    private final AgentVariableRepository agentVariableRepository;
    private final StreamingSttProvider sttProvider;
    private final LanguageDetector languageDetector;
    private final ConversationOrchestrator conversationOrchestrator;
    private final StreamingTtsProvider ttsProvider;
    private final CallSessionStore callSessionStore;
    private final DashboardEventPublisher dashboardEventPublisher;
    private final PostCallAnalysisService postCallAnalysisService;

    public CallPipelineService(
            CallRepository callRepository,
            CallTurnRepository callTurnRepository,
            AgentRepository agentRepository,
            AgentVariableRepository agentVariableRepository,
            StreamingSttProvider sttProvider,
            LanguageDetector languageDetector,
            ConversationOrchestrator conversationOrchestrator,
            StreamingTtsProvider ttsProvider,
            CallSessionStore callSessionStore,
            DashboardEventPublisher dashboardEventPublisher,
            PostCallAnalysisService postCallAnalysisService
    ) {
        this.callRepository = callRepository;
        this.callTurnRepository = callTurnRepository;
        this.agentRepository = agentRepository;
        this.agentVariableRepository = agentVariableRepository;
        this.sttProvider = sttProvider;
        this.languageDetector = languageDetector;
        this.conversationOrchestrator = conversationOrchestrator;
        this.ttsProvider = ttsProvider;
        this.callSessionStore = callSessionStore;
        this.dashboardEventPublisher = dashboardEventPublisher;
        this.postCallAnalysisService = postCallAnalysisService;
    }

    @Transactional
    public Call startTestCall(java.util.UUID tenantId, java.util.UUID agentId) {
        var agent = agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        var callSid = "TEST-" + java.util.UUID.randomUUID();
        var call = callRepository.save(new Call(agent.getTenant(), agent, callSid, "Browser test", "test"));
        callSessionStore.createIfAbsent(callSid, CallSession.fromCall(call, ""));
        var opening = conversationOrchestrator.generateOpeningGreeting(call, agent.getDefaultLanguage(), "browser test call");
        if (!opening.isBlank()) {
            call.appendAgentMessage(agent.getDefaultLanguage(), opening);
            callTurnRepository.save(new CallTurn(
                    call, 1, "", opening, agent.getDefaultLanguage(), 0, 0, 0, false
            ));
        }
        dashboardEventPublisher.callStarted(call);
        return callRepository.save(call);
    }

    @Transactional
    public Call startWebCall(String publicAgentId) {
        return startWebCall(publicAgentId, null);
    }

    @Transactional
    public Call startWebCall(String publicAgentId, String preferredLanguage) {
        var agent = agentRepository.findByWebVoicePublicId(publicAgentId)
                .filter(Agent::isActive)
                .filter(Agent::isWebVoiceEnabled)
                .orElseThrow(() -> new EntityNotFoundException("Web Voice agent not found"));
        var language = preferredLanguage == null || preferredLanguage.isBlank()
                ? agent.getDefaultLanguage()
                : preferredLanguage.trim().toLowerCase(java.util.Locale.ROOT);
        if (!agent.getSupportedLanguages().contains(language)) {
            throw new IllegalArgumentException("Requested Web Voice language is not supported");
        }
        var callSid = "WEB-" + java.util.UUID.randomUUID();
        var call = new Call(agent.getTenant(), agent, callSid, "Web visitor", "web");
        call.selectLanguage(language);
        if (!agent.isAvailableAt(OffsetDateTime.now())) call.markAfterHours();
        call = callRepository.save(call);
        callSessionStore.createIfAbsent(callSid, CallSession.fromCall(call, ""));
        var opening = conversationOrchestrator.generateOpeningGreeting(call, language, "public web voice call");
        if (!opening.isBlank()) {
            call.appendAgentMessage(language, opening);
            callTurnRepository.save(new CallTurn(
                    call, 1, "", opening, language, 0, 0, 0, false
            ));
        }
        dashboardEventPublisher.callStarted(call);
        return callRepository.save(call);
    }

    @Transactional
    public Call startInboundCall(String twilioNumber, String twilioCallSid, String callerNumber) {
        Agent agent = agentRepository.findByTwilioPhoneNumber(twilioNumber)
                .filter(Agent::isActive)
                .orElseThrow(() -> new EntityNotFoundException("No active agent for this phone number"));
        var call = callRepository.findByTwilioCallSid(twilioCallSid)
                .orElseGet(() -> {
                    var created = new Call(agent.getTenant(), agent, twilioCallSid, callerNumber, "inbound");
                    if (!agent.isAvailableAt(OffsetDateTime.now())) created.markAfterHours();
                    return callRepository.save(created);
                });
        callSessionStore.createIfAbsent(twilioCallSid, CallSession.fromCall(call, ""));
        dashboardEventPublisher.callStarted(call);
        return call;
    }

    @Transactional
    public Call startWhatsAppConversation(String phoneNumberId, String customerNumber) {
        var agent = agentRepository.findByWhatsappPhoneNumberId(phoneNumberId)
                .filter(Agent::isActive)
                .filter(Agent::isWhatsappEnabled)
                .orElseThrow(() -> new EntityNotFoundException("No active WhatsApp agent for this phone number"));
        var existing = callRepository
                .findFirstByAgent_IdAndDirectionAndCallerNumberAndOutcomeOrderByStartedAtDesc(
                        agent.getId(), "whatsapp", customerNumber, "active"
                );
        if (existing.isPresent()) {
            var call = existing.get();
            if (call.getStartedAt().isAfter(OffsetDateTime.now().minusHours(24))) {
                callSessionStore.createIfAbsent(call.getTwilioCallSid(), CallSession.fromCall(call, ""));
                return call;
            }
            call.complete("conversation_expired");
            analyzePostCall(call);
            archiveSession(call);
            callRepository.save(call);
            dashboardEventPublisher.callEnded(call);
        }
        var callSid = "WA-" + java.util.UUID.randomUUID();
        var call = new Call(agent.getTenant(), agent, callSid, customerNumber, "whatsapp");
        call.selectLanguage(agent.getDefaultLanguage());
        if (!agent.isAvailableAt(OffsetDateTime.now())) call.markAfterHours();
        call = callRepository.save(call);
        callSessionStore.createIfAbsent(callSid, CallSession.fromCall(call, ""));
        dashboardEventPublisher.callStarted(call);
        return call;
    }

    @Transactional
    public SimulatedTurnResponse processTextTurn(java.util.UUID tenantId, String twilioCallSid, String transcript) {
        return processTextTurn(tenantId, twilioCallSid, transcript, 0);
    }

    @Transactional
    public SimulatedTurnResponse processTextTurn(
            java.util.UUID tenantId,
            String twilioCallSid,
            String transcript,
            int sttLatencyMs
    ) {
        Call call = callRepository.findByTwilioCallSidAndTenantId(twilioCallSid, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        var response = processTranscriptTurn(call, transcript, Math.max(0, sttLatencyMs), false);
        return new SimulatedTurnResponse(response.language(), response.text(), call.getTranscript(), response.outcome());
    }

    @Transactional
    public Call completeTestCall(java.util.UUID tenantId, java.util.UUID callId, String requestedOutcome) {
        var call = callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (!"test".equals(call.getDirection())) {
            throw new IllegalArgumentException("Only browser test calls can be completed here");
        }
        if (call.isActive()) {
            var outcome = java.util.Set.of("completed", "no-response", "max-duration").contains(requestedOutcome)
                    ? requestedOutcome
                    : "completed";
            call.complete(outcome);
        }
        analyzePostCall(call);
        archiveSession(call);
        dashboardEventPublisher.callEnded(call);
        return callRepository.save(call);
    }

    @Transactional
    public void markTestCallInterrupted(java.util.UUID tenantId, java.util.UUID callId) {
        var call = callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (!"test".equals(call.getDirection()) || !call.isActive()) {
            throw new IllegalArgumentException("The browser test call is not active");
        }
        callSessionStore.markInterrupted(call.getTwilioCallSid());
    }

    @Transactional
    public ReminderResult addTestReminder(java.util.UUID tenantId, java.util.UUID callId) {
        var call = callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (!"test".equals(call.getDirection()) || !call.isActive()) {
            throw new IllegalArgumentException("The browser test call is not active");
        }
        var language = call.getLanguageDetected() == null
                ? call.getAgent().getDefaultLanguage()
                : call.getLanguageDetected();
        var text = switch (language) {
            case "sw" -> "Bado uko hapo?";
            case "fr" -> "Êtes-vous toujours là ?";
            case "ar" -> "هل ما زلت معي؟";
            default -> "Are you still there?";
        };
        call.appendAgentMessage(language, text);
        var turnIndex = callTurnRepository.countByCall_Id(call.getId()) + 1;
        callTurnRepository.save(new CallTurn(call, turnIndex, "", text, language, 0, 0, 0, false));
        callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), text, java.util.List.of());
        callRepository.save(call);
        return new ReminderResult(language, text);
    }

    @Transactional
    public ReminderResult addTestFarewell(java.util.UUID tenantId, java.util.UUID callId) {
        var call = callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (!"test".equals(call.getDirection()) || !call.isActive()) {
            throw new IllegalArgumentException("The browser test call is not active");
        }
        var language = call.getLanguageDetected() == null
                ? call.getAgent().getDefaultLanguage()
                : call.getLanguageDetected();
        var text = switch (language) {
            case "sw" -> "Asante kwa kupiga simu. Kwa kuwa sikusikii, nitakata simu sasa. Kwaheri.";
            case "fr" -> "Merci de votre appel. Comme je ne vous entends plus, je vais raccrocher maintenant. Au revoir.";
            case "ar" -> "شكرًا لاتصالك. بما أنني لا أسمع ردًا، سأنهي المكالمة الآن. مع السلامة.";
            default -> "Thank you for calling. Since I cannot hear a response, I will end the call now. Goodbye.";
        };
        call.appendAgentMessage(language, text);
        var turnIndex = callTurnRepository.countByCall_Id(call.getId()) + 1;
        callTurnRepository.save(new CallTurn(call, turnIndex, "", text, language, 0, 0, 0, false));
        callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), text, java.util.List.of());
        callRepository.save(call);
        return new ReminderResult(language, text);
    }

    @Transactional
    public void appendAutomatedAgentMessage(String twilioCallSid, String language, String text) {
        var call = callRepository.findByTwilioCallSid(twilioCallSid)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (text == null || text.isBlank()) return;
        call.appendAgentMessage(language, text);
        var turnIndex = callTurnRepository.countByCall_Id(call.getId()) + 1;
        callTurnRepository.save(new CallTurn(call, turnIndex, "", text, language, 0, 0, 0, false));
        callSessionStore.appendAssistantMessage(call.getTwilioCallSid(), text, java.util.List.of());
        callRepository.save(call);
    }

    @Transactional
    public void completeActiveCall(String twilioCallSid, String outcome) {
        callRepository.findByTwilioCallSid(twilioCallSid)
                .ifPresent(call -> {
                    if (call.isActive()) {
                        call.complete(outcome);
                    }
                    analyzePostCall(call);
                    archiveSession(call);
                    callRepository.save(call);
                    dashboardEventPublisher.callEnded(call);
                });
    }

    @Transactional
    public void updateTwilioStatus(String twilioCallSid, String callStatus, Integer durationSeconds, String recordingUrl, String recordingSid) {
        callRepository.findByTwilioCallSid(twilioCallSid)
                .ifPresent(call -> {
                    call.applyTwilioStatus(callStatus, durationSeconds, recordingUrl, recordingSid);
                    if (isTerminalTwilioStatus(callStatus)) {
                        archiveSession(call);
                        analyzePostCall(call);
                    }
                    callRepository.save(call);
                    if (isTerminalTwilioStatus(callStatus)) {
                        dashboardEventPublisher.callEnded(call);
                    }
                });
    }

    @Transactional
    public TurnResult processTurn(Call call, byte[] audioPayload) {
        if (!call.isActive()) {
            return new TurnResult(
                    call.getLanguageDetected() == null ? call.getAgent().getDefaultLanguage() : call.getLanguageDetected(),
                    "",
                    new byte[0],
                    ""
            );
        }
        long sttStart = System.nanoTime();
        String callerTranscript = sttProvider.transcribe(audioPayload);
        int sttMs = elapsedMs(sttStart);

        return processTranscriptTurn(call, callerTranscript, sttMs, true);
    }

    @Transactional
    public TurnResult processLiveTranscriptTurn(Call call, String callerTranscript) {
        return processTranscriptTurn(call, callerTranscript, 0, false);
    }

    @Transactional
    public TurnResult processLiveTranscriptTurn(String callSid, String callerTranscript) {
        var call = callRepository.findByTwilioCallSid(callSid)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        return processTranscriptTurn(call, callerTranscript, 0, false);
    }

    private TurnResult processTranscriptTurn(Call call, String callerTranscript, int sttMs, boolean synthesizeAudio) {
        if (!call.isActive()) {
            return new TurnResult(
                    call.getLanguageDetected() == null ? call.getAgent().getDefaultLanguage() : call.getLanguageDetected(),
                    "",
                    new byte[0],
                    ""
            );
        }

        if (call.getAgent().isDetectVoicemail()
                && hasNoCallerTurns(call)
                && looksLikeVoicemail(callerTranscript)) {
            call.appendTurn(call.getAgent().getDefaultLanguage(), callerTranscript, "");
            var turnIndex = callTurnRepository.countByCall_Id(call.getId()) + 1;
            callTurnRepository.save(new CallTurn(call, turnIndex, callerTranscript, "",
                    call.getAgent().getDefaultLanguage(), sttMs, 0, 0, false));
            call.complete("voicemail");
            analyzePostCall(call);
            archiveSession(call);
            callRepository.save(call);
            dashboardEventPublisher.callEnded(call);
            return new TurnResult(call.getAgent().getDefaultLanguage(), "", new byte[0], "voicemail");
        }

        if (call.getAgent().isHandleCallScreening()
                && hasNoCallerTurns(call)
                && looksLikeCallScreening(callerTranscript)) {
            String response = "This is " + call.getAgent().getName() + " calling on behalf of "
                    + call.getTenant().getBusinessName() + " about your requested service.";
            call.appendTurn(call.getAgent().getDefaultLanguage(), callerTranscript, response);
            var turnIndex = callTurnRepository.countByCall_Id(call.getId()) + 1;
            callTurnRepository.save(new CallTurn(call, turnIndex, callerTranscript, response,
                    call.getAgent().getDefaultLanguage(), sttMs, 0, 0, false));
            return new TurnResult(call.getAgent().getDefaultLanguage(), response, new byte[0], "");
        }

        String language = languageDetector.detect(
                callerTranscript,
                call.getLanguageDetected() == null
                        ? call.getAgent().getDefaultLanguage()
                        : call.getLanguageDetected(),
                call.getAgent().getSupportedLanguages()
        );

        long llmStart = System.nanoTime();
        var conversationTurn = conversationOrchestrator.handleUserUtterance(call, language, callerTranscript);
        String response = conversationTurn.responseText();
        int llmMs = elapsedMs(llmStart);

        long ttsStart = System.nanoTime();
        byte[] audio = synthesizeAudio ? ttsProvider.synthesize(language, response) : new byte[0];
        int ttsMs = elapsedMs(ttsStart);

        var interrupted = callSessionStore.consumeInterrupted(call.getTwilioCallSid());
        if (call.getAgent().isSaveTranscript() || "test".equals(call.getDirection())) {
            call.appendTurn(language, callerTranscript, response);
            int turnIndex = callTurnRepository.countByCall_Id(call.getId()) + 1;
            callTurnRepository.save(new CallTurn(call, turnIndex, callerTranscript, response, language, sttMs, llmMs, ttsMs, interrupted));
        }
        var outcome = conversationTurn.outcome();
        if ((outcome == null || outcome.isBlank()) && looksLikeConversationEnding(callerTranscript)) {
            outcome = "completed";
        }
        if (outcome != null && !outcome.isBlank()) {
            call.complete(outcome);
            analyzePostCall(call);
            if (synthesizeAudio) {
                archiveSession(call);
            }
            callRepository.save(call);
            dashboardEventPublisher.callEnded(call);
        }
        return new TurnResult(language, response, audio, outcome == null ? "" : outcome);
    }

    public String resolveGreeting(Agent agent) {
        var result = agent.getGreetingMessage()
                .replace("{{agent_name}}", agent.getName())
                .replace("{{timezone}}", agent.getTimezone());
        for (var variable : agentVariableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId())) {
            if (variable.isFilled()) {
                result = result.replace("{{" + variable.getKey() + "}}", variable.getValue());
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public String openingGreeting(Call call) {
        return callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId()).stream()
                .map(CallTurn::getAgentResponse)
                .filter(response -> response != null && !response.isBlank())
                .findFirst()
                .orElse("");
    }

    private boolean hasNoCallerTurns(Call call) {
        return callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId()).stream()
                .noneMatch(turn -> turn.getCallerTranscript() != null && !turn.getCallerTranscript().isBlank());
    }

    private void archiveSession(Call call) {
        if (!call.getAgent().isSaveTranscript()) {
            deleteSessionAfterCommit(call.getTwilioCallSid());
            return;
        }
        callSessionStore.snapshotForArchive(call.getTwilioCallSid())
                .ifPresent(json -> {
                    call.archiveConversation(json);
                    deleteSessionAfterCommit(call.getTwilioCallSid());
                });
    }

    private void deleteSessionAfterCommit(String callSid) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callSessionStore.delete(callSid);
                }
            });
            return;
        }
        callSessionStore.delete(callSid);
    }

    private boolean isTerminalTwilioStatus(String callStatus) {
        if (callStatus == null || callStatus.isBlank()) {
            return false;
        }
        return switch (callStatus) {
            case "completed", "failed", "busy", "no-answer", "canceled" -> true;
            default -> false;
        };
    }

    private boolean looksLikeVoicemail(String transcript) {
        if (transcript == null) return false;
        String value = transcript.toLowerCase(java.util.Locale.ROOT);
        return value.contains("leave a message")
                || value.contains("after the tone")
                || value.contains("not available")
                || value.contains("record your message")
                || value.contains("voicemail")
                || value.contains("laissez un message")
                || value.contains("après le bip")
                || value.contains("messagerie")
                || value.contains("acha ujumbe")
                || value.contains("baada ya mlio")
                || value.contains("hapatikani")
                || value.contains("اترك رسالة")
                || value.contains("بعد الصافرة")
                || value.contains("غير متاح")
                || value.contains("البريد الصوتي");
    }

    private boolean looksLikeCallScreening(String transcript) {
        if (transcript == null) return false;
        String value = transcript.toLowerCase(java.util.Locale.ROOT);
        return value.contains("who is calling")
                || value.contains("what is this call about")
                || value.contains("state your name")
                || value.contains("reason for your call")
                || value.contains("qui appelle")
                || value.contains("objet de votre appel")
                || value.contains("donnez votre nom")
                || value.contains("nani anapiga simu")
                || value.contains("sababu ya simu")
                || value.contains("من المتصل")
                || value.contains("سبب الاتصال")
                || value.contains("اذكر اسمك");
    }

    private boolean looksLikeConversationEnding(String transcript) {
        if (transcript == null) return false;
        String value = transcript.toLowerCase(java.util.Locale.ROOT).trim();
        return value.matches(".*\\b(goodbye|bye|nothing else|that is all|that's all)\\b.*")
                || value.contains("au revoir")
                || value.contains("c'est tout")
                || value.contains("rien d'autre")
                || value.contains("kwaheri")
                || value.contains("hayo tu")
                || value.contains("hamna lingine")
                || value.contains("مع السلامة")
                || value.contains("هذا كل شيء");
    }

    private void analyzePostCall(Call call) {
        postCallAnalysisService.schedule(call);
    }

    private int elapsedMs(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }

    public record TurnResult(String language, String text, byte[] audio, String outcome) {
    }

    public record ReminderResult(String language, String text) {
    }
}
