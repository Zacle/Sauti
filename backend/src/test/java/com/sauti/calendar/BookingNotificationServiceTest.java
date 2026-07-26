package com.sauti.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sauti.agent.Agent;
import com.sauti.dashboard.DashboardEventPublisher;
import com.sauti.notification.WorkspaceNotificationService;
import com.sauti.tenant.Tenant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class BookingNotificationServiceTest {
    @Test
    void presentsAppointmentTimeInTheBusinessTimezoneWithoutRawIsoFormatting() {
        var tenant = new Tenant("Hairy", "owner@example.com", "KE");
        var agent = new Agent(tenant, "Ailsa", "Hello", "Prompt");
        agent.update(
                "Ailsa", "Hello", "Prompt", "en", List.of("en"),
                null, List.of(), true, "Africa/Cairo", ""
        );
        var booking = new Booking(
                tenant,
                agent,
                null,
                "Zachary",
                "0115752441",
                null,
                "Men hairstyle",
                OffsetDateTime.parse("2026-08-03T09:00:00+03:00"),
                60,
                "{}"
        );
        var service = new BookingNotificationService(
                mock(BookingRepository.class),
                mock(WorkspaceNotificationService.class),
                mock(DashboardEventPublisher.class),
                mock(JavaMailSender.class),
                templateEngine(),
                "no-reply@sauti.uk",
                "Sauti",
                "support@sauti.uk",
                "https://sauti.uk/"
        );

        var view = service.emailView(booking);

        assertThat(view.appointmentDate()).isEqualTo("Monday, 3 August 2026");
        assertThat(view.appointmentTime()).isEqualTo("9:00 AM");
        assertThat(view.timezone()).isEqualTo("Africa/Cairo · UTC+03:00");
        assertThat(view.duration()).isEqualTo("1 hour");
        assertThat(view.shortAppointment()).isEqualTo("Mon, 3 Aug at 9:00 AM");
        assertThat(view.confirmedAt()).doesNotContain("T", "+03:00");
    }

    @Test
    void rendersTheBrandedBookingEmailWithClearTimeLabels() {
        var context = new Context();
        context.setVariable("booking", new BookingNotificationService.BookingEmailView(
                "Ailsa",
                "SAT-AB12CD34",
                "Zachary",
                "0115752441",
                "Men hairstyle",
                "Monday, 3 August 2026",
                "9:00 AM",
                "Africa/Cairo · UTC+03:00",
                "1 hour",
                "26 Jul 2026 at 8:40 PM",
                "synced",
                null,
                "Mon, 3 Aug at 9:00 AM"
        ));
        context.setVariable("calendarSyncFailed", false);
        context.setVariable("bookingsUrl", "https://sauti.uk/bookings");

        var html = templateEngine().process("email/booking-confirmation", context);

        assertThat(html)
                .contains("New booking confirmed")
                .contains("Appointment time")
                .contains("9:00 AM")
                .contains("Business local time · Africa/Cairo · UTC+03:00")
                .contains("Confirmation captured")
                .contains("https://sauti.uk/bookings");
    }

    private TemplateEngine templateEngine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
