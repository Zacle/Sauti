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

    private String callerEmail;

    @Column(nullable = false)
    private String serviceType;

    @Column(nullable = false)
    private OffsetDateTime bookedAt;

    @Column(nullable = false)
    private OffsetDateTime appointmentAt;

    @Column(nullable = false)
    private int durationMinutes = 60;

    @Column(nullable = false, unique = true, length = 24)
    private String bookingReference;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String capturedData = "{}";

    private String externalEventId;

    @Column(nullable = false)
    private String calendarSyncStatus = "pending";

    @Column(length = 1000)
    private String calendarSyncError;

    @Column(nullable = false)
    private String status = "confirmed";

    @Column(nullable = false)
    private boolean confirmationSent = false;

    protected Booking() {
    }

    public Booking(Tenant tenant, Agent agent, Call call, String callerName, String callerPhone, String callerEmail,
                   String serviceType, OffsetDateTime appointmentAt, int durationMinutes, String capturedData) {
        this.id = UUID.randomUUID();
        this.bookingReference = reference(this.id);
        this.tenant = tenant;
        this.agent = agent;
        this.call = call;
        this.callerName = callerName;
        this.callerPhone = callerPhone;
        this.callerEmail = callerEmail == null || callerEmail.isBlank() ? null : callerEmail.trim();
        this.serviceType = serviceType;
        this.bookedAt = OffsetDateTime.now();
        this.appointmentAt = appointmentAt;
        this.durationMinutes = durationMinutes <= 0 ? 60 : durationMinutes;
        this.capturedData = capturedData == null || capturedData.isBlank() ? "{}" : capturedData;
    }

    public void cancel() {
        this.status = "cancelled";
    }

    public void reschedule(OffsetDateTime appointmentAt, int durationMinutes) {
        this.appointmentAt = appointmentAt;
        this.durationMinutes = durationMinutes <= 0 ? this.durationMinutes : durationMinutes;
        this.status = "confirmed";
    }

    public void markSynced(String externalEventId) {
        this.externalEventId = externalEventId;
        this.calendarSyncStatus = "synced";
        this.calendarSyncError = null;
    }

    public void markSyncFailed(String error) {
        this.calendarSyncStatus = "pending_owner_action";
        this.calendarSyncError = error == null || error.isBlank() ? "Calendar synchronization failed" : error;
        this.status = "pending_confirmation";
    }

    public void markCalendarActionFailed(String error) {
        this.calendarSyncStatus = "pending_owner_action";
        this.calendarSyncError = error == null || error.isBlank() ? "Calendar synchronization failed" : error;
        if (!"cancelled".equals(status)) this.status = "pending_confirmation";
    }

    public void updateDetails(String callerName, String callerPhone, String callerEmail, String serviceType,
                              OffsetDateTime appointmentAt, int durationMinutes, String capturedData) {
        this.callerName = callerName;
        this.callerPhone = callerPhone;
        this.callerEmail = callerEmail == null || callerEmail.isBlank() ? null : callerEmail.trim();
        this.serviceType = serviceType;
        this.appointmentAt = appointmentAt;
        this.durationMinutes = durationMinutes <= 0 ? this.durationMinutes : durationMinutes;
        this.capturedData = capturedData == null || capturedData.isBlank() ? this.capturedData : capturedData;
        if (!"cancelled".equals(status)) this.status = "confirmed";
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

    public String getCallerEmail() { return callerEmail; }

    public String getServiceType() {
        return serviceType;
    }

    public OffsetDateTime getBookedAt() {
        return bookedAt;
    }

    public OffsetDateTime getAppointmentAt() {
        return appointmentAt;
    }

    public int getDurationMinutes() { return durationMinutes; }

    public String getBookingReference() { return bookingReference; }
    public String getCapturedData() { return capturedData; }
    public String getCalendarSyncStatus() { return calendarSyncStatus; }
    public String getCalendarSyncError() { return calendarSyncError; }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isConfirmationSent() {
        return confirmationSent;
    }

    private static String reference(UUID bookingId) {
        var value = Long.toUnsignedString(
                bookingId.getMostSignificantBits() ^ bookingId.getLeastSignificantBits(), 36
        ).toUpperCase(java.util.Locale.ROOT);
        var normalized = value.length() >= 12 ? value.substring(0, 12) : "000000000000".substring(value.length()) + value;
        return "SAT-" + normalized;
    }
}
