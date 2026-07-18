package com.sauti.calendar;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Delivers owner-selected booking alerts only after the booking transaction commits. */
@Service
public class BookingNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookingNotificationService.class);

    private final BookingRepository bookingRepository;
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public BookingNotificationService(
            BookingRepository bookingRepository,
            JavaMailSender mailSender,
            @Value("${sauti.email.from:no-reply@sauti.uk}") String fromAddress
    ) {
        this.bookingRepository = bookingRepository;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void bookingCreated(BookingCreatedEvent event) {
        var booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null || !booking.getAgent().getBookingNotificationChannels().contains("email")) return;
        var configured = booking.getAgent().getBookingNotificationRecipient();
        var recipient = configured == null || configured.isBlank()
                ? booking.getTenant().getEmail()
                : configured;
        try {
            var message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipient);
            message.setSubject("New Sauti booking " + booking.getBookingReference());
            message.setText("""
                    A booking was captured by %s.

                    Booking number: %s
                    Customer: %s
                    Phone: %s
                    Service: %s
                    Appointment: %s
                    Calendar status: %s

                    Review or update this booking in the Sauti Bookings dashboard.
                    """.formatted(
                    booking.getAgent().getName(), booking.getBookingReference(), booking.getCallerName(),
                    booking.getCallerPhone(), booking.getServiceType(), booking.getAppointmentAt(),
                    booking.getCalendarSyncStatus()
            ));
            mailSender.send(message);
        } catch (RuntimeException exception) {
            LOGGER.warn("Booking owner notification failed bookingId={} recipient={}: {}",
                    booking.getId(), recipient, exception.getMessage());
        }
    }

    public record BookingCreatedEvent(UUID bookingId) { }
}
