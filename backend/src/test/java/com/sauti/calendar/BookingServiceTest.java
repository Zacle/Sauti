package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class BookingServiceTest {
    @Test
    void commitsLocalBookingAndQueuesConfiguredCalendarWithoutCallingIt() {
        var fixture = fixture("Google Calendar");
        when(fixture.provider.createEvent(any())).thenReturn(new CalendarSyncResult("google-event-1"));

        var booking = fixture.service.create(fixture.tenant.getId(), fixture.request, fixture.provider);

        assertThat(booking.getCalendarSyncStatus()).isEqualTo("pending");
        assertThat(booking.getExternalEventId()).isNull();
        assertThat(booking.getCalendarSyncNextAttemptAt()).isNotNull();
        var ordered = inOrder(fixture.bookingRepository, fixture.transactionManager, fixture.provider);
        ordered.verify(fixture.bookingRepository).saveAndFlush(any(Booking.class));
        ordered.verify(fixture.transactionManager).commit(any());
        verify(fixture.provider, never()).createEvent(any(Booking.class));
        verify(fixture.eventPublisher).publishEvent(any(BookingNotificationService.BookingCreatedEvent.class));
    }

    @Test
    void liveCreateDoesNotContactAFailingCalendarProvider() {
        var fixture = fixture("Google Calendar");
        when(fixture.provider.createEvent(any())).thenThrow(new IllegalStateException("expired credential token"));

        var booking = fixture.service.create(fixture.tenant.getId(), fixture.request, fixture.provider);

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getCalendarSyncStatus()).isEqualTo("pending");
        assertThat(booking.getStatus()).isEqualTo("confirmed");
        assertThat(booking.getCalendarSyncError()).isNull();
        verify(fixture.provider, never()).createEvent(any());
        verify(fixture.eventPublisher).publishEvent(any(BookingNotificationService.BookingCreatedEvent.class));
    }

    @Test
    void backgroundWorkerSynchronizesACommittedBooking() {
        var fixture = fixture("Google Calendar");
        var booking = fixture.service.create(fixture.tenant.getId(), fixture.request);
        when(fixture.bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(fixture.calendarProviderFactory.connectedForAgent(fixture.requestAgent.getId()))
                .thenReturn(Optional.of(fixture.provider));
        when(fixture.provider.createEvent(booking)).thenReturn(new CalendarSyncResult("google-event-1"));
        var processor = new BookingCalendarSyncService.BookingCalendarSyncProcessor(
                fixture.bookingRepository,
                fixture.calendarProviderFactory
        );

        processor.synchronize(booking.getId());

        assertThat(booking.getCalendarSyncStatus()).isEqualTo("synced");
        assertThat(booking.getExternalEventId()).isEqualTo("google-event-1");
    }

    @Test
    void backgroundWorkerRetainsAndRetriesTheDatabaseBookingWhenCalendarFails() {
        var fixture = fixture("Google Calendar");
        var booking = fixture.service.create(fixture.tenant.getId(), fixture.request);
        when(fixture.bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(fixture.calendarProviderFactory.connectedForAgent(fixture.requestAgent.getId()))
                .thenReturn(Optional.of(fixture.provider));
        when(fixture.provider.createEvent(booking))
                .thenThrow(new IllegalStateException("expired credential token"));
        var processor = new BookingCalendarSyncService.BookingCalendarSyncProcessor(
                fixture.bookingRepository,
                fixture.calendarProviderFactory
        );

        processor.synchronize(booking.getId());

        assertThat(booking.getStatus()).isEqualTo("confirmed");
        assertThat(booking.getCalendarSyncStatus()).isEqualTo("pending");
        assertThat(booking.getCalendarSyncAttempts()).isEqualTo(1);
        assertThat(booking.getCalendarSyncNextAttemptAt()).isNotNull();
        assertThat(booking.getCalendarSyncError())
                .isEqualTo("Calendar connection is missing or no longer authorized");
    }

    @Test
    void rescheduleCommitsToSautiAndQueuesCalendarUpdateWithoutCallingProvider() {
        var fixture = fixture("Google Calendar");
        var booking = existingBooking(fixture);
        booking.markSynced("google-event-1");
        when(fixture.bookingRepository.findByIdAndTenantId(booking.getId(), fixture.tenant.getId()))
                .thenReturn(Optional.of(booking));
        var newAppointment = booking.getAppointmentAt().plusDays(1);

        fixture.service.reschedule(
                fixture.tenant.getId(),
                booking.getId(),
                new BookingDtos.RescheduleBookingRequest(newAppointment, 45)
        );

        assertThat(booking.getAppointmentAt()).isEqualTo(newAppointment);
        assertThat(booking.getCalendarSyncStatus()).isEqualTo("pending");
        assertThat(booking.getExternalEventId()).isEqualTo("google-event-1");
        verify(fixture.provider, never()).updateEvent(any());
    }

    @Test
    void backgroundWorkerUpdatesTheExistingCalendarEvent() {
        var fixture = fixture("Google Calendar");
        var booking = existingBooking(fixture);
        booking.markSynced("google-event-1");
        booking.reschedule(booking.getAppointmentAt().plusDays(1), 45);
        booking.queueCalendarRefresh();
        when(fixture.bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(fixture.calendarProviderFactory.connectedForAgent(fixture.requestAgent.getId()))
                .thenReturn(Optional.of(fixture.provider));
        when(fixture.provider.updateEvent(booking)).thenReturn(new CalendarSyncResult("google-event-1"));
        var processor = new BookingCalendarSyncService.BookingCalendarSyncProcessor(
                fixture.bookingRepository,
                fixture.calendarProviderFactory
        );

        processor.synchronize(booking.getId());

        assertThat(booking.getCalendarSyncStatus()).isEqualTo("synced");
        assertThat(booking.getExternalEventId()).isEqualTo("google-event-1");
        verify(fixture.provider).updateEvent(booking);
        verify(fixture.provider, never()).createEvent(any());
    }

    @Test
    void cancellationCommitsToSautiAndQueuesCalendarDeleteWithoutCallingProvider() {
        var fixture = fixture("Google Calendar");
        var booking = existingBooking(fixture);
        booking.markSynced("google-event-1");
        when(fixture.bookingRepository.findByIdAndTenantId(booking.getId(), fixture.tenant.getId()))
                .thenReturn(Optional.of(booking));

        fixture.service.cancel(fixture.tenant.getId(), booking.getId());

        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(booking.getCalendarSyncStatus()).isEqualTo("pending");
        assertThat(booking.getExternalEventId()).isEqualTo("google-event-1");
        verify(fixture.provider, never()).deleteEvent(any());
    }

    @Test
    void backgroundWorkerDeletesTheCalendarEventAndKeepsSautiCancelled() {
        var fixture = fixture("Google Calendar");
        var booking = existingBooking(fixture);
        booking.markSynced("google-event-1");
        booking.cancel();
        booking.queueCalendarRefresh();
        when(fixture.bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(fixture.calendarProviderFactory.connectedForAgent(fixture.requestAgent.getId()))
                .thenReturn(Optional.of(fixture.provider));
        var processor = new BookingCalendarSyncService.BookingCalendarSyncProcessor(
                fixture.bookingRepository,
                fixture.calendarProviderFactory
        );

        processor.synchronize(booking.getId());

        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(booking.getCalendarSyncStatus()).isEqualTo("synced");
        assertThat(booking.getExternalEventId()).isNull();
        verify(fixture.provider).deleteEvent(booking);
    }

    @Test
    void cancellationBeforeCalendarCreationCompletesWithoutCreatingOrDeletingAnEvent() {
        var fixture = fixture("Google Calendar");
        var booking = existingBooking(fixture);
        booking.queueCalendarSync();
        booking.cancel();
        booking.queueCalendarRefresh();
        when(fixture.bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        var processor = new BookingCalendarSyncService.BookingCalendarSyncProcessor(
                fixture.bookingRepository,
                fixture.calendarProviderFactory
        );

        processor.synchronize(booking.getId());

        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(booking.getCalendarSyncStatus()).isEqualTo("synced");
        verify(fixture.provider, never()).createEvent(any());
        verify(fixture.provider, never()).deleteEvent(any());
    }

    @Test
    void failedCalendarDeleteNeverRestoresACancelledSautiBooking() {
        var fixture = fixture("Google Calendar");
        var booking = existingBooking(fixture);
        booking.markSynced("google-event-1");
        booking.cancel();
        booking.queueCalendarRefresh();
        when(fixture.bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(fixture.calendarProviderFactory.connectedForAgent(fixture.requestAgent.getId()))
                .thenReturn(Optional.of(fixture.provider));
        doThrow(new IllegalStateException("Google unavailable")).when(fixture.provider).deleteEvent(booking);
        var processor = new BookingCalendarSyncService.BookingCalendarSyncProcessor(
                fixture.bookingRepository,
                fixture.calendarProviderFactory
        );

        processor.synchronize(booking.getId());

        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(booking.getCalendarSyncStatus()).isEqualTo("pending");
        assertThat(booking.getCalendarSyncAttempts()).isEqualTo(1);
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
    void returnsTheCommittedBookingWhenAnAfterCommitSynchronizationThrows() {
        var fixture = fixture("Set up later");
        var call = new Call(
                fixture.tenant,
                fixture.requestAgent,
                "managed-call-id",
                fixture.request.callerPhone(),
                "inbound"
        );
        var request = new CreateBookingRequest(
                fixture.request.agentId(),
                call.getId(),
                fixture.request.callerName(),
                fixture.request.callerPhone(),
                fixture.request.callerEmail(),
                fixture.request.serviceType(),
                fixture.request.appointmentAt(),
                fixture.request.durationMinutes(),
                fixture.request.capturedData()
        );
        var saved = new AtomicReference<Booking>();
        when(fixture.bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return saved.get();
        });
        when(fixture.callRepository.findByIdAndTenantId(call.getId(), fixture.tenant.getId()))
                .thenReturn(Optional.of(call));
        when(fixture.bookingRepository
                .findFirstByTenantIdAndCall_IdAndAgent_IdAndStatusNotAndAppointmentAt(
                        fixture.tenant.getId(),
                        call.getId(),
                        fixture.requestAgent.getId(),
                        "cancelled",
                        request.appointmentAt()
                ))
                .thenReturn(Optional.empty())
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        doThrow(new IllegalStateException("after-commit listener failed"))
                .when(fixture.transactionManager).commit(any());

        var booking = fixture.service.create(fixture.tenant.getId(), request);

        assertThat(booking).isSameAs(saved.get());
        assertThat(booking.getStatus()).isEqualTo("confirmed");
        verify(fixture.bookingRepository).saveAndFlush(any(Booking.class));
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
                .findAllByTenantIdAndAgent_IdInAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
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
    void sameTimeRemainsAvailableToADifferentAgent() {
        var fixture = fixture("Set up later");
        var secondAgent = new Agent(fixture.tenant, "Gerard", "Bonjour", "Prompt");
        secondAgent.update(
                "Gerard", "Bonjour", "Prompt", "fr", List.of("fr"),
                null, List.of(), true, "UTC", ""
        );
        when(fixture.bookingRepository
                .findAllByTenantIdAndAgent_IdInAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
                        any(), any(), any(), any(), any()
                )).thenReturn(List.of());
        when(fixture.serviceAgentRepository().findByIdAndTenantId(
                secondAgent.getId(), fixture.tenant.getId()
        )).thenReturn(Optional.of(secondAgent));
        var request = new CreateBookingRequest(
                secondAgent.getId(),
                null,
                "Second customer",
                "0115752441",
                null,
                "Class",
                fixture.request.appointmentAt(),
                60,
                Map.of()
        );

        var booking = fixture.service.create(fixture.tenant.getId(), request);

        assertThat(booking.getAgent().getId()).isEqualTo(secondAgent.getId());
        @SuppressWarnings("unchecked")
        var queriedAgents = ArgumentCaptor.forClass((Class<java.util.Collection<UUID>>) (Class<?>) java.util.Collection.class);
        verify(fixture.bookingRepository)
                .findAllByTenantIdAndAgent_IdInAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
                        any(), queriedAgents.capture(), any(), any(), any()
                );
        assertThat(queriedAgents.getValue()).containsExactly(secondAgent.getId());
    }

    @Test
    void sameTimeIsBlockedAcrossAgentsSharingOneCalendarScope() {
        var fixture = fixture("Google Calendar");
        var secondAgent = new Agent(fixture.tenant, "Gerard", "Bonjour", "Prompt");
        secondAgent.update(
                "Gerard", "Bonjour", "Prompt", "fr", List.of("fr"),
                null, List.of(), true, "UTC", ""
        );
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
        when(fixture.serviceAgentRepository().findByIdAndTenantId(
                secondAgent.getId(), fixture.tenant.getId()
        )).thenReturn(Optional.of(secondAgent));
        when(fixture.conflictScopeService.resolveAndLock(fixture.tenant.getId(), secondAgent.getId()))
                .thenReturn(new BookingConflictScopeService.ConflictScope(
                        UUID.randomUUID(),
                        List.of(fixture.requestAgent.getId(), secondAgent.getId())
                ));
        when(fixture.bookingRepository
                .findAllByTenantIdAndAgent_IdInAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
                        any(), any(), any(), any(), any()
                )).thenReturn(List.of(existing));
        var request = new CreateBookingRequest(
                secondAgent.getId(), null, "Second customer", "0115752441", null, "Class",
                fixture.request.appointmentAt(), 60, Map.of()
        );

        assertThatThrownBy(() -> fixture.service.create(fixture.tenant.getId(), request))
                .isInstanceOf(BookingSlotUnavailableException.class);

        verify(fixture.bookingRepository, never()).saveAndFlush(any());
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
                .findAllByTenantIdAndAgent_IdInAndStatusNotAndAppointmentAtGreaterThanEqualAndAppointmentAtLessThan(
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

    @Test
    void removesBookingDependenciesBeforePermanentlyDeletingTheBooking() {
        var fixture = fixture("Set up later");
        var booking = new Booking(
                fixture.tenant,
                fixture.requestAgent,
                null,
                fixture.request.callerName(),
                fixture.request.callerPhone(),
                fixture.request.callerEmail(),
                fixture.request.serviceType(),
                fixture.request.appointmentAt(),
                fixture.request.durationMinutes(),
                "{}"
        );
        when(fixture.bookingRepository.findByIdAndTenantId(booking.getId(), fixture.tenant.getId()))
                .thenReturn(Optional.of(booking));

        fixture.service.delete(fixture.tenant.getId(), booking.getId());

        var ordered = inOrder(fixture.outboundCallService, fixture.callRepository, fixture.bookingRepository);
        ordered.verify(fixture.outboundCallService)
                .deleteBookingReminders(fixture.tenant.getId(), booking.getId());
        ordered.verify(fixture.callRepository)
                .clearLegacyBookingReference(fixture.tenant.getId(), booking.getId());
        ordered.verify(fixture.bookingRepository).delete(booking);
        ordered.verify(fixture.bookingRepository).flush();
    }

    @Test
    void publishesACancelledEmailStatusAfterCancellingTheBooking() {
        var fixture = fixture("Set up later");
        var booking = existingBooking(fixture);
        when(fixture.bookingRepository.findByIdAndTenantId(booking.getId(), fixture.tenant.getId()))
                .thenReturn(Optional.of(booking));

        fixture.service.cancel(fixture.tenant.getId(), booking.getId());

        var event = captureStatusEvent(fixture);
        assertThat(booking.getStatus()).isEqualTo("cancelled");
        assertThat(event.bookingId()).isEqualTo(booking.getId());
        assertThat(event.status()).isEqualTo(BookingNotificationService.BookingEmailStatus.CANCELLED);
        assertThat(event.previousAppointmentAt()).isNull();
        assertThat(event.statusChangedAt()).isNotNull();
    }

    @Test
    void publishesARescheduledEmailStatusWithTheOldAppointment() {
        var fixture = fixture("Set up later");
        var booking = existingBooking(fixture);
        var previousAppointment = booking.getAppointmentAt();
        var newAppointment = previousAppointment.plusDays(3).withHour(9);
        when(fixture.bookingRepository.findByIdAndTenantId(booking.getId(), fixture.tenant.getId()))
                .thenReturn(Optional.of(booking));

        fixture.service.reschedule(
                fixture.tenant.getId(),
                booking.getId(),
                new BookingDtos.RescheduleBookingRequest(newAppointment, 45)
        );

        var event = captureStatusEvent(fixture);
        assertThat(booking.getAppointmentAt()).isEqualTo(newAppointment);
        assertThat(event.bookingId()).isEqualTo(booking.getId());
        assertThat(event.status()).isEqualTo(BookingNotificationService.BookingEmailStatus.RESCHEDULED);
        assertThat(event.previousAppointmentAt()).isEqualTo(previousAppointment);
        assertThat(event.statusChangedAt()).isNotNull();
    }

    private Booking existingBooking(Fixture fixture) {
        return new Booking(
                fixture.tenant,
                fixture.requestAgent,
                null,
                fixture.request.callerName(),
                fixture.request.callerPhone(),
                fixture.request.callerEmail(),
                fixture.request.serviceType(),
                fixture.request.appointmentAt(),
                fixture.request.durationMinutes(),
                "{}"
        );
    }

    private BookingNotificationService.BookingStatusChangedEvent captureStatusEvent(Fixture fixture) {
        var captor = ArgumentCaptor.forClass(
                BookingNotificationService.BookingStatusChangedEvent.class
        );
        verify(fixture.eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
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
        var calendarProviderFactory = mock(CalendarProviderFactory.class);
        var conflictScopeService = mock(BookingConflictScopeService.class);
        when(conflictScopeService.resolve(any(), any())).thenAnswer(invocation -> {
            UUID requestedAgentId = invocation.getArgument(1, UUID.class);
            return new BookingConflictScopeService.ConflictScope(null, List.of(requestedAgentId));
        });
        when(conflictScopeService.resolveAndLock(any(), any())).thenAnswer(invocation -> {
            UUID requestedAgentId = invocation.getArgument(1, UUID.class);
            return new BookingConflictScopeService.ConflictScope(null, List.of(requestedAgentId));
        });
        var eventPublisher = mock(ApplicationEventPublisher.class);
        var callRepository = mock(CallRepository.class);
        var outboundCallService = mock(OutboundCallService.class);
        var service = new BookingService(
                bookingRepository,
                agentRepository,
                callRepository,
                mock(WebhookDeliveryService.class),
                outboundCallService,
                calendarProviderFactory,
                conflictScopeService,
                new ObjectMapper(),
                eventPublisher,
                transactionManager
        );
        var request = new CreateBookingRequest(
                agent.getId(), null, "Zachary", "01115753441", null, "Haircut",
                OffsetDateTime.now().plusDays(2), 60, Map.of("style", "Fade")
        );
        return new Fixture(
                tenant,
                agent,
                agentRepository,
                bookingRepository,
                callRepository,
                outboundCallService,
                transactionManager,
                provider,
                calendarProviderFactory,
                conflictScopeService,
                eventPublisher,
                service,
                request
        );
    }

    private record Fixture(
            Tenant tenant,
            Agent requestAgent,
            AgentRepository serviceAgentRepository,
            BookingRepository bookingRepository,
            CallRepository callRepository,
            OutboundCallService outboundCallService,
            PlatformTransactionManager transactionManager,
            CalendarProvider provider,
            CalendarProviderFactory calendarProviderFactory,
            BookingConflictScopeService conflictScopeService,
            ApplicationEventPublisher eventPublisher,
            BookingService service,
            CreateBookingRequest request
    ) { }
}
