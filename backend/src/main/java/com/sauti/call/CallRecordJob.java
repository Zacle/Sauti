package com.sauti.call;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The end-of-call work that completes a call's record: first the transcript of a phone call, which
 * the voice provider holds, then the summary, intent and sentiment. Each stage is retried on its
 * own schedule and then given up, so a call always reaches {@code done}.
 */
@Entity
@Table(name = "call_record_jobs")
public class CallRecordJob {
    public static final String TRANSCRIPT = "transcript";
    public static final String ANALYSIS = "analysis";
    public static final String DONE = "done";

    @Id
    private UUID callId;
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String stage;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private OffsetDateTime nextAttemptAt;
    /** imported, unavailable, or null when the transcript did not come from the provider. */
    private String transcriptStatus;
    /** completed, failed, skipped, or null until analysis has run. */
    private String analysisStatus;
    private String lastError;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected CallRecordJob() {
    }

    CallRecordJob(UUID callId, UUID tenantId, String stage, OffsetDateTime firstAttemptAt) {
        this.callId = callId;
        this.tenantId = tenantId;
        this.stage = stage;
        this.attempts = 0;
        this.nextAttemptAt = firstAttemptAt;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    /** Holds the job for {@code until} while one worker runs it, so another does not pick it up. */
    void lease(OffsetDateTime until) {
        nextAttemptAt = until;
        updatedAt = OffsetDateTime.now();
    }

    void retry(String error, OffsetDateTime at) {
        attempts++;
        lastError = shorten(error);
        nextAttemptAt = at;
        updatedAt = OffsetDateTime.now();
    }

    void transcriptFinished(String status, String nextStage, OffsetDateTime at) {
        transcriptStatus = status;
        advance(nextStage, at);
    }

    void analysisFinished(String status) {
        analysisStatus = status;
        advance(DONE, nextAttemptAt);
    }

    private void advance(String nextStage, OffsetDateTime at) {
        stage = nextStage;
        attempts = 0;
        nextAttemptAt = at;
        updatedAt = OffsetDateTime.now();
    }

    private static String shorten(String error) {
        if (error == null) return null;
        return error.length() <= 200 ? error : error.substring(0, 200);
    }

    public UUID getCallId() { return callId; }
    public UUID getTenantId() { return tenantId; }
    public String getStage() { return stage; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getTranscriptStatus() { return transcriptStatus; }
    public String getAnalysisStatus() { return analysisStatus; }
    public String getLastError() { return lastError; }
    public boolean isDone() { return DONE.equals(stage); }
}
