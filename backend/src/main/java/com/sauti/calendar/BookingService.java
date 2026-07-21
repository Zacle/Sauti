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
import java.time.LocalDate;
import java.time.ZoneId;
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

    @Transactional(readOnly = true)
    public List<CalendarAvailabilitySlot> excludeLocalConflicts(
            UUID tenantId,
            UUID agentId,
            LocalDate date,
            ZoneId timezone,
            List<CalendarAvailabilitySlot> providerSlots
    ) {
        if (providerSlots == null || providerSlots.isEmpty()) return List.of();
        var dayStart = date.atStartOfDay(timezone).toOffsetDateTime();
        var bookings = bookingsInWindow(tenantId, agentId, dayStart.minusDays(1), dayStart.plusDays(1));
        return providerSlots.stream()
                .filter(slot -> bookings.stream().noneMatch(booking -> overlaps(
                        slot.start(), slot.end(), booking.getAppointmentAt(), bookingEnd(booking)
                )))
                .toList();
    }

    public Booking create(UUID tenantId, CreateBookingRequest request) {
        var existing = matchingBookingForCall(tenantId, request);
        if (existing.isPresent()) return existing.get();
        var booking = persistLocalBooking(tenantId, request);
        return synchronizeCreatedBooking(booking, providerFor(booking.getAgent()));
    }

    public Booking create(UUID tenantId, CreateBookingRequest request, CalendarProvider provider) {
        var existing = matchingBookingForCall(tenantId, request);
        if (existing.isPresent()) return existing.get();
        var booking = persistLocalBooking(tenantId, request);
        return synchronizeCreatedBooking(booking, provider);
    }

    private java.util.Optional<Booking> matchingBookingForCall(UUID tenantId, CreateBookingRequest request) {
        if (request.callId() == null || request.agentId() == null || request.appointmentAt() == null) {
            return java.util.Optional.empty();
        }
        return bookingRepository
                .findFirstByTenantIdAndCall_IdAndAgent_IdAndStatusNotAndAppointmentAt(
                        tenantId,
                        request.callId(),
                        request.agentId(),
                        "cancelled",
                        request.appointmentAt()
                )
                .filter(existing -> sameBookingRequest(existing, request));
    }

    private boolean sameBookingRequest(Booking existing, CreateBookingRequest request) {
        var requestedDuration = request.durationMinutes() == null ? 60 : request.durationMinutes();
        return Objects.equals(normalized(existing.getCallerName()), normalized(request.callerName()))
                && Objects.equals(normalized(existing.getCallerPhone()), normalized(request.callerPhone()))
                && Objects.equals(normalized(existing.getCallerEmail()), normalized(request.callerEmail()))
                && Objects.equals(normalized(existing.getServiceType()), normalized(request.serviceType()))
                && existing.getDurationMinutes() == requestedDuration;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private Booking persistLocalBooking(UUID tenantId, CreateBookingRequest request) {
        return Objects.requireNonNull(requiresNewTransaction.execute(status -> {
        var agent = agentRepository.findByIdAndTenantId(request.agentId(), tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        if (!agent.isBookingEnabled()) {
            throw new IllegalArgumentException("Booking is not enabled for this agent");
        }
        var requestedDuration = request.durationMinutes() == null ? 60 : request.durationMinutes();
        var requestedEnd = request.appointmentAt().plusMinutes(requestedDuration);
        var conflicts = bookingsInWindow(
                tenantId,
                agent.getId(),
                request.appointmentAt().minusDays(1),
                requestedEnd
        );
        if (conflicts.stream().anyMatch(existing -> overlaps(
                request.appointmentAt(), requestedEnd, existing.getAppointmentAt(), bookingEnd(existing)
        ))) {
            throw new IllegalArgumentException("The requested appointment time is no longer available");
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
                request.appointmentAt(), requestedDuration,
                json(request.capturedData())
        ));
        }));
    }

    private List<Booking> bookingsInWindow(
            UUID tenantId,
            UUID agentId,
            java.time.OffsetDateTime start,
            java.time.OffsetDateTime end
    ) {
        var bookings = bookingRepository
                .findAllByTenantIdAndAgent_IdAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
                        tenantId, agentId, "cancelled", start, end
                );
        return bookings == null ? List.of() : bookings;
    }

    private java.time.OffsetDateTime bookingEnd(Booking booking) {
        return booking.getAppointmentAt().plusMinutes(Math.max(1, booking.getDurationMinutes()));
    }

    private boolean overlaps(
            java.time.OffsetDateTime firstStart,
            java.time.OffsetDateTime firstEnd,
            java.time.OffsetDateTime secondStart,
            java.time.OffsetDateTime secondEnd
    ) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
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
