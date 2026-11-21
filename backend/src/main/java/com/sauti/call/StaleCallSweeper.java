package com.sauti.call;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ends voice calls that are still marked active well past their agent's maximum length, because
 * their hang-up event was lost (a restart, a dropped webhook). Without this they would stay "live"
 * forever, keep their session, and never be analysed or sent to integrations.
 *
 * <p>A phone call is closed only once the provider says it has ended, or, if the provider cannot
 * be asked, an hour after it should have ended. Browser and test calls have no provider leg, so
 * they are closed after the grace period. Every closed call is billed up to its maximum length at
 * most, never for the time nobody noticed.
 */
@Component
public class StaleCallSweeper {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaleCallSweeper.class);
    /** Allowance past the agent's maximum length for transfers and late events. */
    static final Duration GRACE = Duration.ofMinutes(5);
    /** When the provider cannot confirm a phone call ended, wait this long past the maximum. */
    static final Duration UNCONFIRMED_LIMIT = Duration.ofHours(1);
    private static final Set<String> PHONE = Set.of("inbound", "outbound");
    private static final int BATCH = 100;

    private final CallRepository calls;
    private final CallPipelineService pipeline;
    private final ObjectProvider<CallLiveness> liveness;
    private final Clock clock;

    @Autowired
    public StaleCallSweeper(CallRepository calls, CallPipelineService pipeline, ObjectProvider<CallLiveness> liveness) {
        this(calls, pipeline, liveness, Clock.systemUTC());
    }

    StaleCallSweeper(CallRepository calls, CallPipelineService pipeline, ObjectProvider<CallLiveness> liveness,
                     Clock clock) {
        this.calls = calls;
        this.pipeline = pipeline;
        this.liveness = liveness;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${sauti.calls.stale-sweep-initial-delay-ms:60000}",
            fixedDelayString = "${sauti.calls.stale-sweep-delay-ms:300000}")
    public void sweep() {
        var now = OffsetDateTime.now(clock);
        // The shortest allowed call is one minute, so nothing younger than that plus grace can be stale.
        var candidates = calls.findActiveVoiceCallsStartedBefore(
                now.minus(Duration.ofMinutes(1)).minus(GRACE), PageRequest.of(0, BATCH));
        for (var call : candidates) {
            try {
                sweep(call, now);
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not close a stale call callId={}", call.getId(), failure);
            }
        }
    }

    private void sweep(Call call, OffsetDateTime now) {
        var shouldHaveEnded = call.getStartedAt().plusSeconds(call.getAgent().getMaxCallDurationSeconds());
        if (now.isBefore(shouldHaveEnded.plus(GRACE))) return;
        if (PHONE.contains(call.getDirection())) {
            var provider = liveness.getIfAvailable();
            var alive = provider == null ? java.util.Optional.<Boolean>empty()
                    : provider.isAlive(call.getTwilioCallSid());
            if (alive.orElse(false) || (alive.isEmpty() && now.isBefore(shouldHaveEnded.plus(UNCONFIRMED_LIMIT)))) {
                return;
            }
        }
        LOGGER.info("Closing a call whose end was never reported callId={} direction={}",
                call.getId(), call.getDirection());
        pipeline.closeStaleCall(call.getId(), shouldHaveEnded);
    }
}
