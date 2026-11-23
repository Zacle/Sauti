package com.sauti.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.calendar.BookingRepository;
import com.sauti.notification.WorkspaceNotificationDtos.NotificationListResponse;
import com.sauti.notification.WorkspaceNotificationDtos.NotificationResponse;
import com.sauti.tenant.Tenant;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class WorkspaceNotificationService {
    private final WorkspaceNotificationRepository repository;
    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    public WorkspaceNotificationService(
            WorkspaceNotificationRepository repository,
            BookingRepository bookingRepository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.bookingRepository = bookingRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WorkspaceNotification bookingCreated(UUID bookingId) {
        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
        var calendarSynced = "synced".equals(booking.getCalendarSyncStatus());
        var calendarFailed = "pending_owner_action".equals(booking.getCalendarSyncStatus());
        var payload = new LinkedHashMap<String, Object>();
        payload.put("bookingReference", booking.getBookingReference());
        payload.put("callerName", booking.getCallerName());
        payload.put("serviceType", booking.getServiceType());
        payload.put("appointmentAt", booking.getAppointmentAt().toString());
        payload.put("agentName", booking.getAgent().getName());
        payload.put("calendarSyncStatus", booking.getCalendarSyncStatus());
        if (calendarFailed) payload.put("calendarSyncError", booking.getCalendarSyncError());
        var notification = new WorkspaceNotification(
                booking.getTenant(),
                calendarFailed ? "booking.calendar_sync_failed" : "booking.confirmed",
                calendarFailed ? "Booking saved, calendar sync failed" : "New booking confirmed",
                calendarFailed
                        ? booking.getCallerName() + " was saved in Sauti, but the external calendar needs attention."
                        : booking.getCallerName() + " booked " + booking.getServiceType(),
                "/bookings",
                "booking",
                booking.getId(),
                json(payload)
        );
        return repository.save(notification);
    }

    /**
     * The workspace reached 80% or 100% of its call minutes, or of its outbound messages when
     * {@code messages} is true. Saved in the caller's transaction.
     */
    @Transactional
    public WorkspaceNotification usageThresholdReached(Tenant tenant, UUID alertId, boolean messages,
                                                      int thresholdPercent, int used, int included, boolean trial,
                                                      OffsetDateTime cycleEndsAt) {
        var unit = messages ? "messages" : "minutes";
        var allowance = trial ? "trial " + unit : messages ? "messages" : "call minutes";
        var paused = messages ? "AI and automatic messages are paused (staff can still reply from the inbox)"
                : "new AI calls are paused";
        var renews = cycleEndsAt == null ? null
                : cycleEndsAt.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("d MMM uuuu"));
        var exhausted = thresholdPercent >= 100;
        String title;
        String message;
        if (exhausted) {
            title = (trial ? "Trial " + unit : messages ? "Messages" : "Call minutes") + " used up";
            message = trial
                    ? "All " + included + " trial " + unit + " are used, so " + paused + ". Choose a plan to continue."
                    : "All " + included + " " + unit + " for this cycle are used, so " + paused + " until " + renews + ".";
        } else {
            title = thresholdPercent + "% of your " + allowance + " used";
            message = used + " of " + included + " " + unit + " used. "
                    + (messages ? "AI and automatic messages pause" : "New AI calls pause") + " when they run out"
                    + (renews == null ? "." : "; " + unit + " renew on " + renews + ".");
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("kind", unit);
        payload.put("thresholdPercent", thresholdPercent);
        payload.put("used", used);
        payload.put("included", included);
        payload.put("trial", trial);
        if (cycleEndsAt != null) payload.put("cycleEndsAt", cycleEndsAt.toString());
        var type = "billing." + unit + (exhausted ? "_used_up" : "_warning");
        return repository.save(new WorkspaceNotification(tenant, type, title, message, "/billing", "billing",
                alertId, json(payload)));
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(UUID tenantId) {
        return new NotificationListResponse(
                repository.findTop50ByTenant_IdOrderByCreatedAtDesc(tenantId).stream()
                        .map(NotificationResponse::from)
                        .toList(),
                repository.countByTenant_IdAndReadAtIsNull(tenantId)
        );
    }

    @Transactional
    public NotificationResponse markRead(UUID tenantId, UUID notificationId) {
        var notification = repository.findByIdAndTenant_Id(notificationId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(UUID tenantId) {
        repository.findAllByTenant_IdAndReadAtIsNull(tenantId).forEach(WorkspaceNotification::markRead);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }
}
