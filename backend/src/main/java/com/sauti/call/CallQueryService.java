package com.sauti.call;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallQueryService {
    private final CallRepository callRepository;
    private final CallTurnRepository callTurnRepository;

    public CallQueryService(CallRepository callRepository, CallTurnRepository callTurnRepository) {
        this.callRepository = callRepository;
        this.callTurnRepository = callTurnRepository;
    }

    @Transactional(readOnly = true)
    public List<Call> list(UUID tenantId) {
        return callRepository.findAllByTenantIdOrderByStartedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public Call get(UUID tenantId, UUID callId) {
        return callRepository.findByIdAndTenantId(callId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
    }

    @Transactional(readOnly = true)
    public String firstAgentResponse(UUID tenantId, UUID callId) {
        get(tenantId, callId);
        return callTurnRepository.findByCall_IdOrderByTurnIndexAsc(callId).stream()
                .findFirst()
                .map(CallTurn::getAgentResponse)
                .orElse("");
    }

    @Transactional(readOnly = true)
    public List<CallTurn> turns(UUID tenantId, UUID callId) {
        get(tenantId, callId);
        return callTurnRepository.findByCall_IdOrderByTurnIndexAsc(callId);
    }

    @Transactional(readOnly = true)
    public CallTurn lastTurn(UUID tenantId, UUID callId) {
        get(tenantId, callId);
        return callTurnRepository.findFirstByCall_IdOrderByTurnIndexDesc(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call has no agent response"));
    }

    @Transactional
    public CallTurn recordLatestTtsLatency(UUID tenantId, UUID callId, int latencyMs) {
        get(tenantId, callId);
        var turn = callTurnRepository.findFirstByCall_IdOrderByTurnIndexDesc(callId).orElse(null);
        if (turn == null) {
            return null;
        }
        turn.recordTtsLatency(latencyMs);
        return callTurnRepository.save(turn);
    }
}
