package com.sauti.tool;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One attempt to run a side-effecting tool during a call, and what came of it. Keeps a short
 * summary for the owner, never the tool's arguments or result.
 */
@Entity
@Table(name = "call_tool_actions")
public class CallToolAction extends Auditable {
    /** Retained and read back to the caller; nothing has changed yet. */
    public static final String AWAITING_CONFIRMATION = "awaiting_confirmation";
    /** Running now, or the server stopped while it ran (outcome unknown). */
    public static final String STARTED = "started";
    public static final String PERFORMED = "performed";
    /** Ran, but the business rule declined it (slot taken, booking not found, …). */
    public static final String NOT_PERFORMED = "not_performed";
    public static final String FAILED = "failed";

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "call_id", nullable = false)
    private UUID callId;
    @Column(name = "invocation_id", nullable = false)
    private String invocationId;
    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;
    @Column(nullable = false, length = 32)
    private String effect;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(length = 300)
    private String summary;

    protected CallToolAction() {
    }

    CallToolAction(UUID tenantId, UUID callId, String invocationId, String toolName, String effect) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.callId = callId;
        this.invocationId = invocationId;
        this.toolName = toolName;
        this.effect = effect;
        this.status = STARTED;
    }

    void record(String status, String summary) {
        this.status = status;
        this.summary = summary == null ? null : summary.substring(0, Math.min(300, summary.length()));
    }

    public UUID getId() { return id; }
    public UUID getCallId() { return callId; }
    public String getInvocationId() { return invocationId; }
    public String getToolName() { return toolName; }
    public String getEffect() { return effect; }
    public String getStatus() { return status; }
    public String getSummary() { return summary; }
}
