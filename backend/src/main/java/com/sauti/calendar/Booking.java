package com.sauti.calendar;

import com.sauti.agent.Agent;
import com.sauti.call.Call;
import com.sauti.shared.Auditable;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @ManyToOne(optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @ManyToOne
    @JoinColumn(name = "call_id")
    private Call call;

    @Column(nullable = false)
    private String callerName;

    @Column(nullable = false)
    private String callerPhone;

    @Column(nullable = false)
    private String serviceType;

    @Column(nullable = false)
    private OffsetDateTime bookedAt;

    @Column(nullable = false)
    private OffsetDateTime appointmentAt;

    private String externalEventId;

    @Column(nullable = false)
    private String status = "confirmed";

    @Column(nullable = false)
    private boolean confirmationSent = false;

    protected Booking() {
    }

    public Booking(Tenant tenant, Agent agent, Call call, String callerName, String callerPhone, String serviceType, OffsetDateTime appointmentAt) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        this.agent = agent;
        this.call = call;
        this.callerName = callerName;
        this.callerPhone = callerPhone;
        this.serviceType = serviceType;
        this.bookedAt = OffsetDateTime.now();
        this.appointmentAt = appointmentAt;
    }

    public void cancel() {
        this.status = "cancelled";
    }

    public void markSynced(String externalEventId) {
        this.externalEventId = externalEventId;
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

    public Call getCall() {
        return call;
    }

    public String getCallerName() {
        return callerName;
    }

    public String getCallerPhone() {
        return callerPhone;
    }

    public String getServiceType() {
        return serviceType;
    }

    public OffsetDateTime getBookedAt() {
        return bookedAt;
    }

    public OffsetDateTime getAppointmentAt() {
        return appointmentAt;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isConfirmationSent() {
        return confirmationSent;
    }
}
