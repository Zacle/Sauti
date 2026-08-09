package com.sauti.reliability;

import com.sauti.call.CallRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceStartupMeasurementService {
    private static final int MAX_LATENCY_MS = 120_000;
    private final VoiceStartupMeasurementRepository measurements;
    private final CallRepository calls;

    public VoiceStartupMeasurementService(
            VoiceStartupMeasurementRepository measurements,
            CallRepository calls
    ) {
        this.measurements = measurements;
        this.calls = calls;
    }

    @Transactional
    public void recordTestCall(UUID tenantId, UUID callId, int latencyMs) {
        var call = calls.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (!"test".equals(call.getDirection())) {
            throw new IllegalArgumentException("Startup measurement is not for a browser test call");
        }
        record("test:" + callId, "browser_test", latencyMs);
    }

    @Transactional
    public void recordWebVoice(String callSid, int latencyMs) {
        var call = calls.findByTwilioCallSid(callSid)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (!"web".equals(call.getDirection())) {
            throw new IllegalArgumentException("Startup measurement is not for a Web Voice call");
        }
        record("web:" + callSid, "web_voice", latencyMs);
    }

    @Transactional
    public void recordPublicDemo(String sessionId, int latencyMs) {
        record("demo:" + sessionId, "public_demo", latencyMs);
    }

    private synchronized void record(String sourceKey, String channel, int latencyMs) {
        if (latencyMs < 0 || latencyMs > MAX_LATENCY_MS) {
            throw new IllegalArgumentException("Startup latency must be between 0 and 120000 milliseconds");
        }
        if (measurements.existsBySourceKey(sourceKey)) return;
        measurements.save(new VoiceStartupMeasurement(
                sourceKey, channel, latencyMs, OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
