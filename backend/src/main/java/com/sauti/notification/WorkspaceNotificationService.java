package com.sauti.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.calendar.BookingRepository;
import com.sauti.notification.WorkspaceNotificationDtos.NotificationListResponse;
import com.sauti.notification.WorkspaceNotificationDtos.NotificationResponse;
import jakarta.persistence.EntityNotFoundException;
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
