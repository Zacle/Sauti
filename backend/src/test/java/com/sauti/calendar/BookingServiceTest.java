package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.sauti.outbound.OutboundCallService;
import com.sauti.tenant.Tenant;
import com.sauti.tool.CalendarProviderFactory;
import com.sauti.webhook.WebhookDeliveryService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
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
        return new Fixture(tenant, bookingRepository, transactionManager, provider, eventPublisher, service, request);
    }

    private record Fixture(
            Tenant tenant,
            BookingRepository bookingRepository,
            PlatformTransactionManager transactionManager,
            CalendarProvider provider,
            ApplicationEventPublisher eventPublisher,
            BookingService service,
            CreateBookingRequest request
    ) { }
}
