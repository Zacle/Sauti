package com.sauti.call;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallTransferService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallTransferService.class);
    private final CallRepository callRepository;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "call-transfer");
        thread.setDaemon(true);
        return thread;
    });
    private final String accountSid;
    private final String authToken;
    private final String publicBaseUrl;
    private final String callsApiBaseUrl;
    private final String webhookPath;

    public CallTransferService(
            CallRepository callRepository,
            @Value("${sauti.telephony.calls-api-base-url:https://api.twilio.com/2010-04-01/Accounts}") String callsApiBaseUrl,
            @Value("${sauti.telephony.account-sid:${sauti.twilio.account-sid:}}") String accountSid,
            @Value("${sauti.telephony.auth-token:${sauti.twilio.auth-token:}}") String authToken,
            @Value("${sauti.telephony.public-base-url:${sauti.twilio.public-base-url}}") String publicBaseUrl,
            @Value("${sauti.telephony.transfer-webhook-path:/webhooks/telephony/transfer}") String webhookPath
    ) {
        this.callRepository = callRepository;
        this.callsApiBaseUrl = callsApiBaseUrl.endsWith("/") ? callsApiBaseUrl.substring(0, callsApiBaseUrl.length() - 1) : callsApiBaseUrl;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.publicBaseUrl = publicBaseUrl;
        this.webhookPath = webhookPath;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Transactional
    public Map<String, Object> request(Call call, String reason) {
        var targetNumber = call.getAgent().getHumanTransferNumber();
        if (targetNumber == null || targetNumber.isBlank()) {
            throw new IllegalStateException("No human transfer number is configured");
        }
        if ("test".equals(call.getDirection())) {
            return Map.of(
                    "transferPending", false,
                    "simulated", true,
                    "targetNumber", targetNumber,
                    "reason", reason
            );
        }
        if (call.getTwilioCallSid() == null || call.getTwilioCallSid().isBlank()) {
            throw new IllegalStateException("The active call does not have a telephony call SID");
        }
        call.requestTransfer(targetNumber);
        callRepository.save(call);
        return Map.of(
                "transferPending", true,
                "callId", call.getId().toString(),
                "targetNumber", targetNumber,
                "reason", reason
        );
    }

    public CompletableFuture<Boolean> initiateAsync(UUID callId) {
        return CompletableFuture.supplyAsync(() -> initiate(callId), executor);
    }

    @Transactional
    public boolean isPending(UUID callId) {
        return callRepository.findById(callId)
                .map(call -> "requested".equals(call.getTransferStatus()))
                .orElse(false);
    }

    @Transactional
    public TransferResult handleDialResult(UUID callId, String dialStatus, String childCallSid) {
        var call = callRepository.findById(callId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer call not found"));
        var normalized = normalizeStatus(dialStatus);
        if ("completed".equals(normalized) || "answered".equals(normalized)) {
            call.markTransferResult("completed", childCallSid, null);
            callRepository.save(call);
            return result(call, true, normalized);
        }
        var reason = switch (normalized) {
            case "busy" -> "The team member's line was busy";
            case "no_answer" -> "The team member did not answer";
            case "canceled" -> "The transfer was canceled";
            default -> "The transfer could not be completed";
        };
        call.markTransferResult(normalized, childCallSid, reason);
        callRepository.save(call);
        return result(call, false, normalized);
    }

    private boolean initiate(UUID callId) {
        var call = callRepository.findById(callId).orElse(null);
        if (call == null || !call.isActive() || !"requested".equals(call.getTransferStatus())) return false;
        try {
            requireTelephonyCredentials();
            var callbackUrl = publicBaseUrl + webhookPath + "/" + call.getId();
            var twiml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Response>
                      <Dial action="%s" method="POST" timeout="20" answerOnBridge="true">
                        <Number>%s</Number>
                      </Dial>
                    </Response>
                    """.formatted(escapeXml(callbackUrl), escapeXml(call.getTransferTargetNumber()));
            var body = "Twiml=" + URLEncoder.encode(twiml, StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(URI.create(
                            callsApiBaseUrl + "/" + accountSid
                                    + "/Calls/" + call.getTwilioCallSid() + ".json"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", authorization())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                failInitiation(callId, "Telephony provider rejected the transfer with HTTP " + response.statusCode());
                return false;
            }
            markDialing(callId);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failInitiation(callId, "Transfer request was interrupted");
            return false;
        } catch (Exception exception) {
            failInitiation(callId, exception.getMessage());
            return false;
        }
    }

    private TransferResult result(Call call, boolean connected, String status) {
        return new TransferResult(
                connected,
                status,
                call.getTwilioCallSid(),
                call.getTenant().getId(),
                call.getAgent().getId()
        );
    }

    @Transactional
    protected void markDialing(UUID callId) {
        callRepository.findById(callId).ifPresent(call -> {
            call.markTransferDialing();
            callRepository.save(call);
        });
    }

    @Transactional
    protected void failInitiation(UUID callId, String reason) {
        callRepository.findById(callId).ifPresent(call -> {
            call.markTransferResult("failed", null, reason);
            callRepository.save(call);
        });
        LOGGER.warn("Transfer initiation failed for callId={}: {}", callId, reason);
    }

    private void requireTelephonyCredentials() {
        if (accountSid.isBlank() || authToken.isBlank()) {
            throw new IllegalStateException("Telephony credentials (account SID / project ID and auth token) are not configured");
        }
    }

    private String authorization() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8)
        );
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "failed";
        return status.trim().toLowerCase(java.util.Locale.ROOT).replace("-", "_");
    }

    private String escapeXml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public record TransferResult(
            boolean connected,
            String status,
            String twilioCallSid,
            UUID tenantId,
            UUID agentId
    ) {
    }
}
