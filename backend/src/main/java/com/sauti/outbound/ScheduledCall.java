package com.sauti.outbound;

import com.sauti.agent.Agent;
import com.sauti.calendar.Booking;
import com.sauti.shared.Auditable;
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
@Table(name = "scheduled_calls")
public class ScheduledCall extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false)
    private String callType;

    @Column(nullable = false)
    private String targetPhone;

    @Column(nullable = false)
    private OffsetDateTime scheduledFor;

    @Column(nullable = false)
    private String status = "pending";

    private String twilioCallSid;
    private String failureReason;

    protected ScheduledCall() {
    }

    public ScheduledCall(Tenant tenant, Agent agent, Booking booking, String callType, String targetPhone, OffsetDateTime scheduledFor) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.agent = agent;
        this.booking = booking;
        this.callType = callType;
        this.targetPhone = targetPhone;
        this.scheduledFor = scheduledFor;
    }

    public void markInitiated(String twilioCallSid) {
        this.status = "initiated";
        this.twilioCallSid = twilioCallSid;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = "failed";
        this.failureReason = reason;
    }

    public UUID getId() {
        return id;
    }

    public Agent getAgent() {
        return agent;
    }

    public String getTargetPhone() {
        return targetPhone;
    }

    public OffsetDateTime getScheduledFor() {
        return scheduledFor;
    }
}
