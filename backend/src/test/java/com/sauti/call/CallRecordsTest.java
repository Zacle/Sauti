package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.integration.PostCallIntegrationService;
import com.sauti.testsupport.TestAccounts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A finished phone call gets its whole conversation from the provider, then a summary, once, and
 * durably. Not transactional: the worker commits each step as it does in production.
 */
@SpringBootTest(properties = "sauti.calls.record-worker-initial-delay-ms=3600000")
@AutoConfigureMockMvc
class CallRecordsTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired AgentRepository agents;
    @Autowired CallRepository calls;
    @Autowired CallTurnRepository turns;
    @Autowired CallRecordJobRepository jobs;
    @Autowired CallPipelineService pipeline;
    @Autowired ObjectProvider<ProviderConversations> conversationsProvider;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ProviderConversations conversations;
    @MockitoBean AuthRateLimitService rateLimits;
    @MockitoSpyBean PostCallAnalysisService analysis;
    @MockitoSpyBean PostCallIntegrationService integrations;

    @Test
    void aPhoneCallGetsItsWholeConversationAndThenASummary() throws Exception {
        var owner = accounts.owner("Records Clinic", "records-" + System.nanoTime() + "@example.com", "KE");
        var call = phoneCall(agent(owner));
        var sid = call.getTwilioCallSid();
        doReturn(true).when(analysis).needsAnalysis(any());
        doReturn(new PostCallAnalysisService.AnalysisResult("The caller booked a cleaning.", true, "positive", "book_appointment"))
                .when(analysis).analyze(any());
        when(conversations.conversation(sid)).thenReturn(
                ProviderConversations.Lookup.notReady(),
                ProviderConversations.Lookup.found(List.of(
                        new ProviderConversations.Message("agent", "Hello, Records Clinic.", at(0)),
                        new ProviderConversations.Message("caller", "I'd like a cleaning", at(3)),
                        new ProviderConversations.Message("caller", "on Monday.", at(4)),
                        new ProviderConversations.Message("agent", "Monday at ten is free. Shall I book it?", at(6)),
                        new ProviderConversations.Message("caller", "Yes please.", at(9)))));

        // Hang-up and conversation-ended both report the end: the work starts once.
        pipeline.completeActiveCall(sid, "completed");
        pipeline.completeActiveCall(sid, "completed");
        assertThat(jobs.findById(call.getId())).get().extracting(CallRecordJob::getStage).isEqualTo("transcript");

        mvc.perform(get(owner.path("/calls")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recordStatus").value("transcript"))
                .andExpect(jsonPath("$[0].transcript").doesNotExist())
                .andExpect(jsonPath("$[0].conversationJson").doesNotExist());

        var clock = new MovingClock();
        var worker = worker(clock);
        clock.advance(Duration.ofSeconds(6));
        worker.run(call.getId());
        assertThat(jobs.findById(call.getId()).orElseThrow().getAttempts()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(11));
        worker.run(call.getId());
        assertThat(jobs.findById(call.getId()).orElseThrow())
                .returns("analysis", CallRecordJob::getStage)
                .returns("imported", CallRecordJob::getTranscriptStatus);

        clock.advance(Duration.ofSeconds(1));
        worker.run(call.getId());
        var job = jobs.findById(call.getId()).orElseThrow();
        assertThat(job.getStage()).isEqualTo("done");
        assertThat(job.getAnalysisStatus()).isEqualTo("completed");
        verify(analysis, times(1)).analyze(any());
        verify(integrations, times(1)).analysisCompleted(call.getId());

        mvc.perform(get(owner.path("/calls/" + call.getId() + "/turns")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].callerTranscript").value(""))
                .andExpect(jsonPath("$[0].agentResponse").value("Hello, Records Clinic."))
                .andExpect(jsonPath("$[1].callerTranscript").value("I'd like a cleaning on Monday."))
                .andExpect(jsonPath("$[2].callerTranscript").value("Yes please."))
                .andExpect(jsonPath("$[2].agentResponse").value(""));
        mvc.perform(get(owner.path("/calls/" + call.getId())).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordStatus").doesNotExist())
                .andExpect(jsonPath("$.callSummary").value("The caller booked a cleaning."))
                .andExpect(jsonPath("$.intent").value("book_appointment"))
                .andExpect(jsonPath("$.sentiment").value("positive"))
                .andExpect(jsonPath("$.transcript").value(
                        "Agent: Hello, Records Clinic.\nCaller: I'd like a cleaning on Monday.\n"
                                + "Agent: Monday at ten is free. Shall I book it?\nCaller: Yes please.\n"));
    }

    @Test
    void aConversationThatNeverArrivesStillReleasesTheCall() throws Exception {
        var owner = accounts.owner("Quiet Clinic", "quiet-" + System.nanoTime() + "@example.com", "KE");
        var call = phoneCall(agent(owner));
        when(conversations.conversation(anyString())).thenReturn(ProviderConversations.Lookup.notReady());
        pipeline.completeActiveCall(call.getTwilioCallSid(), "completed");

        var clock = new MovingClock();
        var worker = worker(clock);
        for (var attempt = 0; attempt < CallRecordWorker.TRANSCRIPT_ATTEMPTS; attempt++) {
            clock.advance(Duration.ofMinutes(5));
            worker.run(call.getId());
        }

        var job = jobs.findById(call.getId()).orElseThrow();
        assertThat(job.getStage()).isEqualTo("done");
        assertThat(job.getTranscriptStatus()).isEqualTo("unavailable");
        verify(conversations, times(CallRecordWorker.TRANSCRIPT_ATTEMPTS)).conversation(call.getTwilioCallSid());
        verify(integrations, times(1)).analysisCompleted(call.getId());
        // Only the greeting was heard by Sauti: nothing worth summarising.
        verify(analysis, never()).analyze(any());
        assertThat(turns.findByCall_IdOrderByTurnIndexAsc(call.getId())).hasSize(1);
    }

    @Test
    void aFailingSummaryIsRetriedAndThenGivenUp() throws Exception {
        var owner = accounts.owner("Retry Clinic", "retry-" + System.nanoTime() + "@example.com", "KE");
        var call = phoneCall(agent(owner));
        when(conversations.conversation(anyString())).thenReturn(ProviderConversations.Lookup.found(List.of(
                new ProviderConversations.Message("caller", "Are you open on Sunday?", at(1)),
                new ProviderConversations.Message("agent", "We are closed on Sundays.", at(2)))));
        doReturn(true).when(analysis).needsAnalysis(any());
        doThrow(new IllegalStateException("model unavailable")).when(analysis).analyze(any());
        pipeline.completeActiveCall(call.getTwilioCallSid(), "completed");

        var clock = new MovingClock();
        var worker = worker(clock);
        for (var step = 0; step < 1 + CallRecordWorker.ANALYSIS_ATTEMPTS; step++) {
            clock.advance(Duration.ofMinutes(10));
            worker.run(call.getId());
        }

        var job = jobs.findById(call.getId()).orElseThrow();
        assertThat(job.getAnalysisStatus()).isEqualTo("failed");
        assertThat(job.getStage()).isEqualTo("done");
        verify(analysis, times(CallRecordWorker.ANALYSIS_ATTEMPTS)).analyze(any());
        verify(integrations, times(1)).analysisCompleted(call.getId());
    }

    @Test
    void aGreetingOnlyTranscriptIsNotSummarised() {
        assertThat(PostCallAnalysisService.hasCallerSpeech("Agent: Hello.\n")).isFalse();
        assertThat(PostCallAnalysisService.hasCallerSpeech("Agent: Hello.\nCaller: \n")).isFalse();
        assertThat(PostCallAnalysisService.hasCallerSpeech("Agent: Hello.\nCaller: Hi.\n")).isTrue();
    }

    private CallRecordWorker worker(Clock clock) {
        return new CallRecordWorker(jobs, calls, turns, analysis, integrations, conversationsProvider, transactions, clock);
    }

    private Call phoneCall(UUID agentId) {
        return new TransactionTemplate(transactions).execute(status -> {
            var agent = agents.findById(agentId).orElseThrow();
            var call = calls.save(new Call(agent.getTenant(), agent, "v3:records-" + UUID.randomUUID(),
                    "+254700000000", "inbound"));
            call.appendAgentMessage("en", "Hello, Records Clinic.");
            turns.save(new CallTurn(call, 1, "", "Hello, Records Clinic.", "en", 0, 0, 0, false));
            return calls.save(call);
        });
    }

    private UUID agent(TestAccounts.Member owner) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"agent":{"name":"Front desk","greetingMessage":"Hello.",
                                  "systemPrompt":"You answer the phone.","defaultLanguage":"en","supportedLanguages":["en"]}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
    }

    private static OffsetDateTime at(int seconds) {
        return OffsetDateTime.parse("2026-11-22T09:00:00Z").plusSeconds(seconds);
    }

    /** Starts at the real time and only moves when told to. */
    private static final class MovingClock extends Clock {
        private Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        void advance(Duration by) { now = now.plus(by); }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
