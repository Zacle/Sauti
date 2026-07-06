package com.sauti.call;

import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallRecordingService {
    private final CallRepository callRepository;
    private final Path recordingsDirectory;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String twilioAccountSid;
    private final String twilioAuthToken;

    public CallRecordingService(
            CallRepository callRepository,
            @Value("${sauti.recordings.directory:/data/recordings}") String recordingsDirectory,
            @Value("${sauti.twilio.account-sid:}") String twilioAccountSid,
            @Value("${sauti.twilio.auth-token:}") String twilioAuthToken
    ) {
        this.callRepository = callRepository;
        this.recordingsDirectory = Path.of(recordingsDirectory).toAbsolutePath().normalize();
        this.twilioAccountSid = twilioAccountSid;
        this.twilioAuthToken = twilioAuthToken;
    }

    @Transactional
    public Call save(UUID tenantId, UUID callId, byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("Recording is empty");
        }
        var call = callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        try {
            Files.createDirectories(recordingsDirectory);
            var target = recordingsDirectory.resolve(callId + ".webm").normalize();
            if (!target.startsWith(recordingsDirectory)) {
                throw new IllegalArgumentException("Invalid recording path");
            }
            Files.write(target, audio);
            call.attachRecording("/api/v1/calls/" + callId + "/recording", "TEST-" + callId);
            return callRepository.save(call);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save call recording", exception);
        }
    }

    public WebVoiceRecordingWriter startWebVoiceRecording(UUID callId) {
        return new WebVoiceRecordingWriter(recordingsDirectory.resolve(callId + ".wav").normalize());
    }

    @Transactional
    public void completeWebVoiceRecording(UUID tenantId, UUID callId, WebVoiceRecordingWriter writer) {
        if (writer == null) return;
        writer.finish();
        var call = callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        call.attachRecording("/api/v1/calls/" + callId + "/recording", "WEBVOICE-" + callId);
        callRepository.save(call);
    }

    @Transactional(readOnly = true)
    public RecordingData read(UUID tenantId, UUID callId) {
        var call = callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (call.getRecordingUrl() == null || call.getRecordingUrl().isBlank()) {
            throw new EntityNotFoundException("Recording not found");
        }
        if (call.getRecordingUrl().startsWith("https://api.twilio.com/")) {
            return readTwilioRecording(call.getRecordingUrl());
        }
        try {
            boolean webVoice = call.getRecordingSid() != null && call.getRecordingSid().startsWith("WEBVOICE-");
            var extension = webVoice ? ".wav" : ".webm";
            return new RecordingData(
                    Files.readAllBytes(recordingsDirectory.resolve(callId + extension).normalize()),
                    webVoice ? "audio/wav" : "audio/webm"
            );
        } catch (IOException exception) {
            throw new EntityNotFoundException("Recording not found");
        }
    }

    private RecordingData readTwilioRecording(String recordingUrl) {
        if (twilioAccountSid.isBlank() || twilioAuthToken.isBlank()) {
            throw new IllegalStateException("Twilio recording credentials are not configured");
        }
        try {
            var mediaUrl = recordingUrl.endsWith(".mp3") ? recordingUrl : recordingUrl + ".mp3";
            var authorization = Base64.getEncoder().encodeToString(
                    (twilioAccountSid + ":" + twilioAuthToken).getBytes(StandardCharsets.UTF_8)
            );
            var request = HttpRequest.newBuilder(URI.create(mediaUrl))
                    .header("Authorization", "Basic " + authorization)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Twilio recording download failed with HTTP " + response.statusCode());
            }
            return new RecordingData(response.body(), "audio/mpeg");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Twilio recording download was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to download Twilio recording", exception);
        }
    }

    public record RecordingData(byte[] bytes, String mediaType) {
    }
}
