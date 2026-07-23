package com.sauti.call;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Low-cardinality operational metrics shared by browser and phone voice runtimes. */
@Component
public class VoiceRuntimeMetrics {
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> activeSessions = new ConcurrentHashMap<>();

    public VoiceRuntimeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void sessionStarted(String runtime, String channel) {
        active(runtime, channel).incrementAndGet();
        Counter.builder("sauti.voice.sessions.started")
                .tag("runtime", runtime).tag("channel", channel)
                .register(registry).increment();
    }

    public void sessionEnded(String runtime, String channel) {
        active(runtime, channel).updateAndGet(value -> Math.max(0, value - 1));
        Counter.builder("sauti.voice.sessions.ended")
                .tag("runtime", runtime).tag("channel", channel)
                .register(registry).increment();
    }

    public void recordLatency(String stage, String runtime, String channel, long elapsedNanos) {
        Timer.builder("sauti.voice.latency")
                .description("Voice pipeline latency by stage")
                .tag("stage", stage).tag("runtime", runtime).tag("channel", channel)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry)
                .record(Math.max(0, elapsedNanos), TimeUnit.NANOSECONDS);
    }

    public void interruption(String runtime, String channel) {
        counter("sauti.voice.interruptions", runtime, channel, "reason", "caller_speech").increment();
    }

    public void playbackUnderrun(String runtime, String channel, int targetBufferMs) {
        Counter.builder("sauti.voice.playback.underruns")
                .description("Audible playback buffers that ran dry before provider completion")
                .tag("runtime", runtime).tag("channel", channel)
                .register(registry).increment();
        if (targetBufferMs > 0) {
            DistributionSummary.builder("sauti.voice.playback.rebuffer.target")
                    .description("Adaptive PCM target selected after a playback underrun")
                    .baseUnit("milliseconds")
                    .tag("runtime", runtime).tag("channel", channel)
                    .publishPercentileHistogram()
                    .minimumExpectedValue(100.0)
                    .maximumExpectedValue(1_000.0)
                    .register(registry)
                    .record(targetBufferMs);
        }
    }

    public void fallback(String runtime, String channel, String reason) {
        counter("sauti.voice.fallbacks", runtime, channel, "reason", reason).increment();
    }

    public void failure(String runtime, String channel, String stage) {
        counter("sauti.voice.failures", runtime, channel, "stage", stage).increment();
    }

    private AtomicInteger active(String runtime, String channel) {
        var key = runtime + ":" + channel;
        return activeSessions.computeIfAbsent(key, ignored -> {
            var value = new AtomicInteger();
            Gauge.builder("sauti.voice.sessions.active", value, AtomicInteger::get)
                    .description("Currently active Sauti voice sessions")
                    .tag("runtime", runtime).tag("channel", channel)
                    .register(registry);
            return value;
        });
    }

    private Counter counter(String name, String runtime, String channel, String detailKey, String detailValue) {
        return Counter.builder(name)
                .tag("runtime", runtime).tag("channel", channel).tag(detailKey, detailValue)
                .register(registry);
    }
}
