package com.sauti.calendar;

import com.sauti.dashboard.DashboardEventPublisher;
import com.sauti.notification.WorkspaceNotificationService;
import jakarta.mail.MessagingException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Delivers owner-selected booking alerts only after the booking transaction commits. */
@Service
public class BookingNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BookingNotificationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(
            "EEEE, d MMMM uuuu", Locale.ENGLISH
    );
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern(
            "h:mm a", Locale.ENGLISH
    );
    private static final DateTimeFormatter ACTION_TIME = DateTimeFormatter.ofPattern(
            "d MMM uuuu 'at' h:mm a", Locale.ENGLISH
    );

    private final BookingRepository bookingRepository;
    private final WorkspaceNotificationService workspaceNotificationService;
    private final DashboardEventPublisher dashboardEventPublisher;
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromAddress;
    private final String fromName;
    private final String replyToAddress;
    private final String dashboardBaseUrl;

    public BookingNotificationService(
            BookingRepository bookingRepository,
            WorkspaceNotificationService workspaceNotificationService,
            DashboardEventPublisher dashboardEventPublisher,
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${sauti.email.from:no-reply@sauti.uk}") String fromAddress,
            @Value("${sauti.email.from-name:Sauti}") String fromName,
            @Value("${sauti.email.reply-to:support@sauti.uk}") String replyToAddress,
            @Value("${sauti.dashboard.base-url:https://sauti.uk}") String dashboardBaseUrl
    ) {
        this.bookingRepository = bookingRepository;
        this.workspaceNotificationService = workspaceNotificationService;
        this.dashboardEventPublisher = dashboardEventPublisher;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.replyToAddress = replyToAddress;
        this.dashboardBaseUrl = stripTrailingSlash(dashboardBaseUrl);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bookingCreated(BookingCreatedEvent event) {
        var booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) return;
        var calendarSyncFailed = "pending_owner_action".equals(booking.getCalendarSyncStatus());
        if (calendarSyncFailed || booking.getAgent().getBookingNotificationChannels().contains("dashboard")) {
            try {
                workspaceNotificationService.bookingCreated(booking.getId());
            } catch (RuntimeException exception) {
                LOGGER.warn("Dashboard booking notification failed bookingId={}: {}",
                        booking.getId(), exception.getMessage());
            }
        }
        dashboardEventPublisher.bookingCreated(booking);
        sendBookingEmail(booking, BookingEmailStatus.CONFIRMED, null, booking.getBookedAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bookingStatusChanged(BookingStatusChangedEvent event) {
        var booking = bookingRepository.findById(event.bookingId()).orElse(null);
        if (booking == null) return;
        sendBookingEmail(
                booking,
                event.status(),
                event.previousAppointmentAt(),
                event.statusChangedAt()
        );
    }

    private void sendBookingEmail(
            Booking booking,
            BookingEmailStatus status,
            OffsetDateTime previousAppointmentAt,
            OffsetDateTime statusChangedAt
    ) {
        if (!booking.getAgent().getBookingNotificationChannels().contains("email")) return;
        var configured = booking.getAgent().getBookingNotificationRecipient();
        var recipient = configured == null || configured.isBlank()
                ? booking.getTenant().getEmail()
                : configured;
        var calendarSyncFailed = "pending_owner_action".equals(booking.getCalendarSyncStatus());
        try {
            var view = emailView(booking);
            var notification = emailNotification(
                    booking,
                    status,
                    previousAppointmentAt,
                    statusChangedAt
            );
            var context = new Context(Locale.ENGLISH);
            context.setVariable("booking", view);
            context.setVariable("notification", notification);
            context.setVariable("calendarSyncFailed", calendarSyncFailed);
            context.setVariable("bookingsUrl", dashboardBaseUrl + "/bookings");
            var html = templateEngine.process("email/booking-confirmation", context);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromName + " <" + fromAddress + ">");
            helper.setReplyTo(replyToAddress);
            helper.setTo(recipient);
            helper.setSubject((calendarSyncFailed ? "Action required: " : "")
                    + notification.subjectPrefix() + " | " + view.shortAppointment()
                    + " | " + booking.getBookingReference());
            helper.setText(plainText(view, notification, calendarSyncFailed), html);
            mailSender.send(message);
        } catch (MessagingException | RuntimeException exception) {
            LOGGER.warn("Booking owner notification failed bookingId={} recipient={}: {}",
                    booking.getId(), recipient, exception.getMessage());
        }
    }

    BookingEmailView emailView(Booking booking) {
        var timezone = safeTimezone(booking.getAgent().getTimezone());
        var appointment = booking.getAppointmentAt().atZoneSameInstant(timezone);
        var captured = booking.getBookedAt().atZoneSameInstant(timezone);
        return new BookingEmailView(
                booking.getAgent().getName(),
                booking.getBookingReference(),
                booking.getCallerName(),
                booking.getCallerPhone(),
                booking.getServiceType(),
                appointment.format(DATE),
                appointment.format(TIME),
                timezoneLabel(timezone, appointment),
                durationLabel(booking.getDurationMinutes()),
                captured.format(ACTION_TIME),
                booking.getCalendarSyncStatus(),
                booking.getCalendarSyncError(),
                appointment.format(DateTimeFormatter.ofPattern("EEE, d MMM 'at' h:mm a", Locale.ENGLISH))
        );
    }

    BookingEmailNotification emailNotification(
            Booking booking,
            BookingEmailStatus status,
            OffsetDateTime previousAppointmentAt,
            OffsetDateTime statusChangedAt
    ) {
        var timezone = safeTimezone(booking.getAgent().getTimezone());
        var actionTime = (statusChangedAt == null ? booking.getBookedAt() : statusChangedAt)
                .atZoneSameInstant(timezone);
        var previousAppointment = previousAppointmentAt == null ? "" : previousAppointmentAt
                .atZoneSameInstant(timezone)
                .format(DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu 'at' h:mm a", Locale.ENGLISH))
                + " in " + timezoneLabel(
                        timezone, previousAppointmentAt.atZoneSameInstant(timezone)
                );
        return switch (status) {
            case CONFIRMED -> new BookingEmailNotification(
                    "New booking confirmed", "CONFIRMED", "New booking",
                    "Confirmation captured at", actionTime.format(ACTION_TIME), "", false
            );
            case RESCHEDULED -> new BookingEmailNotification(
                    "Booking rescheduled", "RESCHEDULED", "Booking rescheduled",
                    "Reschedule confirmed at", actionTime.format(ACTION_TIME), previousAppointment, false
            );
            case CANCELLED -> new BookingEmailNotification(
                    "Booking cancelled", "CANCELLED", "Booking cancelled",
                    "Cancellation confirmed at", actionTime.format(ACTION_TIME), "", true
            );
        };
    }

    private String plainText(
            BookingEmailView view,
            BookingEmailNotification notification,
            boolean calendarSyncFailed
    ) {
        return """
                %s
                Status: %s
                Captured by: %s

                %s
                %s | %s
                Duration: %s
                %s

                Customer: %s
                Phone: %s
                Service: %s
                Booking number: %s
                %s: %s
                Calendar status: %s
                %s

                Review this booking: %s/bookings
                """.formatted(
                notification.heading(), notification.statusLabel(), view.agentName(),
                view.appointmentDate(), view.appointmentTime(), view.timezone(), view.duration(),
                notification.previousAppointment().isBlank()
                        ? "" : "Previous appointment: " + notification.previousAppointment(),
                view.customerName(), view.customerPhone(), view.service(), view.bookingNumber(),
                notification.actionTimeLabel(), notification.actionTime(), view.calendarStatus(),
                calendarSyncFailed ? "Calendar issue: " + view.calendarError() : "",
                dashboardBaseUrl
        );
    }

    private ZoneId safeTimezone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException ignored) {
            return ZoneId.of("UTC");
        }
    }

    private String timezoneLabel(ZoneId timezone, ZonedDateTime appointment) {
        var offset = appointment.getOffset().getId();
        if ("Z".equals(offset)) offset = "+00:00";
        return timezone.getId().replace('_', ' ') + " (UTC" + offset + ")";
    }

    private String durationLabel(int durationMinutes) {
        if (durationMinutes == 60) return "1 hour";
        if (durationMinutes > 60 && durationMinutes % 60 == 0) {
            return (durationMinutes / 60) + " hours";
        }
        return durationMinutes + " minutes";
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "https://sauti.uk";
        return value.trim().replaceAll("/+$", "");
    }

    record BookingEmailView(
            String agentName,
            String bookingNumber,
            String customerName,
            String customerPhone,
            String service,
            String appointmentDate,
            String appointmentTime,
            String timezone,
            String duration,
            String confirmedAt,
            String calendarStatus,
            String calendarError,
            String shortAppointment
    ) { }

    record BookingEmailNotification(
            String heading,
            String statusLabel,
            String subjectPrefix,
            String actionTimeLabel,
            String actionTime,
            String previousAppointment,
            boolean cancelled
    ) { }

    public enum BookingEmailStatus {
        CONFIRMED,
        RESCHEDULED,
        CANCELLED
    }

    public record BookingCreatedEvent(UUID bookingId) { }

    public record BookingStatusChangedEvent(
            UUID bookingId,
            BookingEmailStatus status,
            OffsetDateTime previousAppointmentAt,
            OffsetDateTime statusChangedAt
    ) { }
}
