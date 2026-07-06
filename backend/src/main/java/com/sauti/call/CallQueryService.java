package com.sauti.call;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallQueryService {
    private final CallRepository callRepository;

    public CallQueryService(CallRepository callRepository) {
        this.callRepository = callRepository;
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
}
