package com.sauti.call;

import com.sauti.agent.Agent;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "call_turns")
public class CallTurn {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "call_id")
    private Call call;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false)
    private int turnIndex;

    @Column(nullable = false)
    private String callerTranscript;

    @Column(nullable = false)
    private String agentResponse;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private int sttLatencyMs;

    @Column(nullable = false)
    private int llmLatencyMs;

    @Column(nullable = false)
    private int ttsLatencyMs;

    @Column(nullable = false)
    private boolean interrupted;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected CallTurn() {
    }

    @Deprecated
    public CallTurn(Call call, int turnIndex, String callerTranscript, String agentResponse, String language, int sttLatencyMs, int llmLatencyMs, int ttsLatencyMs) {
        this(call, turnIndex, callerTranscript, agentResponse, language, sttLatencyMs, llmLatencyMs, ttsLatencyMs, false);
    }

    public CallTurn(Call call, int turnIndex, String callerTranscript, String agentResponse, String language, int sttLatencyMs, int llmLatencyMs, int ttsLatencyMs, boolean interrupted) {
        this.id = UUID.randomUUID();
        this.call = call;
        this.tenant = call.getTenant();
        this.agent = call.getAgent();
        this.turnIndex = turnIndex;
        this.callerTranscript = callerTranscript;
        this.agentResponse = agentResponse;
        this.language = language;
        this.sttLatencyMs = sttLatencyMs;
        this.llmLatencyMs = llmLatencyMs;
        this.ttsLatencyMs = ttsLatencyMs;
        this.interrupted = interrupted;
        this.createdAt = OffsetDateTime.now();
    }

    public int getTurnIndex() {
        return turnIndex;
    }

    public String getCallerTranscript() {
        return callerTranscript;
    }

    public String getAgentResponse() {
        return agentResponse;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public int getSttLatencyMs() {
        return sttLatencyMs;
    }

    public int getLlmLatencyMs() {
        return llmLatencyMs;
    }

    public int getTtsLatencyMs() {
        return ttsLatencyMs;
    }

    public void recordTtsLatency(int latencyMs) {
        this.ttsLatencyMs = Math.max(0, latencyMs);
    }
}
