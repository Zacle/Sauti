package com.sauti.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

class DemoLifecycleEmailListenerTest {
    @Test
    void receiptNotifiesBothThePlatformAndTheRequester() throws Exception {
        var mail = mail(); var templates = templates();
        var listener = new DemoRequestEmailListener(mail, templates,
                "noreply@sauti.uk", "Sauti", "support@sauti.uk",
                "https://admin.sauti.uk/admin/demo-requests");

        listener.notifyParties(new DemoRequestReceived(request()));

        var messages = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail, times(2)).send(messages.capture());
        assertThat(messages.getAllValues()).extracting(message -> message.getAllRecipients()[0].toString())
                .containsExactly("support@sauti.uk", "owner@example.com");
        assertThat(messages.getAllValues().get(0).getSubject()).contains("Acme");
        assertThat(messages.getAllValues().get(1).getSubject()).isEqualTo("We received your Sauti demo request");
        var contexts = ArgumentCaptor.forClass(IContext.class);
        verify(templates, times(2)).process(any(String.class), contexts.capture());
        assertThat(contexts.getAllValues().get(0).getVariable("adminReviewUrl"))
                .isEqualTo("https://admin.sauti.uk/admin/demo-requests");
    }

    @Test
    void rejectionExplainsTheDecisionToTheRequester() throws Exception {
        var mail = mail(); var templates = templates(); var request = request();
        request.reject("Pilot capacity is currently full", OffsetDateTime.now());
        var listener = new DemoRequestRejectedEmailListener(mail, templates,
                "noreply@sauti.uk", "Sauti", "support@sauti.uk");

        listener.send(new DemoRequestRejected(request));

        var message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getAllRecipients()[0].toString()).isEqualTo("owner@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("Update on your Sauti demo request");
    }

    private JavaMailSender mail() {
        var sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(ignored ->
                new MimeMessage(Session.getInstance(new Properties())));
        return sender;
    }
    private TemplateEngine templates() {
        var templates = mock(TemplateEngine.class);
        when(templates.process(any(String.class), any(IContext.class))).thenReturn("<p>Sauti</p>");
        return templates;
    }
    private DemoRequest request() {
        return new DemoRequest("Acme", "Amina", "owner@example.com", "KE", null,
                "Healthcare", "under-100", "voice", "Answer calls", null);
    }
}
