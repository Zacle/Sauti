package com.sauti.call;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelnyxRecordingReconciliationService {
    static final String PENDING_PREFIX = "TELNYX-CALL-CONTROL:";
    private static final Logger LOGGER = LoggerFactory.getLogger(TelnyxRecordingReconciliationService.class);
    private final CallRepository callRepository;
    private final ManagedVoiceProviderHttpClient httpClient;
    private final TelnyxAiConversationService conversationService;
    private final String apiKey;
    private final String apiBaseUrl;

    public TelnyxRecordingReconciliationService(
            CallRepository callRepository,
            ManagedVoiceProviderHttpClient httpClient,
            TelnyxAiConversationService conversationService,
            @Value("${sauti.telnyx.api-key:}") String apiKey,
            @Value("${sauti.telnyx.api-base-url:https://api.telnyx.com/v2}") String apiBaseUrl
    ) {
        this.callRepository = callRepository;
        this.httpClient = httpClient;
        this.conversationService = conversationService;
        this.apiKey = trim(apiKey);
        this.apiBaseUrl = stripTrailingSlash(apiBaseUrl);
    }

    @Scheduled(fixedDelayString = "${sauti.telnyx.recording-reconciliation-delay-ms:10000}")
    @Transactional
    public void reconcilePending() {
        if (apiKey.isBlank()) return;
        var pending = new java.util.LinkedHashMap<java.util.UUID, Call>();
        callRepository
                .findTop25ByRecordingUrlIsNullAndRecordingSidStartingWithAndEndedAtIsNotNullOrderByEndedAtAsc(
                        PENDING_PREFIX
                ).forEach(call -> pending.put(call.getId(), call));
        callRepository
                .findTop25ByRecordingUrlIsNullAndRecordingSidStartingWithAndEndedAtIsNotNullOrderByEndedAtAsc(
                        "TELNYX-CONVERSATION:"
                ).forEach(call -> pending.put(call.getId(), call));
        callRepository
                .findTop25ByRecordingUrlIsNullAndRecordingSidIsNullAndEndedAtIsNotNullAndDirectionInAndEndedAtAfterOrderByEndedAtAsc(
                        java.util.List.of("test", "web"),
                        OffsetDateTime.now().minusHours(24)
                ).forEach(call -> pending.put(call.getId(), call));
        for (var call : pending.values()) {
            try {
                reconcile(call);
            } catch (Exception exception) {
                LOGGER.warn("Telnyx recording reconciliation failed callId={}", call.getId(), exception);
            }
        }
    }

    void reconcile(Call call) {
        var callControlId = call.pendingTelnyxCallControlId();
        if (call.getEndedAt().isBefore(OffsetDateTime.now().minusHours(24))) {
            call.markTelnyxRecordingUnavailable();
            callRepository.save(call);
            LOGGER.warn("Telnyx recording was not available within 24 hours callId={}", call.getId());
            return;
        }
        if (callControlId.isBlank()) {
            var conversationId = call.pendingTelnyxConversationId();
            callControlId = conversationId.isBlank()
                    ? conversationService.callControlIdForSautiCall(call)
                    : conversationService.callControlId(conversationId);
            if (callControlId.isBlank()) return;
            call.awaitTelnyxRecording(callControlId);
            callRepository.save(call);
        }

        var headers = Map.of(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        var response = httpClient.get(
                "Telnyx",
                URI.create(apiBaseUrl + "/recordings?filter%5Bcall_control_id%5D="
                        + encode(callControlId) + "&page%5Bsize%5D=10"),
                headers
        );
        for (var recording : response.path("data")) {
            if (!"completed".equalsIgnoreCase(recording.path("status").asText(""))) continue;
            var urls = recording.path("download_urls");
            var url = urls.path("mp3").asText(urls.path("wav").asText("")).trim();
            var recordingId = recording.path("id").asText("").trim();
            if (url.isBlank() || recordingId.isBlank()) continue;
            call.attachRecording(url, recordingId);
            callRepository.save(call);
            return;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        var normalized = trim(value);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
