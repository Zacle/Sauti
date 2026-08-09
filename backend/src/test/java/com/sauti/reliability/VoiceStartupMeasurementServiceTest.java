package com.sauti.reliability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoiceStartupMeasurementServiceTest {
    private final VoiceStartupMeasurementRepository measurements = mock(VoiceStartupMeasurementRepository.class);
    private final CallRepository calls = mock(CallRepository.class);
    private final VoiceStartupMeasurementService service = new VoiceStartupMeasurementService(measurements, calls);

    @Test
    void recordsOneMeasurementForAnOwnedBrowserTestCall() {
        var tenantId = UUID.randomUUID();
        var callId = UUID.randomUUID();
        var call = mock(Call.class);
        when(call.getDirection()).thenReturn("test");
        when(calls.findByIdAndTenantId(callId, tenantId)).thenReturn(Optional.of(call));

        service.recordTestCall(tenantId, callId, 1250);

        verify(measurements).save(any(VoiceStartupMeasurement.class));
    }

    @Test
    void rejectsOutOfRangeAndWrongChannelMeasurements() {
        assertThatThrownBy(() -> service.recordPublicDemo("demo-1", 120_001))
                .isInstanceOf(IllegalArgumentException.class);
        var tenantId = UUID.randomUUID();
        var callId = UUID.randomUUID();
        var call = mock(Call.class);
        when(call.getDirection()).thenReturn("inbound");
        when(calls.findByIdAndTenantId(callId, tenantId)).thenReturn(Optional.of(call));

        assertThatThrownBy(() -> service.recordTestCall(tenantId, callId, 1000))
                .isInstanceOf(IllegalArgumentException.class);
        verify(measurements, never()).save(any());
    }

    @Test
    void repeatedSdkEventsKeepTheFirstMeasurement() {
        when(measurements.existsBySourceKey("demo:demo-1")).thenReturn(true);

        service.recordPublicDemo("demo-1", 1500);

        verify(measurements, never()).save(any());
    }
}
