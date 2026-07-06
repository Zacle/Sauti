package com.sauti.call;

import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallAnalysisPersistenceService {
    private final CallRepository callRepository;

    public CallAnalysisPersistenceService(CallRepository callRepository) {
        this.callRepository = callRepository;
    }

    @Transactional
    public void apply(UUID callId, String summary, Boolean successful, String sentiment, String intent) {
        var call = callRepository.findById(callId)
                .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        call.applyAnalysis(summary, successful, sentiment, intent);
        callRepository.save(call);
    }
}
