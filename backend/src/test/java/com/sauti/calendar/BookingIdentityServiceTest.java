package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BookingIdentityServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AGENT_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 8, 2);
    private static final ZoneId TIMEZONE = ZoneId.of("Africa/Cairo");

    @Test
    void requiresCallerSuppliedTimeBeforeDisclosingASingleMatchingBooking() {
        var bookings = mock(BookingService.class);
        var booking = booking("0115753441", "2026-08-02T09:00:00+03:00");
        when(bookings.findOnAppointmentDateForAgent(TENANT_ID, AGENT_ID, DATE, TIMEZONE))
                .thenReturn(List.of(booking));

        var result = new BookingIdentityService(bookings).verify(request("0115753441", null));

        assertThat(result.status()).isEqualTo(BookingIdentityService.Status.TIME_REQUIRED);
        assertThat(result.booking()).isNull();
    }

    @Test
    void verifiesPhoneDateAndTimeWithoutUsingLanguageDependentNames() {
        var bookings = mock(BookingService.class);
        var booking = booking("011 575 3441", "2026-08-02T09:00:00+03:00");
        when(booking.getCallerName()).thenReturn("هاري");
        when(bookings.findOnAppointmentDateForAgent(TENANT_ID, AGENT_ID, DATE, TIMEZONE))
                .thenReturn(List.of(booking));

        var result = new BookingIdentityService(bookings).verify(request("0115753441", "09:00"));

        assertThat(result.status()).isEqualTo(BookingIdentityService.Status.VERIFIED);
        assertThat(result.booking()).isSameAs(booking);
    }

    @Test
    void doesNotRevealWhetherThePhoneOrTimeWasWrong() {
        var bookings = mock(BookingService.class);
        var booking = booking("0115753441", "2026-08-02T09:00:00+03:00");
        when(bookings.findOnAppointmentDateForAgent(TENANT_ID, AGENT_ID, DATE, TIMEZONE))
                .thenReturn(List.of(booking));

        var wrongPhone = new BookingIdentityService(bookings).verify(request("0110000000", "09:00"));
        var wrongTime = new BookingIdentityService(bookings).verify(request("0115753441", "10:00"));

        assertThat(wrongPhone.status()).isEqualTo(BookingIdentityService.Status.MISMATCH);
        assertThat(wrongTime.status()).isEqualTo(BookingIdentityService.Status.MISMATCH);
        assertThat(wrongPhone.booking()).isNull();
        assertThat(wrongTime.booking()).isNull();
    }

    @Test
    void requiresReferenceWhenPhoneDateAndTimeStillIdentifyMultipleBookings() {
        var bookings = mock(BookingService.class);
        var first = booking("0115753441", "2026-08-02T09:00:00+03:00");
        var second = booking("0115753441", "2026-08-02T09:00:00+03:00");
        when(bookings.findOnAppointmentDateForAgent(TENANT_ID, AGENT_ID, DATE, TIMEZONE)).thenReturn(List.of(
                first, second
        ));

        var result = new BookingIdentityService(bookings).verify(request("0115753441", "09:00"));

        assertThat(result.status()).isEqualTo(BookingIdentityService.Status.REFERENCE_REQUIRED);
        assertThat(result.booking()).isNull();
    }

    @Test
    void referenceLookupCannotFallBackToAnotherAgentInTheWorkspace() {
        var bookings = mock(BookingService.class);
        var otherAgentBooking = booking("0115753441", "2026-08-02T09:00:00+03:00");
        when(bookings.resolveForAgent(TENANT_ID, AGENT_ID, "SAT-OTHERAGENT1"))
                .thenThrow(new EntityNotFoundException("Booking not found"));
        when(bookings.resolve(TENANT_ID, "SAT-OTHERAGENT1")).thenReturn(otherAgentBooking);
        var request = new BookingIdentityService.Request(
                TENANT_ID,
                AGENT_ID,
                "0115753441",
                "SAT-OTHERAGENT1",
                "",
                null,
                null,
                TIMEZONE
        );

        var result = new BookingIdentityService(bookings).verify(request);

        assertThat(result.status()).isEqualTo(BookingIdentityService.Status.MISMATCH);
        assertThat(result.booking()).isNull();
        verify(bookings, never()).resolve(TENANT_ID, "SAT-OTHERAGENT1");
    }

    @Test
    void verifiesFinalFourReferenceCharactersOnlyWithinAgentAndPhoneScope() {
        var bookings = mock(BookingService.class);
        var booking = booking("0115753441", "2026-08-02T09:00:00+03:00");
        when(bookings.findByReferenceSuffixForAgent(TENANT_ID, AGENT_ID, "EF56"))
                .thenReturn(List.of(booking));
        var request = new BookingIdentityService.Request(
                TENANT_ID,
                AGENT_ID,
                "0115753441",
                "",
                "ef-56",
                null,
                null,
                TIMEZONE
        );

        var result = new BookingIdentityService(bookings).verify(request);

        assertThat(result.status()).isEqualTo(BookingIdentityService.Status.VERIFIED);
        assertThat(result.booking()).isSameAs(booking);
        verify(bookings).findByReferenceSuffixForAgent(TENANT_ID, AGENT_ID, "EF56");
    }

    @Test
    void refusesToGuessWhenFinalFourCharactersMatchMultiplePhoneScopedBookings() {
        var bookings = mock(BookingService.class);
        var first = booking("0115753441", "2026-08-02T09:00:00+03:00");
        var second = booking("0115753441", "2026-08-03T09:00:00+03:00");
        when(bookings.findByReferenceSuffixForAgent(TENANT_ID, AGENT_ID, "EF56"))
                .thenReturn(List.of(first, second));
        var request = new BookingIdentityService.Request(
                TENANT_ID,
                AGENT_ID,
                "0115753441",
                "",
                "EF56",
                null,
                null,
                TIMEZONE
        );

        var result = new BookingIdentityService(bookings).verify(request);

        assertThat(result.status()).isEqualTo(BookingIdentityService.Status.REFERENCE_SUFFIX_AMBIGUOUS);
        assertThat(result.booking()).isNull();
    }

    private BookingIdentityService.Request request(String phone, String time) {
        return new BookingIdentityService.Request(
                TENANT_ID,
                AGENT_ID,
                phone,
                "",
                "",
                DATE,
                time == null ? null : java.time.LocalTime.parse(time),
                TIMEZONE
        );
    }

    private Booking booking(String phone, String appointmentAt) {
        var booking = mock(Booking.class);
        when(booking.getCallerPhone()).thenReturn(phone);
        when(booking.getAppointmentAt()).thenReturn(OffsetDateTime.parse(appointmentAt));
        return booking;
    }
}
