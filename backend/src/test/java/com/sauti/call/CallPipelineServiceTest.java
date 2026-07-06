package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.agent.AgentVariableRepository;
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
        var service = new CallPipelineService(
                callRepository,
                callTurnRepository,
                agentRepository,
                agentVariableRepository,
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
        agent.update(
                "Amina", "Welcome", "Prompt", "en", List.of("en", "sw"),
                null, List.of(), false, "Africa/Nairobi", ""
        );
        agent.configureWebVoice(true, List.of(), true);
        agent.activate();
        when(agentRepository.findByWebVoicePublicId(agent.getWebVoicePublicId())).thenReturn(Optional.of(agent));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentVariableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()))
                .thenReturn(List.of());

        var call = service.startWebCall(agent.getWebVoicePublicId(), "sw");

        assertThat(call.getDirection()).isEqualTo("web");
        assertThat(call.getLanguageDetected()).isEqualTo("sw");
        verify(callSessionStore).createIfAbsent(org.mockito.ArgumentMatchers.eq(call.getTwilioCallSid()), any(CallSession.class));
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
