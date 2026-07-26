package com.sauti.call;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class TelnyxRecordingReconciliationServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void attachesTheCompletedRecordingFoundThroughTheCallControlId() throws Exception {
        var repository = mock(CallRepository.class);
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var call = mock(Call.class);
        when(call.pendingTelnyxCallControlId()).thenReturn("v3:provider-call");
        when(call.getEndedAt()).thenReturn(OffsetDateTime.now().minusMinutes(1));
        when(http.get(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/recordings"
                        + "?filter%5Bcall_control_id%5D=v3%3Aprovider-call&page%5Bsize%5D=10")),
                any()
        )).thenReturn(objectMapper.readTree("""
                {"data":[{
                  "id":"recording-123",
                  "status":"completed",
                  "download_urls":{"mp3":"https://api.telnyx.com/recording.mp3"}
                }]}
                """));
        var service = new TelnyxRecordingReconciliationService(
                repository, http, "secret", "https://api.telnyx.com/v2/"
        );

        service.reconcile(call);

        verify(call).attachRecording("https://api.telnyx.com/recording.mp3", "recording-123");
        verify(repository).save(call);
        verify(http).get(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/recordings"
                        + "?filter%5Bcall_control_id%5D=v3%3Aprovider-call&page%5Bsize%5D=10")),
                eq(Map.of(HttpHeaders.AUTHORIZATION, "Bearer secret"))
        );
    }

    @Test
    void keepsThePendingReferenceWhenTelnyxIsStillProcessingTheRecording() throws Exception {
        var repository = mock(CallRepository.class);
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var call = mock(Call.class);
        when(call.pendingTelnyxCallControlId()).thenReturn("v3:provider-call");
        when(call.getEndedAt()).thenReturn(OffsetDateTime.now().minusMinutes(1));
        when(http.get(any(), any(), any())).thenReturn(objectMapper.readTree("{\"data\":[]}"));
        var service = new TelnyxRecordingReconciliationService(
                repository, http, "secret", "https://api.telnyx.com/v2"
        );

        service.reconcile(call);

        verify(call, never()).attachRecording(any(), any());
        verify(repository, never()).save(call);
    }

    @Test
    void stopsRetryingARecordingThatTelnyxDidNotProduceWithinOneDay() {
        var repository = mock(CallRepository.class);
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var call = mock(Call.class);
        when(call.pendingTelnyxCallControlId()).thenReturn("v3:provider-call");
        when(call.getEndedAt()).thenReturn(OffsetDateTime.now().minusHours(25));
        var service = new TelnyxRecordingReconciliationService(
                repository, http, "secret", "https://api.telnyx.com/v2"
        );

        service.reconcile(call);

        verify(call).markTelnyxRecordingUnavailable();
        verify(repository).save(call);
        verify(http, never()).get(any(), any(), any());
    }

    @Test
    void attachesTheCompletedRecordingFoundThroughTheCallLegId() throws Exception {
        var repository = mock(CallRepository.class);
        var http = mock(ManagedVoiceProviderHttpClient.class);
        var call = mock(Call.class);
        when(call.pendingTelnyxCallControlId()).thenReturn("");
        when(call.pendingTelnyxCallLegId())
                .thenReturn("9311b6ba-88f5-11f1-b7ef-02420a21041f");
        when(call.getEndedAt()).thenReturn(OffsetDateTime.now().minusMinutes(1));
        when(http.get(
                eq("Telnyx"),
                eq(URI.create("https://api.telnyx.com/v2/recordings"
                        + "?filter%5Bcall_leg_id%5D=9311b6ba-88f5-11f1-b7ef-02420a21041f"
                        + "&page%5Bsize%5D=10")),
                any()
        )).thenReturn(objectMapper.readTree("""
                {"data":[{
                  "id":"recording-123",
                  "status":"completed",
                  "download_urls":{"mp3":"https://api.telnyx.com/recording.mp3"}
                }]}
                """));
        var service = new TelnyxRecordingReconciliationService(
                repository, http, "secret", "https://api.telnyx.com/v2"
        );

        service.reconcile(call);

        verify(call).attachRecording("https://api.telnyx.com/recording.mp3", "recording-123");
        verify(repository).save(call);
    }
}
