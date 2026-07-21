package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.calendar.BookingDtos.CreateBookingRequest;
import com.sauti.call.CallRepository;
import com.sauti.call.Call;
import com.sauti.outbound.OutboundCallService;
import com.sauti.tenant.Tenant;
import com.sauti.tool.CalendarProviderFactory;
import com.sauti.webhook.WebhookDeliveryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class BookingServiceTest {
    @Test
    void commitsLocalBookingBeforeCallingConfiguredCalendar() {
        var fixture = fixture("Google Calendar");
        when(fixture.provider.createEvent(any())).thenReturn(new CalendarSyncResult("google-event-1"));

        var booking = fixture.service.create(fixture.tenant.getId(), fixture.request, fixture.provider);

        assertThat(booking.getCalendarSyncStatus()).isEqualTo("synced");
        assertThat(booking.getExternalEventId()).isEqualTo("google-event-1");
        var ordered = inOrder(fixture.bookingRepository, fixture.transactionManager, fixture.provider);
        ordered.verify(fixture.bookingRepository).saveAndFlush(any(Booking.class));
        ordered.verify(fixture.transactionManager).commit(any());
        ordered.verify(fixture.provider).createEvent(any(Booking.class));
        verify(fixture.eventPublisher).publishEvent(any(BookingNotificationService.BookingCreatedEvent.class));
    }

    @Test
    void keepsDatabaseBookingAndRequestsOwnerFollowUpWhenCalendarFails() {
        var fixture = fixture("Google Calendar");
        when(fixture.provider.createEvent(any())).thenThrow(new IllegalStateException("expired credential token"));

        var booking = fixture.service.create(fixture.tenant.getId(), fixture.request, fixture.provider);

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getCalendarSyncStatus()).isEqualTo("pending_owner_action");
        assertThat(booking.getStatus()).isEqualTo("pending_confirmation");
        assertThat(booking.getCalendarSyncError()).isEqualTo("Calendar connection is missing or no longer authorized");
        verify(fixture.eventPublisher).publishEvent(any(BookingNotificationService.BookingCreatedEvent.class));
    }

    @Test
    void savesLocallyWithoutCallingProviderWhenNoExternalCalendarIsConfigured() {
        var fixture = fixture("Set up later");

        var booking = fixture.service.create(fixture.tenant.getId(), fixture.request, fixture.provider);

        assertThat(booking.getCalendarSyncStatus()).isEqualTo("not_configured");
        assertThat(booking.getStatus()).isEqualTo("confirmed");
        verify(fixture.provider, never()).createEvent(any());
    }

    @Test
    void rejectsAnOverlappingLocalBookingBeforeSavingOrCallingTheProvider() {
        var fixture = fixture("Google Calendar");
        var existing = new Booking(
                fixture.tenant,
                fixture.requestAgent,
                null,
                "Existing customer",
                "0100000000",
                null,
                "Haircut",
                fixture.request.appointmentAt(),
                fixture.request.durationMinutes(),
                "{}"
        );
        when(fixture.bookingRepository
                .findAllByTenantIdAndAgent_IdAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
                        any(), any(), any(), any(), any()
                )).thenReturn(List.of(existing));

        assertThatThrownBy(() -> fixture.service.create(
                fixture.tenant.getId(), fixture.request, fixture.provider
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no longer available");

        verify(fixture.bookingRepository, never()).saveAndFlush(any(Booking.class));
        verify(fixture.provider, never()).createEvent(any());
    }

    @Test
    void removesLocallyOccupiedIntervalsFromProviderAvailability() {
        var fixture = fixture("Google Calendar");
        var existing = new Booking(
                fixture.tenant,
                fixture.requestAgent,
                null,
                "Existing customer",
                "0100000000",
                null,
                "Haircut",
                fixture.request.appointmentAt(),
                fixture.request.durationMinutes(),
                "{}"
        );
        when(fixture.bookingRepository
                .findAllByTenantIdAndAgent_IdAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
                        any(), any(), any(), any(), any()
                )).thenReturn(List.of(existing));
        var occupied = new CalendarAvailabilitySlot(
                fixture.request.appointmentAt(),
                fixture.request.appointmentAt().plusMinutes(60),
                "occupied"
        );
        var available = new CalendarAvailabilitySlot(
                fixture.request.appointmentAt().plusMinutes(60),
                fixture.request.appointmentAt().plusMinutes(120),
                "available"
        );

        var result = fixture.service.excludeLocalConflicts(
                fixture.tenant.getId(),
                fixture.requestAgent.getId(),
                fixture.request.appointmentAt().toLocalDate(),
                java.time.ZoneId.of("UTC"),
                List.of(occupied, available)
        );

        assertThat(result).containsExactly(available);
    }

    @Test
    void returnsTheExistingBookingWhenAReviewedCallRetriesTheSameSave() {
        var fixture = fixture("Google Calendar");
        var callId = UUID.randomUUID();
        var call = mock(Call.class);
        var request = new CreateBookingRequest(
                fixture.request.agentId(), callId, fixture.request.callerName(), fixture.request.callerPhone(),
                fixture.request.callerEmail(), fixture.request.serviceType(), fixture.request.appointmentAt(),
                fixture.request.durationMinutes(), fixture.request.capturedData()
        );
        var existing = new Booking(
                fixture.tenant,
                fixture.requestAgent,
                call,
                request.callerName(),
                request.callerPhone(),
                request.callerEmail(),
                request.serviceType(),
                request.appointmentAt(),
                request.durationMinutes(),
                "{}"
        );
        when(fixture.bookingRepository
                .findFirstByTenantIdAndCall_IdAndAgent_IdAndStatusNotAndAppointmentAt(
                        fixture.tenant.getId(), callId, fixture.requestAgent.getId(), "cancelled",
                        request.appointmentAt()
                )).thenReturn(Optional.of(existing));

        var result = fixture.service.create(fixture.tenant.getId(), request, fixture.provider);

        assertThat(result).isSameAs(existing);
        verify(fixture.bookingRepository, never()).saveAndFlush(any(Booking.class));
        verify(fixture.provider, never()).createEvent(any());
    }

    private Fixture fixture(String calendarProvider) {
        var tenant = new Tenant("Hairy", "owner@example.com", "KE");
        var agent = new Agent(tenant, "Ailsa", "Hello", "Prompt");
        agent.update(
                "Ailsa", "Hello", "Prompt", "en", List.of("en"),
                null, List.of(), true, "UTC", ""
        );
        agent.configureOnboarding("Salon", "Appointment booking", null, List.of("Haircut"),
                calendarProvider, "Fixed calendar", "Provider default");
        var bookingRepository = mock(BookingRepository.class);
        var saved = new AtomicReference<Booking>();
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return saved.get();
        });
        when(bookingRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        var agentRepository = mock(AgentRepository.class);
        when(agentRepository.findByIdAndTenantId(agent.getId(), tenant.getId())).thenReturn(Optional.of(agent));
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var provider = mock(CalendarProvider.class);
        var eventPublisher = mock(ApplicationEventPublisher.class);
        var service = new BookingService(
                bookingRepository,
                agentRepository,
                mock(CallRepository.class),
                mock(WebhookDeliveryService.class),
                mock(OutboundCallService.class),
                mock(CalendarProviderFactory.class),
                new ObjectMapper(),
                eventPublisher,
                transactionManager
        );
        var request = new CreateBookingRequest(
                agent.getId(), null, "Zachary", "01115753441", null, "Haircut",
                OffsetDateTime.now().plusDays(2), 60, Map.of("style", "Fade")
        );
        return new Fixture(tenant, agent, bookingRepository, transactionManager, provider, eventPublisher, service, request);
    }

    private record Fixture(
            Tenant tenant,
            Agent requestAgent,
            BookingRepository bookingRepository,
            PlatformTransactionManager transactionManager,
            CalendarProvider provider,
            ApplicationEventPublisher eventPublisher,
            BookingService service,
            CreateBookingRequest request
    ) { }
}
