package com.sauti.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.sauti.agent.Agent;
import com.sauti.calendar.Booking;
import com.sauti.calendar.BookingRepository;
import com.sauti.tenant.Tenant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkspaceNotificationServiceTest {
    @Test
    void createsReadableBookingNotificationWithStructuredPayload() throws Exception {
        var repository = mock(WorkspaceNotificationRepository.class);
        var bookingRepository = mock(BookingRepository.class);
        var mapper = new ObjectMapper();
        var tenant = new Tenant("Studio", "owner@example.com", "KE");
        var agent = new Agent(tenant, "Amina", "Greeting", "Prompt");
        var booking = new Booking(
                tenant, agent, null, "Zachary Nji", "+201115753441", null,
                "Consultation", OffsetDateTime.now().plusDays(2), 45, Map.of().toString()
        );
        booking.markSynced("google-event-1");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new WorkspaceNotificationService(repository, bookingRepository, mapper);

        service.bookingCreated(booking.getId());

        var capture = ArgumentCaptor.forClass(WorkspaceNotification.class);
        verify(repository).save(capture.capture());
        var notification = capture.getValue();
        assertThat(notification.getType()).isEqualTo("booking.confirmed");
        assertThat(notification.getTitle()).isEqualTo("New booking confirmed");
        assertThat(notification.getMessage()).contains("Zachary Nji", "Consultation");
        assertThat(notification.getHref()).isEqualTo("/bookings");
        assertThat(mapper.readValue(notification.getPayload(), new TypeReference<Map<String, Object>>() { }))
                .containsEntry("callerName", "Zachary Nji")
                .containsEntry("calendarSyncStatus", "synced");
    }
}
