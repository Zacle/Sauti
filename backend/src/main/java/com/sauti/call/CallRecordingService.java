package com.sauti.call;

import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallRecordingService {
    private final CallRepository callRepository;
    private final Path recordingsDirectory;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ManagedVoiceProviderHttpClient providerHttpClient;
    private final String telnyxApiKey;
    private final String telnyxApiBaseUrl;

    public CallRecordingService(
            CallRepository callRepository,
            ManagedVoiceProviderHttpClient providerHttpClient,
            @Value("${sauti.recordings.directory:/data/recordings}") String recordingsDirectory,
            @Value("${sauti.telnyx.api-key:}") String telnyxApiKey,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String telnyxApiBaseUrl
    ) {
        this.callRepository = callRepository;
        this.providerHttpClient = providerHttpClient;
        this.recordingsDirectory = Path.of(recordingsDirectory).toAbsolutePath().normalize();
        this.telnyxApiKey = telnyxApiKey == null ? "" : telnyxApiKey.trim();
        this.telnyxApiBaseUrl = stripTrailingSlash(telnyxApiBaseUrl);
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

    @Transactional(readOnly = true)
    public RecordingData read(UUID tenantId, UUID callId) {
        var call = callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        if (call.getRecordingUrl() == null || call.getRecordingUrl().isBlank()) {
            throw new EntityNotFoundException("Recording not found");
        }
        if (call.getRecordingUrl().startsWith("https://")) {
            return readTelnyxRecording(currentTelnyxDownloadUrl(
                    call.getRecordingSid(), call.getRecordingUrl()
            ));
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

    private RecordingData readTelnyxRecording(String recordingUrl) {
        if (telnyxApiKey.isBlank()) {
            throw new IllegalStateException("Telnyx recording credentials are not configured");
        }
        try {
            var request = HttpRequest.newBuilder(URI.create(recordingUrl))
                    .header("Authorization", "Bearer " + telnyxApiKey)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Telnyx recording download failed with HTTP " + response.statusCode());
            }
            return new RecordingData(response.body(), "audio/mpeg");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telnyx recording download was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to download Telnyx recording", exception);
        }
    }

    private String currentTelnyxDownloadUrl(String recordingId, String fallbackUrl) {
        if (telnyxApiKey.isBlank()
                || recordingId == null || recordingId.isBlank()
                || recordingId.startsWith("TEST-")
                || recordingId.startsWith("WEBVOICE-")) {
            return fallbackUrl;
        }
        try {
            var response = providerHttpClient.get(
                    "Telnyx",
                    URI.create(telnyxApiBaseUrl + "/recordings/"
                            + java.net.URLEncoder.encode(recordingId, java.nio.charset.StandardCharsets.UTF_8)),
                    java.util.Map.of("Authorization", "Bearer " + telnyxApiKey)
            ).path("data").path("download_urls");
            var current = response.path("mp3").asText(response.path("wav").asText("")).trim();
            return current.isBlank() ? fallbackUrl : current;
        } catch (Exception exception) {
            return fallbackUrl;
        }
    }

    private static String stripTrailingSlash(String value) {
        var normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }

    public record RecordingData(byte[] bytes, String mediaType) {
    }
}
