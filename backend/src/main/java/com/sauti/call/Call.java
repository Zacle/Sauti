package com.sauti.call;

import com.sauti.agent.Agent;
import com.sauti.shared.Auditable;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "calls")
public class Call extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false, unique = true)
    private String twilioCallSid;

    private String callerNumber;

    @Column(nullable = false)
    private String direction;

    private String languageDetected;

    @Column(nullable = false)
    private OffsetDateTime startedAt;

    private OffsetDateTime endedAt;
    private Integer durationSeconds;
    private String outcome;
    private String transcript;
    private String recordingUrl;
    private String recordingSid;
    private String sentiment;
    private String callSummary;
    private Boolean callSuccessful;
    private String intent;
    private String failureReason;
    private String conversationJson;
    private String transferStatus;
    private String transferTargetNumber;
    private String transferChildCallSid;
    private String transferFailureReason;
    private OffsetDateTime transferRequestedAt;
    private OffsetDateTime transferCompletedAt;
    @Column(nullable = false)
    private boolean afterHours;

    protected Call() {
    }

    public Call(Tenant tenant, Agent agent, String twilioCallSid, String callerNumber, String direction) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.agent = agent;
        this.twilioCallSid = twilioCallSid;
        this.callerNumber = callerNumber;
        this.direction = direction;
        this.startedAt = OffsetDateTime.now();
        this.outcome = "active";
        this.transcript = "";
    }

    public void appendTurn(String language, String callerTranscript, String agentResponse) {
        this.languageDetected = language;
        this.transcript = (transcript == null ? "" : transcript)
                + "Caller: " + callerTranscript + "\n"
                + "Agent: " + agentResponse + "\n";
    }

    public void appendAgentMessage(String language, String agentResponse) {
        this.languageDetected = language;
        this.transcript = (transcript == null ? "" : transcript)
                + "Agent: " + agentResponse + "\n";
    }

    public void attachRecording(String recordingUrl, String recordingSid) {
        this.recordingUrl = recordingUrl;
        this.recordingSid = recordingSid;
    }

    public void markAfterHours() {
        this.afterHours = true;
    }

    public void selectLanguage(String language) {
        this.languageDetected = language;
    }

    public void requestTransfer(String targetNumber) {
        this.transferStatus = "requested";
        this.transferTargetNumber = targetNumber;
        this.transferChildCallSid = null;
        this.transferFailureReason = null;
        this.transferRequestedAt = OffsetDateTime.now();
        this.transferCompletedAt = null;
    }

    public void markTransferDialing() {
        this.transferStatus = "dialing";
    }

    public void markTransferResult(String status, String childCallSid, String failureReason) {
        this.transferStatus = status;
        this.transferChildCallSid = childCallSid;
        this.transferFailureReason = failureReason;
        this.transferCompletedAt = OffsetDateTime.now();
    }

    public void complete(String outcome) {
        if (endedAt != null) {
            return;
        }
        this.outcome = outcome;
        this.endedAt = OffsetDateTime.now();
        this.durationSeconds = (int) Duration.between(startedAt, endedAt).toSeconds();
        this.tenant.addMinutesUsed(Math.max(1, (int) Math.ceil(durationSeconds / 60.0)));
    }

    public void applyTwilioStatus(String callStatus, Integer authoritativeDurationSeconds, String recordingUrl, String recordingSid) {
        int previousBilledMinutes = billedMinutes(outcome, durationSeconds, endedAt != null);
        if (isTerminalTwilioStatus(callStatus)) {
            var normalizedStatus = callStatus.replace("-", "_");
            if (!"completed".equals(normalizedStatus) || isActive()) {
                this.outcome = normalizedStatus;
            }
            if (endedAt == null) {
                this.endedAt = OffsetDateTime.now();
            }
        }
        if (authoritativeDurationSeconds != null && authoritativeDurationSeconds >= 0) {
            this.durationSeconds = authoritativeDurationSeconds;
        }
        if (recordingUrl != null && !recordingUrl.isBlank()) {
            this.recordingUrl = recordingUrl;
        }
        if (recordingSid != null && !recordingSid.isBlank()) {
            this.recordingSid = recordingSid;
        }
        int newBilledMinutes = billedMinutes(outcome, durationSeconds, endedAt != null);
        this.tenant.adjustMinutesUsed(newBilledMinutes - previousBilledMinutes);
    }

    public void fail(String outcome, String reason) {
        this.failureReason = reason;
        complete(outcome);
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Agent getAgent() {
        return agent;
    }

    public String getTwilioCallSid() {
        return twilioCallSid;
    }

    public String getCallerNumber() {
        return callerNumber;
    }

    public String getDirection() {
        return direction;
    }

    public String getLanguageDetected() {
        return languageDetected;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getTranscript() {
        return transcript;
    }

    public String getRecordingUrl() {
        return recordingUrl;
    }

    public String getRecordingSid() {
        return recordingSid;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getSentiment() { return sentiment; }
    public String getCallSummary() { return callSummary; }
    public Boolean getCallSuccessful() { return callSuccessful; }
    public String getIntent() { return intent; }
    public String getTransferStatus() { return transferStatus; }
    public String getTransferTargetNumber() { return transferTargetNumber; }
    public String getTransferChildCallSid() { return transferChildCallSid; }
    public String getTransferFailureReason() { return transferFailureReason; }
    public OffsetDateTime getTransferRequestedAt() { return transferRequestedAt; }
    public OffsetDateTime getTransferCompletedAt() { return transferCompletedAt; }
    public boolean isAfterHours() { return afterHours; }

    public void applyAnalysis(String callSummary, Boolean callSuccessful, String sentiment, String intent) {
        this.callSummary = callSummary;
        this.callSuccessful = callSuccessful;
        this.sentiment = sentiment;
        this.intent = intent;
    }

    public String getConversationJson() {
        return conversationJson;
    }

    public void archiveConversation(String conversationJson) {
        this.conversationJson = conversationJson;
    }

    public boolean isActive() {
        return "active".equals(outcome);
    }

    private int billedMinutes(String outcome, Integer seconds, boolean hasEnded) {
        if (!hasEnded || seconds == null) {
            return 0;
        }
        if ("failed".equals(outcome) || "busy".equals(outcome) || "no_answer".equals(outcome) || "canceled".equals(outcome)) {
            return 0;
        }
        if (seconds == null || seconds <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(seconds / 60.0));
    }

    private boolean isTerminalTwilioStatus(String callStatus) {
        if (callStatus == null || callStatus.isBlank()) {
            return false;
        }
        return switch (callStatus) {
            case "completed", "failed", "busy", "no-answer", "canceled" -> true;
            default -> false;
        };
    }
}
