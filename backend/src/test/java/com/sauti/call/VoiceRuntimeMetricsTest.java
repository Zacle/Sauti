package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class VoiceRuntimeMetricsTest {
    @Test
    void recordsBoundedRuntimeMetricsWithoutCallIdentifiers() {
        var registry = new SimpleMeterRegistry();
        var metrics = new VoiceRuntimeMetrics(registry);

        metrics.sessionStarted("hybrid", "web");
        metrics.recordLatency("session_to_first_audio", "hybrid", "web", 250_000_000L);
        metrics.interruption("hybrid", "web");
        metrics.fallback("hybrid", "web", "realtime_disconnect");
        metrics.failure("hybrid", "web", "tts_stream");
        metrics.sessionEnded("hybrid", "web");

        assertThat(registry.get("sauti.voice.sessions.started").counter().count()).isEqualTo(1);
        assertThat(registry.get("sauti.voice.sessions.active").gauge().value()).isZero();
        assertThat(registry.get("sauti.voice.latency").timer().count()).isEqualTo(1);
        assertThat(registry.get("sauti.voice.interruptions").counter().count()).isEqualTo(1);
        assertThat(registry.get("sauti.voice.fallbacks").counter().count()).isEqualTo(1);
        assertThat(registry.get("sauti.voice.failures").counter().count()).isEqualTo(1);
    }
}
