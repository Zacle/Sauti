package com.sauti.call;

import com.sauti.agent.TelephonyProvider;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallTransferService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallTransferService.class);
    private final CallRepository callRepository;
    private final ObjectProvider<TelephonyProvider> telephonyProvider;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "call-transfer");
        thread.setDaemon(true);
        return thread;
    });

    public CallTransferService(
            CallRepository callRepository,
            ObjectProvider<TelephonyProvider> telephonyProvider
    ) {
        this.callRepository = callRepository;
        this.telephonyProvider = telephonyProvider;
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
            telephonyProvider.getObject().transfer(
                    call.getTwilioCallSid(),
                    call.getTransferTargetNumber(),
                    call.getAgent().getTwilioPhoneNumber()
            );
            markDialing(callId);
            return true;
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

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return "failed";
        return status.trim().toLowerCase(java.util.Locale.ROOT).replace("-", "_");
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
