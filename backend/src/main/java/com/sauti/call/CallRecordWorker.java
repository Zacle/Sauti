package com.sauti.call;

import com.sauti.integration.PostCallIntegrationService;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Completes each finished call's record, durably. A phone call's transcript is read from the voice
 * provider (only it heard the conversation), replacing the greeting-only transcript stored during
 * the call; then the transcript is summarised. Each stage retries on its own schedule and then gives
 * up, and integrations are released once the job is done, whatever the outcome.
 *
 * <p>Provider and model requests run outside any database transaction; a lease stops a second run
 * from picking up a job that is being worked on.
 */
@Component
public class CallRecordWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallRecordWorker.class);
    /** 5 s after the call, then 10 s, 20 s, 40 s, 80 s, 160 s: about five minutes in all. */
    static final int TRANSCRIPT_ATTEMPTS = 6;
    static final int ANALYSIS_ATTEMPTS = 3;
    static final Duration LEASE = Duration.ofMinutes(2);
    static final int MAX_TURNS = 400;
    static final int MAX_LINE = 4000;
    private static final int BATCH = 10;

    private final CallRecordJobRepository jobs;
    private final CallRepository calls;
    private final CallTurnRepository turns;
    private final PostCallAnalysisService analysis;
    private final PostCallIntegrationService integrations;
    private final ObjectProvider<ProviderConversations> conversations;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public CallRecordWorker(CallRecordJobRepository jobs, CallRepository calls, CallTurnRepository turns,
                            PostCallAnalysisService analysis, PostCallIntegrationService integrations,
                            ObjectProvider<ProviderConversations> conversations,
                            PlatformTransactionManager transactionManager) {
        this(jobs, calls, turns, analysis, integrations, conversations, transactionManager, Clock.systemUTC());
    }

    CallRecordWorker(CallRecordJobRepository jobs, CallRepository calls, CallTurnRepository turns,
                     PostCallAnalysisService analysis, PostCallIntegrationService integrations,
                     ObjectProvider<ProviderConversations> conversations,
                     PlatformTransactionManager transactionManager, Clock clock) {
        this.jobs = jobs;
        this.calls = calls;
        this.turns = turns;
        this.analysis = analysis;
        this.integrations = integrations;
        this.conversations = conversations;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${sauti.calls.record-worker-initial-delay-ms:10000}",
            fixedDelayString = "${sauti.calls.record-worker-delay-ms:5000}")
    public void runDue() {
        for (var callId : jobs.findDue(OffsetDateTime.now(clock), PageRequest.of(0, BATCH))) {
            try {
                run(callId);
            } catch (RuntimeException failure) {
                LOGGER.warn("Call record work failed callId={}: {}", callId, failure.getClass().getSimpleName());
            }
        }
    }

    /** Runs the job's current stage once, if it is due. */
    void run(UUID callId) {
        var stage = transactions.execute(status -> {
            var job = jobs.findById(callId).orElse(null);
            var now = OffsetDateTime.now(clock);
            if (job == null || job.isDone() || job.getNextAttemptAt().isAfter(now)) return null;
            job.lease(now.plus(LEASE));
            jobs.save(job);
            return job.getStage();
        });
        if (CallRecordJob.TRANSCRIPT.equals(stage)) readTranscript(callId);
        else if (CallRecordJob.ANALYSIS.equals(stage)) summarise(callId);
    }

    private void readTranscript(UUID callId) {
        var providerCallId = transactions.execute(status ->
                calls.findById(callId).map(Call::getTwilioCallSid).orElse(null));
        var provider = conversations.getIfAvailable();
        var lookup = providerCallId == null || provider == null
                ? ProviderConversations.Lookup.unavailable()
                : provider.conversation(providerCallId);
        var done = transactions.execute(status -> {
            var job = jobs.findById(callId).orElse(null);
            var call = calls.findById(callId).orElse(null);
            if (job == null) return false;
            var now = OffsetDateTime.now(clock);
            if (call == null) {
                job.transcriptFinished("unavailable", CallRecordJob.DONE, now);
            } else if (lookup.status() == ProviderConversations.Status.NOT_READY
                    && job.getAttempts() + 1 < TRANSCRIPT_ATTEMPTS) {
                job.retry("The provider has not returned the conversation yet",
                        now.plus(PostCallAnalysisService.FIRST_TRANSCRIPT_LOOKUP.multipliedBy(2L << job.getAttempts())));
            } else {
                var result = lookup.status() == ProviderConversations.Status.FOUND ? store(call, lookup.messages()) : "unavailable";
                job.transcriptFinished(result, analysis.needsAnalysis(call) ? CallRecordJob.ANALYSIS : CallRecordJob.DONE, now);
            }
            jobs.save(job);
            return job.isDone();
        });
        if (Boolean.TRUE.equals(done)) integrations.analysisCompleted(callId);
    }

    /**
     * Rebuilds the call's turns from the provider's conversation. Lines a caller says before the
     * agent answers are joined into one caller turn, as during a browser call.
     */
    String store(Call call, List<ProviderConversations.Message> messages) {
        if (!call.getAgent().isSaveTranscript() || call.getPrivacyRedactedAt() != null) return "unavailable";
        var built = new ArrayList<String[]>();
        var caller = new StringBuilder();
        for (var message : messages) {
            var text = clip(message.text());
            if ("caller".equals(message.role())) {
                if (!caller.isEmpty()) caller.append(' ');
                caller.append(text);
                continue;
            }
            var spoken = VoiceOutputGuard.speechText(text);
            if (spoken.isBlank()) continue;
            built.add(new String[] {clip(caller.toString()), clip(spoken)});
            caller.setLength(0);
        }
        if (!caller.isEmpty()) built.add(new String[] {clip(caller.toString()), ""});
        if (built.isEmpty()) return "empty";
        var kept = built.size() <= MAX_TURNS ? built : built.subList(0, MAX_TURNS);
        var language = call.getLanguageDetected() == null || call.getLanguageDetected().isBlank()
                ? call.getAgent().getDefaultLanguage() : call.getLanguageDetected();
        turns.deleteByCall_Id(call.getId());
        turns.flush();
        var transcript = new StringBuilder();
        for (var index = 0; index < kept.size(); index++) {
            var turn = kept.get(index);
            turns.save(new CallTurn(call, index + 1, turn[0], turn[1], language, 0, 0, 0, false));
            if (!turn[0].isBlank()) transcript.append("Caller: ").append(turn[0]).append('\n');
            if (!turn[1].isBlank()) transcript.append("Agent: ").append(turn[1]).append('\n');
        }
        call.replaceTranscript(transcript.toString());
        calls.save(call);
        return "imported";
    }

    private void summarise(UUID callId) {
        var request = transactions.execute(status -> calls.findById(callId)
                .filter(analysis::needsAnalysis)
                .map(analysis::request)
                .orElse(null));
        PostCallAnalysisService.AnalysisResult result = null;
        String error = null;
        if (request != null) {
            try {
                result = analysis.analyze(request);
            } catch (Exception failure) {
                error = failure.getClass().getSimpleName();
                LOGGER.warn("Post-call analysis failed callId={}: {}", callId, error);
            }
        }
        var outcome = result;
        var failure = error;
        var done = transactions.execute(status -> {
            var job = jobs.findById(callId).orElse(null);
            if (job == null) return false;
            if (request == null) {
                job.analysisFinished("skipped");
            } else if (outcome != null) {
                calls.findById(callId).ifPresent(call -> {
                    call.applyAnalysis(outcome.summary(), outcome.successful(), outcome.sentiment(), outcome.intent());
                    calls.save(call);
                });
                job.analysisFinished("completed");
            } else if (job.getAttempts() + 1 < ANALYSIS_ATTEMPTS) {
                job.retry(failure, OffsetDateTime.now(clock).plus(Duration.ofSeconds(30L << (2 * job.getAttempts()))));
            } else {
                job.analysisFinished("failed");
            }
            jobs.save(job);
            return job.isDone();
        });
        if (Boolean.TRUE.equals(done)) integrations.analysisCompleted(callId);
    }

    private static String clip(String text) {
        var trimmed = text == null ? "" : text.trim();
        return trimmed.length() <= MAX_LINE ? trimmed : trimmed.substring(0, MAX_LINE);
    }
}
