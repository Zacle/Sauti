package com.sauti.calendar;

import com.sauti.agent.AgentRepository;
import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.dashboard.DashboardEventPublisher;
import com.sauti.outbound.OutboundCallService;
import com.sauti.webhook.WebhookDeliveryService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final AgentRepository agentRepository;
    private final CallRepository callRepository;
    private final CalendarProvider calendarProvider;
    private final DashboardEventPublisher dashboardEventPublisher;
    private final WebhookDeliveryService webhookDeliveryService;
    private final OutboundCallService outboundCallService;

    public BookingService(
            BookingRepository bookingRepository,
            AgentRepository agentRepository,
            CallRepository callRepository,
            CalendarProvider calendarProvider,
            DashboardEventPublisher dashboardEventPublisher,
            WebhookDeliveryService webhookDeliveryService,
            OutboundCallService outboundCallService
    ) {
        this.bookingRepository = bookingRepository;
        this.agentRepository = agentRepository;
        this.callRepository = callRepository;
        this.calendarProvider = calendarProvider;
        this.dashboardEventPublisher = dashboardEventPublisher;
        this.webhookDeliveryService = webhookDeliveryService;
        this.outboundCallService = outboundCallService;
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

    @Transactional
    public Booking create(UUID tenantId, CreateBookingRequest request) {
        return create(tenantId, request, calendarProvider);
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
                request.serviceType(),
                request.appointmentAt()
        ));
        booking.markSynced(provider.createEvent(booking).externalEventId());
        dashboardEventPublisher.bookingCreated(booking);
        webhookDeliveryService.bookingCreated(booking);
        outboundCallService.scheduleReminder(booking);
        return booking;
    }

    @Transactional
    public Booking cancel(UUID tenantId, UUID bookingId) {
        var booking = get(tenantId, bookingId);
        booking.cancel();
        webhookDeliveryService.bookingCancelled(booking);
        return booking;
    }
}
