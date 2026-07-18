package com.sauti.calendar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.outbound.OutboundCallService;
import com.sauti.tool.CalendarProviderFactory;
import com.sauti.webhook.WebhookDeliveryService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BookingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookingService.class);
    private final BookingRepository bookingRepository;
    private final AgentRepository agentRepository;
    private final CallRepository callRepository;
    private final WebhookDeliveryService webhookDeliveryService;
    private final OutboundCallService outboundCallService;
    private final CalendarProviderFactory calendarProviderFactory;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate requiresNewTransaction;

    public BookingService(
            BookingRepository bookingRepository,
            AgentRepository agentRepository,
            CallRepository callRepository,
            WebhookDeliveryService webhookDeliveryService,
            OutboundCallService outboundCallService,
            CalendarProviderFactory calendarProviderFactory,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager
    ) {
        this.bookingRepository = bookingRepository;
        this.agentRepository = agentRepository;
        this.callRepository = callRepository;
        this.webhookDeliveryService = webhookDeliveryService;
        this.outboundCallService = outboundCallService;
        this.calendarProviderFactory = calendarProviderFactory;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

    public Booking create(UUID tenantId, CreateBookingRequest request) {
        var booking = persistLocalBooking(tenantId, request);
        return synchronizeCreatedBooking(booking, providerFor(booking.getAgent()));
    }

    public Booking create(UUID tenantId, CreateBookingRequest request, CalendarProvider provider) {
        var booking = persistLocalBooking(tenantId, request);
        return synchronizeCreatedBooking(booking, provider);
    }

    private Booking persistLocalBooking(UUID tenantId, CreateBookingRequest request) {
        return Objects.requireNonNull(requiresNewTransaction.execute(status -> {
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
        return bookingRepository.saveAndFlush(new Booking(
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
        }));
    }

    private Booking synchronizeCreatedBooking(Booking localBooking, CalendarProvider provider) {
        var integrationConfigured = hasExternalCalendar(localBooking.getAgent());
        String externalEventId = null;
        String syncError = null;
        try {
            if (integrationConfigured && provider == null) {
                throw new IllegalStateException("The selected calendar integration is not connected");
            }
            if (integrationConfigured) externalEventId = provider.createEvent(localBooking).externalEventId();
            if (!integrationConfigured) externalEventId = null;
            if (integrationConfigured && (externalEventId == null || externalEventId.isBlank())) {
                throw new IllegalStateException("Calendar provider did not return an event identifier");
            }
        } catch (RuntimeException exception) {
            syncError = safeSyncError(exception);
        }

        var finalExternalEventId = externalEventId;
        var finalSyncError = syncError;
        var booking = Objects.requireNonNull(requiresNewTransaction.execute(status -> {
            var persisted = bookingRepository.findById(localBooking.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Booking not found after local save"));
            if (!integrationConfigured) persisted.markLocalOnly();
            else if (finalSyncError == null) persisted.markSynced(finalExternalEventId);
            else persisted.markSyncFailed(finalSyncError);
            eventPublisher.publishEvent(new BookingNotificationService.BookingCreatedEvent(persisted.getId()));
            return bookingRepository.saveAndFlush(persisted);
        }));
        runPostSaveActions(booking);
        return booking;
    }

    private void runPostSaveActions(Booking booking) {
        try {
            webhookDeliveryService.bookingCreated(booking);
        } catch (RuntimeException exception) {
            LOGGER.warn("Booking webhook delivery failed bookingId={}: {}", booking.getId(), exception.getClass().getSimpleName());
        }
        try {
            outboundCallService.scheduleReminder(booking);
        } catch (RuntimeException exception) {
            LOGGER.warn("Booking reminder scheduling failed bookingId={}: {}", booking.getId(), exception.getClass().getSimpleName());
        }
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
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean hasExternalCalendar(com.sauti.agent.Agent agent) {
        var configured = agent.getCalendarProvider();
        if (configured == null || configured.isBlank()) return false;
        return !java.util.Set.of("set up later", "provider default", "local", "sauti")
                .contains(configured.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private String safeSyncError(RuntimeException exception) {
        var message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.contains("not connected") || message.contains("credential") || message.contains("authoriz")) {
            return "Calendar connection is missing or no longer authorized";
        }
        if (message.contains("event identifier")) {
            return "The calendar did not confirm the event creation";
        }
        return "External calendar synchronization failed";
    }

    private String normalizeReference(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Booking number is required");
        var normalized = value.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return normalized.startsWith("SAT") ? "SAT-" + normalized.substring(3) : "SAT-" + normalized;
    }
}
