package com.sauti.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.dashboard.DashboardEventPublisher;
import com.sauti.outbound.OutboundCallService;
import com.sauti.tool.CalendarProviderFactory;
import com.sauti.webhook.WebhookDeliveryService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final AgentRepository agentRepository;
    private final CallRepository callRepository;
    private final DashboardEventPublisher dashboardEventPublisher;
    private final WebhookDeliveryService webhookDeliveryService;
    private final OutboundCallService outboundCallService;
    private final CalendarProviderFactory calendarProviderFactory;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(
            BookingRepository bookingRepository,
            AgentRepository agentRepository,
            CallRepository callRepository,
            DashboardEventPublisher dashboardEventPublisher,
            WebhookDeliveryService webhookDeliveryService,
            OutboundCallService outboundCallService,
            CalendarProviderFactory calendarProviderFactory,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bookingRepository = bookingRepository;
        this.agentRepository = agentRepository;
        this.callRepository = callRepository;
        this.dashboardEventPublisher = dashboardEventPublisher;
        this.webhookDeliveryService = webhookDeliveryService;
        this.outboundCallService = outboundCallService;
        this.calendarProviderFactory = calendarProviderFactory;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<Booking> list(UUID tenantId) {
        return bookingRepository.findAllByTenantIdOrderByAppointmentAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public Booking get(UUID tenantId, UUID bookingId) {
        return bookingRepository.findByIdAndTenantId(bookingId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public Booking getByReference(UUID tenantId, String bookingReference) {
        return bookingRepository.findByBookingReferenceIgnoreCaseAndTenantId(normalizeReference(bookingReference), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public Booking resolve(UUID tenantId, String identifier) {
        try {
            return get(tenantId, UUID.fromString(identifier));
        } catch (IllegalArgumentException ignored) {
            return getByReference(tenantId, identifier);
        }
    }

    @Transactional
    public Booking create(UUID tenantId, CreateBookingRequest request) {
        var agent = agentRepository.findByIdAndTenantId(request.agentId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        CalendarProvider provider = providerFor(agent);
        return create(tenantId, request, provider);
    }

    @Transactional
    public Booking create(UUID tenantId, CreateBookingRequest request, CalendarProvider provider) {
        var agent = agentRepository.findByIdAndTenantId(request.agentId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        if (!agent.isBookingEnabled()) {
            throw new IllegalArgumentException("Booking is not enabled for this agent");
        }
        Call call = null;
        if (request.callId() != null) {
            call = callRepository.findByIdAndTenantId(request.callId(), tenantId)
                    .orElseThrow(() -> new EntityNotFoundException("Call not found"));
        }
        var booking = bookingRepository.save(new Booking(
                agent.getTenant(),
                agent,
                call,
                request.callerName(),
                request.callerPhone(),
                request.callerEmail(),
                request.serviceType(),
                request.appointmentAt(), request.durationMinutes() == null ? 60 : request.durationMinutes(),
                json(request.capturedData())
        ));
        try {
            if (provider == null) throw new IllegalStateException("No calendar provider is connected");
            var externalEventId = provider.createEvent(booking).externalEventId();
            if (externalEventId == null || externalEventId.isBlank()) {
                throw new IllegalStateException("Calendar provider did not return an event identifier");
            }
            booking.markSynced(externalEventId);
        } catch (RuntimeException exception) {
            booking.markSyncFailed(exception.getMessage());
        }
        dashboardEventPublisher.bookingCreated(booking);
        webhookDeliveryService.bookingCreated(booking);
        outboundCallService.scheduleReminder(booking);
        eventPublisher.publishEvent(new BookingNotificationService.BookingCreatedEvent(booking.getId()));
        return booking;
    }

    @Transactional
    public Booking cancel(UUID tenantId, UUID bookingId) {
        var booking = get(tenantId, bookingId);
        try {
            var provider = providerFor(booking.getAgent());
            if (provider == null) throw new IllegalStateException("No connected calendar provider");
            provider.deleteEvent(booking);
        } catch (RuntimeException exception) {
            booking.markCalendarActionFailed(exception.getMessage());
        }
        booking.cancel();
        webhookDeliveryService.bookingCancelled(booking);
        return booking;
    }

    @Transactional
    public Booking reschedule(UUID tenantId, UUID bookingId, BookingDtos.RescheduleBookingRequest request) {
        var booking = get(tenantId, bookingId);
        booking.reschedule(request.appointmentAt(), request.durationMinutes() == null
                ? booking.getDurationMinutes() : request.durationMinutes());
        try {
            var provider = providerFor(booking.getAgent());
            if (provider == null) throw new IllegalStateException("No connected calendar provider");
            provider.updateEvent(booking);
            booking.markSynced(booking.getExternalEventId());
        } catch (RuntimeException exception) {
            booking.markCalendarActionFailed(exception.getMessage());
        }
        return booking;
    }

    @Transactional
    public Booking update(UUID tenantId, UUID bookingId, BookingDtos.UpdateBookingRequest request) {
        var booking = get(tenantId, bookingId);
        booking.updateDetails(
                request.callerName(), request.callerPhone(), request.callerEmail(), request.serviceType(),
                request.appointmentAt(), request.durationMinutes() == null ? booking.getDurationMinutes() : request.durationMinutes(),
                json(request.capturedData())
        );
        try {
            var provider = providerFor(booking.getAgent());
            if (provider == null) throw new IllegalStateException("No connected calendar provider");
            provider.updateEvent(booking);
            booking.markSynced(booking.getExternalEventId());
        } catch (RuntimeException exception) {
            booking.markCalendarActionFailed(exception.getMessage());
        }
        return booking;
    }

    @Transactional
    public void delete(UUID tenantId, UUID bookingId) {
        var booking = get(tenantId, bookingId);
        try {
            var provider = providerFor(booking.getAgent());
            if (provider != null) provider.deleteEvent(booking);
        } catch (RuntimeException ignored) {
            // The owner explicitly requested deletion; the local record must still be removed.
        }
        bookingRepository.delete(booking);
    }

    private String json(java.util.Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? java.util.Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Booking details must be valid structured data");
        }
    }

    private CalendarProvider providerFor(com.sauti.agent.Agent agent) {
        try {
            if ("Google Calendar".equalsIgnoreCase(agent.getCalendarProvider())) {
                return calendarProviderFactory.connectedForAgent(agent.getId()).orElse(null);
            }
            return calendarProviderFactory.forAgent(agent.getId());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String normalizeReference(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Booking number is required");
        var normalized = value.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.startsWith("SAT") ? "SAT-" + normalized.substring(3) : "SAT-" + normalized;
    }
}
