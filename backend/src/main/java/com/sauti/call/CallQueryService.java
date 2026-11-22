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
    private final CallRecordJobRepository recordJobs;
    private final jakarta.persistence.EntityManager entityManager;

    public CallQueryService(CallRepository callRepository, CallTurnRepository callTurnRepository,
                            CallRecordJobRepository recordJobs, jakarta.persistence.EntityManager entityManager) {
        this.callRepository = callRepository;
        this.callTurnRepository = callTurnRepository;
        this.recordJobs = recordJobs;
        this.entityManager = entityManager;
    }

    public static final int MAX_PAGE_SIZE = 100;

    /** One page of a search, newest first, with totals over every match (not just this page). */
    public record CallPage(List<Call> items, long total, long answered, long averageDurationSeconds) {
    }

    @Transactional(readOnly = true)
    public CallPage search(UUID tenantId, CallSearch search, int page, int size) {
        var matching = search.forTenant(tenantId);
        var request = org.springframework.data.domain.PageRequest.of(Math.max(0, page),
                Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "startedAt"));
        var found = callRepository.findAll(matching, request);
        org.springframework.data.jpa.domain.Specification<Call> answered =
                (root, query, cb) -> root.get("outcome").in(CallSearch.ANSWERED);
        var answeredCount = callRepository.count(matching.and(answered));
        return new CallPage(found.getContent(), found.getTotalElements(), answeredCount,
                averageDuration(matching));
    }

    private long averageDuration(org.springframework.data.jpa.domain.Specification<Call> matching) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Double.class);
        var root = query.from(Call.class);
        var where = matching.toPredicate(root, query, cb);
        query.select(cb.avg(root.get("durationSeconds")))
                .where(cb.and(where, cb.isNotNull(root.get("durationSeconds"))));
        var average = entityManager.createQuery(query).getSingleResult();
        return average == null ? 0 : Math.round(average);
    }

    /** Calls whose record is still being completed, with the stage it is at (transcript or analysis). */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> unfinishedRecords(UUID tenantId, java.util.Collection<UUID> callIds) {
        var stages = new java.util.HashMap<UUID, String>();
        if (callIds.isEmpty()) return stages;
        recordJobs.findAllById(callIds).stream()
                .filter(job -> !job.isDone() && job.getTenantId().equals(tenantId))
                .forEach(job -> stages.put(job.getCallId(), job.getStage()));
        return stages;
    }

    @Transactional(readOnly = true)
    public java.util.Map<UUID, String> unfinishedRecords(UUID tenantId) {
        var stages = new java.util.HashMap<UUID, String>();
        recordJobs.findAllByTenantIdAndStageIn(tenantId, List.of(CallRecordJob.TRANSCRIPT, CallRecordJob.ANALYSIS))
                .forEach(job -> stages.put(job.getCallId(), job.getStage()));
        return stages;
    }

    @Transactional(readOnly = true)
    public String unfinishedRecord(UUID tenantId, UUID callId) {
        get(tenantId, callId);
        return recordJobs.findById(callId).filter(job -> !job.isDone()).map(CallRecordJob::getStage).orElse(null);
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
    public Call findActiveByProviderCallId(String providerCallId) {
        return callRepository.findByTwilioCallSid(providerCallId)
                .filter(Call::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Active call not found"));
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
