package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.agent.AgentVariableRepository;
import com.sauti.agent.AgentVariable;
import com.sauti.dashboard.DashboardEventPublisher;
import com.sauti.llm.ConversationOrchestrator;
import com.sauti.llm.ConversationTurnResult;
import com.sauti.nlp.LanguageDetector;
import com.sauti.session.CallSession;
import com.sauti.session.CallSessionStore;
import com.sauti.tenant.Tenant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CallPipelineServiceTest {
    @Test
    void resolvesPromptBusinessInSavedGreetingWithoutWorkspaceLeakage() {
        var variableRepository = mock(AgentVariableRepository.class);
        var tenant = new Tenant("Tranquil AI", "owner@example.com", "KE");
        var agent = new Agent(
                tenant,
                "Alec",
                "Hello, this is Alec from Tranquil AI. How can I help?",
                "You are Alec, the virtual assistant for X-Fit."
        );
        when(variableRepository.findByAgentIdAndKey(agent.getId(), "business_name"))
                .thenReturn(Optional.empty());
        when(variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()))
                .thenReturn(List.of());
        var service = new CallPipelineService(
                mock(CallRepository.class), mock(CallTurnRepository.class), mock(AgentRepository.class),
                variableRepository, mock(StreamingSttProvider.class), mock(LanguageDetector.class),
                mock(ConversationOrchestrator.class), mock(StreamingTtsProvider.class),
                mock(CallSessionStore.class), mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );

        assertThat(service.resolveGreeting(agent))
                .isEqualTo("Hello, this is Alec from X-Fit. How can I help?");
    }

    @Test
    void startTestCallUsesTheAgentBusinessNameInsteadOfTheTenantName() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var agentRepository = mock(AgentRepository.class);
        var tenant = new Tenant("Tranquil AI", "owner@example.com", "KE");
        var agent = new Agent(
                tenant,
                "Sarah",
                "Generate the opening at call time. Do not use a fixed script. Ask one simple opening question.",
                "Prompt"
        );
        agent.activate();
        var variableRepository = mock(AgentVariableRepository.class);
        var businessName = new AgentVariable(agent, "business_name", "Business name", null, true);
        businessName.updateValue("X-Fit");
        when(agentRepository.findByIdAndTenantId(agent.getId(), tenant.getId())).thenReturn(Optional.of(agent));
        when(variableRepository.findByAgentIdAndKey(agent.getId(), "business_name"))
                .thenReturn(Optional.of(businessName));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                agentRepository,
                variableRepository,
                mock(StreamingSttProvider.class),
                mock(LanguageDetector.class),
                mock(ConversationOrchestrator.class),
                mock(StreamingTtsProvider.class),
                mock(CallSessionStore.class),
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );

        service.startTestCall(tenant.getId(), agent.getId());

        var turn = ArgumentCaptor.forClass(CallTurn.class);
        verify(callTurnRepository).save(turn.capture());
        assertThat(turn.getValue().getAgentResponse())
                .isEqualTo("Bonjour, c'est Sarah de X-Fit. Comment puis-je vous aider ?");
    }

    @Test
    void startTestCallReplacesAFormerTenantNameInAnExactSavedGreeting() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var agentRepository = mock(AgentRepository.class);
        var tenant = new Tenant("Tranquil AI", "owner@example.com", "KE");
        var agent = new Agent(
                tenant,
                "Sarah",
                "Bonjour, c'est Sarah de Tranquil AI. Comment puis-je vous aider ?",
                "Prompt"
        );
        agent.activate();
        var variableRepository = mock(AgentVariableRepository.class);
        var businessName = new AgentVariable(agent, "business_name", "Business name", null, true);
        businessName.updateValue("X-Fit");
        when(agentRepository.findByIdAndTenantId(agent.getId(), tenant.getId())).thenReturn(Optional.of(agent));
        when(variableRepository.findByAgentIdAndKey(agent.getId(), "business_name"))
                .thenReturn(Optional.of(businessName));
        when(variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()))
                .thenReturn(List.of(businessName));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                agentRepository,
                variableRepository,
                mock(StreamingSttProvider.class),
                mock(LanguageDetector.class),
                mock(ConversationOrchestrator.class),
                mock(StreamingTtsProvider.class),
                mock(CallSessionStore.class),
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );

        service.startTestCall(tenant.getId(), agent.getId());

        var turn = ArgumentCaptor.forClass(CallTurn.class);
        verify(callTurnRepository).save(turn.capture());
        assertThat(turn.getValue().getAgentResponse())
                .isEqualTo("Bonjour, c'est Sarah de X-Fit. Comment puis-je vous aider ?");
    }

    @Test
    void startInboundCallCreatesCallSessionBeforeMediaStreamStarts() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var agentRepository = mock(AgentRepository.class);
        var agentVariableRepository = mock(AgentVariableRepository.class);
        var sttProvider = mock(StreamingSttProvider.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var ttsProvider = mock(StreamingTtsProvider.class);
        var callSessionStore = mock(CallSessionStore.class);
        var dashboardEventPublisher = mock(DashboardEventPublisher.class);
        var postCallAnalysisService = mock(PostCallAnalysisService.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                agentRepository,
                agentVariableRepository,
                sttProvider,
                languageDetector,
                conversationOrchestrator,
                ttsProvider,
                callSessionStore,
                dashboardEventPublisher,
                postCallAnalysisService
        );
        var tenant = new Tenant("Demo Clinic", "owner@example.com", "SN");
        var agent = new Agent(tenant, "Amina", "Bonjour", "Prompt");
        agent.activate();
        var call = new Call(tenant, agent, "CA123", "+221771234567", "inbound");
        when(agentRepository.findByTwilioPhoneNumber("+221770000000")).thenReturn(Optional.of(agent));
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(call));

        service.startInboundCall("+221770000000", "CA123", "+221771234567");

        verify(callSessionStore).createIfAbsent(org.mockito.ArgumentMatchers.eq("CA123"), any(CallSession.class));
        verify(dashboardEventPublisher).callStarted(call);
    }

    @Test
    void startWebCallUsesSupportedPreferredLanguage() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var agentRepository = mock(AgentRepository.class);
        var agentVariableRepository = mock(AgentVariableRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var dashboardEventPublisher = mock(DashboardEventPublisher.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                agentRepository,
                agentVariableRepository,
                mock(StreamingSttProvider.class),
                mock(LanguageDetector.class),
                conversationOrchestrator,
                mock(StreamingTtsProvider.class),
                callSessionStore,
                dashboardEventPublisher,
                mock(PostCallAnalysisService.class)
        );
        var tenant = new Tenant("Demo Clinic", "owner@example.com", "KE");
        var agent = new Agent(tenant, "Amina", "Welcome", "Prompt");
        agent.update(
                "Amina", "Welcome", "Prompt", "en", List.of("en", "fr"),
                null, List.of(), false, "Africa/Nairobi", ""
        );
        agent.configureWebVoice(true, List.of(), true);
        agent.activate();
        when(agentRepository.findByWebVoicePublicId(agent.getWebVoicePublicId())).thenReturn(Optional.of(agent));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var call = service.startWebCall(agent.getWebVoicePublicId(), "fr");

        assertThat(call.getDirection()).isEqualTo("web");
        assertThat(call.getLanguageDetected()).isEqualTo("fr");
        verify(callSessionStore).createIfAbsent(org.mockito.ArgumentMatchers.eq(call.getTwilioCallSid()), any(CallSession.class));
        var turnCaptor = ArgumentCaptor.forClass(CallTurn.class);
        verify(callTurnRepository).save(turnCaptor.capture());
        assertThat(turnCaptor.getValue().getAgentResponse())
                .isEqualTo("Bonjour, c'est Amina. Comment puis-je vous aider ?");
        verify(dashboardEventPublisher).callStarted(call);
    }

    @Test
    void startWhatsAppConversationCreatesAReusableChannelCall() {
        var callRepository = mock(CallRepository.class);
        var agentRepository = mock(AgentRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var dashboardEventPublisher = mock(DashboardEventPublisher.class);
        var service = new CallPipelineService(
                callRepository,
                mock(CallTurnRepository.class),
                agentRepository,
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                mock(LanguageDetector.class),
                mock(ConversationOrchestrator.class),
                mock(StreamingTtsProvider.class),
                callSessionStore,
                dashboardEventPublisher,
                mock(PostCallAnalysisService.class)
        );
        var tenant = new Tenant("Demo Clinic", "owner@example.com", "KE");
        var agent = new Agent(tenant, "Amina", "Welcome", "Prompt");
        agent.configureWhatsApp(true, "123456789");
        agent.activate();
        when(agentRepository.findByWhatsappPhoneNumberId("123456789")).thenReturn(Optional.of(agent));
        when(callRepository.findFirstByAgent_IdAndDirectionAndCallerNumberAndOutcomeOrderByStartedAtDesc(
                agent.getId(), "whatsapp", "254700000000", "active"
        )).thenReturn(Optional.empty());
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var call = service.startWhatsAppConversation("123456789", "254700000000");

        assertThat(call.getDirection()).isEqualTo("whatsapp");
        assertThat(call.getCallerNumber()).isEqualTo("254700000000");
        verify(callSessionStore).createIfAbsent(org.mockito.ArgumentMatchers.eq(call.getTwilioCallSid()), any(CallSession.class));
        verify(dashboardEventPublisher).callStarted(call);
    }

    @Test
    void savesInterruptedFlagFromCallSessionOnLiveTurn() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var agentRepository = mock(AgentRepository.class);
        var agentVariableRepository = mock(AgentVariableRepository.class);
        var sttProvider = mock(StreamingSttProvider.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var ttsProvider = mock(StreamingTtsProvider.class);
        var callSessionStore = mock(CallSessionStore.class);
        var dashboardEventPublisher = mock(DashboardEventPublisher.class);
        var postCallAnalysisService = mock(PostCallAnalysisService.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                agentRepository,
                agentVariableRepository,
                sttProvider,
                languageDetector,
                conversationOrchestrator,
                ttsProvider,
                callSessionStore,
                dashboardEventPublisher,
                postCallAnalysisService
        );
        var call = activeCall("CA123");
        when(languageDetector.detect("wait", "en", List.of("en", "fr"))).thenReturn("en");
        when(conversationOrchestrator.handleUserUtterance(call, "en", "wait"))
                .thenReturn(new ConversationTurnResult("Sure, I am listening.", ""));
        when(callTurnRepository.countByCall_Id(call.getId())).thenReturn(0);
        when(callSessionStore.consumeInterrupted("CA123")).thenReturn(true);

        service.processLiveTranscriptTurn(call, "wait");

        var captor = ArgumentCaptor.forClass(CallTurn.class);
        org.mockito.Mockito.verify(callTurnRepository).save(captor.capture());
        assertThat(captor.getValue().isInterrupted()).isTrue();
    }

    @Test
    void storesOnlySanitizedAgentSpeechFromRealtimeProviders() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var callSessionStore = mock(CallSessionStore.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                mock(AgentRepository.class),
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                mock(LanguageDetector.class),
                mock(ConversationOrchestrator.class),
                mock(StreamingTtsProvider.class),
                callSessionStore,
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );
        var call = activeCall("CA-guarded");
        when(callRepository.findByIdAndTenantId(call.getId(), call.getTenant().getId()))
                .thenReturn(Optional.of(call));
        when(callTurnRepository.countByCall_Id(call.getId())).thenReturn(0);

        service.recordRealtimeTranscript(
                call.getTenant().getId(), call.getId(), "agent",
                "assistant: Hi Walker, a men's haircut costs 5 dollars.", false
        );
        service.recordRealtimeTranscript(
                call.getTenant().getId(), call.getId(), "agent",
                "Hi Walker.\nanalysis to=functions.get_business_hours code", false
        );

        var turn = ArgumentCaptor.forClass(CallTurn.class);
        verify(callTurnRepository, times(1)).save(turn.capture());
        assertThat(turn.getValue().getAgentResponse())
                .isEqualTo("Hi Walker, a men's haircut costs 5 dollars.");
        verify(callSessionStore, times(1)).appendAssistantMessage(
                "CA-guarded", "Hi Walker, a men's haircut costs 5 dollars.", List.of()
        );
    }

    @Test
    void ignoresPunctuationOnlyLiveTranscript() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var agentRepository = mock(AgentRepository.class);
        var agentVariableRepository = mock(AgentVariableRepository.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var callSessionStore = mock(CallSessionStore.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                agentRepository,
                agentVariableRepository,
                mock(StreamingSttProvider.class),
                languageDetector,
                conversationOrchestrator,
                mock(StreamingTtsProvider.class),
                callSessionStore,
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );
        var call = activeCall("CA123");

        var result = service.processLiveTranscriptTurn(call, ".");

        assertThat(result.text()).isBlank();
        assertThat(result.outcome()).isBlank();
        verify(languageDetector, never()).detect(any(), any(), any());
        verify(conversationOrchestrator, never()).handleUserUtterance(any(), any(), any());
        verify(callTurnRepository, never()).save(any());
    }

    @Test
    void ignoresShortEnglishNoiseOnFrenchBrowserCallBeforeLanguageDetection() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                mock(AgentRepository.class),
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                languageDetector,
                conversationOrchestrator,
                mock(StreamingTtsProvider.class),
                mock(CallSessionStore.class),
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );
        var call = activeCall("TEST-123");
        call.selectLanguage("fr");
        when(callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId())).thenReturn(List.of());

        var result = service.processLiveTranscriptTurn(call, "Hello");

        assertThat(result.language()).isEqualTo("fr");
        assertThat(result.text()).isBlank();
        assertThat(result.outcome()).isBlank();
        assertThat(result.acceptedTranscript()).isFalse();
        verify(languageDetector, never()).detect(any(), any(), any());
        verify(conversationOrchestrator, never()).handleUserUtterance(any(), any(), any());
        verify(callTurnRepository, never()).save(any());
    }

    @Test
    void ignoresShortIndonesianNoiseOnFrenchBrowserCallBeforeLanguageDetection() {
        var callTurnRepository = mock(CallTurnRepository.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var service = new CallPipelineService(
                mock(CallRepository.class),
                callTurnRepository,
                mock(AgentRepository.class),
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                languageDetector,
                conversationOrchestrator,
                mock(StreamingTtsProvider.class),
                mock(CallSessionStore.class),
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );
        var call = activeCall("TEST-123");
        call.selectLanguage("fr");
        when(callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId())).thenReturn(List.of());

        var result = service.processLiveTranscriptTurn(call, "Terima kasih");

        assertThat(result.language()).isEqualTo("fr");
        assertThat(result.text()).isBlank();
        assertThat(result.acceptedTranscript()).isFalse();
        verify(languageDetector, never()).detect(any(), any(), any());
        verify(conversationOrchestrator, never()).handleUserUtterance(any(), any(), any());
        verify(callTurnRepository, never()).save(any());
    }

    @Test
    void asksForRepeatOnMangledFirstFrenchCallerTranscript() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                mock(AgentRepository.class),
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                languageDetector,
                conversationOrchestrator,
                mock(StreamingTtsProvider.class),
                mock(CallSessionStore.class),
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );
        var call = activeCall("TEST-123");
        call.selectLanguage("fr");
        when(callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId())).thenReturn(List.of(
                new CallTurn(call, 1, "", "Bonjour, c'est Amelie. Comment puis-je vous aider ?", "fr", 0, 0, 0, false)
        ));

        var result = service.processLiveTranscriptTurn(call, "Meli seza kare");

        assertThat(result.language()).isEqualTo("fr");
        assertThat(result.text()).contains("Je n'ai pas bien saisi");
        assertThat(result.acceptedTranscript()).isFalse();
        verify(languageDetector, never()).detect(any(), any(), any());
        verify(conversationOrchestrator, never()).handleUserUtterance(any(), any(), any());
        verify(callTurnRepository, never()).save(any());
    }

    @Test
    void acceptsClearShortFrenchFirstCallerTranscript() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                mock(AgentRepository.class),
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                languageDetector,
                conversationOrchestrator,
                mock(StreamingTtsProvider.class),
                mock(CallSessionStore.class),
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );
        var call = activeCall("TEST-123");
        call.selectLanguage("fr");
        when(callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId())).thenReturn(List.of(
                new CallTurn(call, 1, "", "Bonjour, c'est Amelie. Comment puis-je vous aider ?", "fr", 0, 0, 0, false)
        ));
        when(languageDetector.detect("Bonjour Amelie", "fr", List.of("en", "fr"))).thenReturn("fr");
        when(conversationOrchestrator.handleUserUtterance(call, "fr", "Bonjour Amelie"))
                .thenReturn(new ConversationTurnResult("Bonjour, comment puis-je vous aider ?", ""));

        var result = service.processLiveTranscriptTurn(call, "Bonjour Amelie");

        assertThat(result.language()).isEqualTo("fr");
        assertThat(result.acceptedTranscript()).isTrue();
        verify(languageDetector).detect("Bonjour Amelie", "fr", List.of("en", "fr"));
        verify(conversationOrchestrator).handleUserUtterance(call, "fr", "Bonjour Amelie");
        verify(callTurnRepository).save(any(CallTurn.class));
    }

    @Test
    void acceptsNormalFrenchTurnOnFrenchBrowserCall() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var callSessionStore = mock(CallSessionStore.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                mock(AgentRepository.class),
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                languageDetector,
                conversationOrchestrator,
                mock(StreamingTtsProvider.class),
                callSessionStore,
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );
        var call = activeCall("TEST-123");
        call.selectLanguage("fr");
        when(callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId())).thenReturn(List.of());
        when(languageDetector.detect("Bonjour", "fr", List.of("en", "fr"))).thenReturn("fr");
        when(conversationOrchestrator.handleUserUtterance(call, "fr", "Bonjour"))
                .thenReturn(new ConversationTurnResult("Bonjour, comment puis-je vous aider ?", ""));
        when(callTurnRepository.countByCall_Id(call.getId())).thenReturn(1);

        var result = service.processLiveTranscriptTurn(call, "Bonjour");

        assertThat(result.language()).isEqualTo("fr");
        assertThat(result.text()).contains("Bonjour");
        verify(languageDetector).detect("Bonjour", "fr", List.of("en", "fr"));
        verify(conversationOrchestrator).handleUserUtterance(call, "fr", "Bonjour");
        verify(callTurnRepository).save(any(CallTurn.class));
    }

    @Test
    void recognizesNaturalClosingPhrases() {
        var service = new CallPipelineService(
                mock(CallRepository.class),
                mock(CallTurnRepository.class),
                mock(AgentRepository.class),
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                mock(LanguageDetector.class),
                mock(ConversationOrchestrator.class),
                mock(StreamingTtsProvider.class),
                mock(CallSessionStore.class),
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );

        assertThat(service.looksLikeConversationEnding("No thank you, have a good day.")).isTrue();
        assertThat(service.looksLikeConversationEnding("Non merci, excellente journée.")).isTrue();
        assertThat(service.looksLikeConversationEnding("D'accord merci beaucoup, excellent jour à vous.")).isTrue();
        assertThat(service.looksLikeConversationEnding("orevoir")).isTrue();
        assertThat(service.looksLikeConversationEnding("Je vous souhaite une excellente journee et a tres bientot !")).isTrue();
        assertThat(service.looksLikeConversationEnding("I want to book tomorrow.")).isFalse();
    }

    @Test
    void recoversFromShortCrossLanguageTranscriptDriftAfterLanguageIsStable() {
        var callRepository = mock(CallRepository.class);
        var callTurnRepository = mock(CallTurnRepository.class);
        var languageDetector = mock(LanguageDetector.class);
        var conversationOrchestrator = mock(ConversationOrchestrator.class);
        var callSessionStore = mock(CallSessionStore.class);
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                mock(AgentRepository.class),
                mock(AgentVariableRepository.class),
                mock(StreamingSttProvider.class),
                languageDetector,
                conversationOrchestrator,
                mock(StreamingTtsProvider.class),
                callSessionStore,
                mock(DashboardEventPublisher.class),
                mock(PostCallAnalysisService.class)
        );
        var call = activeCall("CA123");
        call.selectLanguage("fr");
        when(callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId())).thenReturn(List.of(
                new CallTurn(call, 1, "Bonjour", "Bonjour.", "fr", 0, 0, 0, false),
                new CallTurn(call, 2, "Je veux un rendez-vous", "Bien sûr.", "fr", 0, 0, 0, false)
        ));
        when(callTurnRepository.countByCall_Id(call.getId())).thenReturn(2);

        var result = service.processLiveTranscriptTurn(call, "اسیورکو۔");

        assertThat(result.language()).isEqualTo("fr");
        assertThat(result.text()).contains("Je n'ai pas bien saisi");
        assertThat(result.outcome()).isBlank();
        verify(languageDetector, never()).detect(any(), any(), any());
        verify(conversationOrchestrator, never()).handleUserUtterance(any(), any(), any());
        verify(callTurnRepository).save(any(CallTurn.class));
    }

    private Call activeCall(String callSid) {
        var tenant = new Tenant("Demo Clinic", "owner@example.com", "SN");
        var agent = new Agent(tenant, "Amina", "Bonjour", "Prompt");
        agent.update(
                "Amina",
                "Bonjour",
                "Prompt",
                "en",
                List.of("en", "fr"),
                "+221770000000",
                List.of("speak to a human"),
                true,
                "Africa/Dakar",
                ""
        );
        agent.activate();
        return new Call(tenant, agent, callSid, "+221771234567", "inbound");
    }
}
