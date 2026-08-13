package com.sauti.call;

import com.sauti.tenant.TenantRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallPrivacyRetentionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallPrivacyRetentionService.class);
    private final TenantRepository tenants;
    private final CallRepository calls;
    private final CallTurnRepository turns;
    private final CallRecordingService recordings;

    public CallPrivacyRetentionService(TenantRepository tenants, CallRepository calls,
                                       CallTurnRepository turns, CallRecordingService recordings) {
        this.tenants = tenants;
        this.calls = calls;
        this.turns = turns;
        this.recordings = recordings;
    }

    @Scheduled(cron = "${sauti.privacy.retention-cron:0 17 2 * * *}", zone = "UTC")
    public void purgeExpired() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        for (var tenant : tenants.findAll()) {
            var recordingCutoff = now.minusDays(tenant.getRecordingRetentionDays());
            var expiredRecordings = calls
                    .findTop250ByTenantIdAndEndedAtBeforeAndRecordingPurgedAtIsNullAndRecordingUrlIsNotNullOrderByEndedAtAsc(
                            tenant.getId(), recordingCutoff
                    );
            for (var call : expiredRecordings) {
                try {
                    recordings.purgeForRetention(tenant.getId(), call.getId());
                } catch (RuntimeException exception) {
                    LOGGER.warn("Recording retention deletion failed for callId={}: {}",
                            call.getId(), exception.getMessage());
                }
            }
            redactExpired(tenant.getId(), now.minusDays(tenant.getConversationRetentionDays()));
        }
    }

    @Transactional
    public int redactExpired(java.util.UUID tenantId, OffsetDateTime cutoff) {
        var expired = calls.findTop250ByTenantIdAndEndedAtBeforeAndPrivacyRedactedAtIsNullOrderByEndedAtAsc(
                tenantId, cutoff
        );
        var redactedAt = OffsetDateTime.now(ZoneOffset.UTC);
        for (var call : expired) {
            var callTurns = turns.findByCall_IdAndTenant_Id(call.getId(), tenantId);
            callTurns.forEach(CallTurn::redactContent);
            turns.saveAll(callTurns);
            call.redactConversation(redactedAt);
        }
        calls.saveAll(expired);
        return expired.size();
    }
}
