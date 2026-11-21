package com.sauti.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.billing.CommunicationUsageMeteringService;
import com.sauti.call.CallIntakeNoteService;
import com.sauti.call.CallRepository;
import com.sauti.calendar.BookingRepository;
import com.sauti.tool.WebhookDestinationValidator;
import com.sauti.webhook.WebhookDeliveryService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.thymeleaf.TemplateEngine;

class PostCallDeliveryIsolationTest {
    @Test
    void recordsRetryBackoffWhenAProviderFailureHappensInsideTheDelivery() {
        var tenantId = UUID.randomUUID();
        var binding = new AgentIntegration(tenantId, UUID.randomUUID(), "google_sheets");
        var delivery = new IntegrationDelivery(new PostCallJob(tenantId, UUID.randomUUID(), false), binding);
        var deliveries = mock(IntegrationDeliveryRepository.class);
        when(deliveries.findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(any(), any()))
                .thenReturn(List.of(delivery));
        when(deliveries.findById(delivery.getId())).thenReturn(Optional.of(delivery));
        var calls = mock(CallRepository.class);
        when(calls.findById(delivery.getCallId())).thenReturn(Optional.empty());
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        var service = new PostCallIntegrationService(
                mock(PostCallJobRepository.class), deliveries, mock(AgentIntegrationRepository.class),
                mock(IntegrationConnectionRepository.class), calls, mock(BookingRepository.class),
                mock(WhatsAppTemplateParameterMapper.class), mock(CallIntakeNoteService.class),
                mock(IntegrationService.class), mock(ProviderOAuthService.class),
                mock(GoogleSheetsApiClient.class), mock(GoogleSheetsCustomerSyncService.class),
                new ObjectMapper(), mock(JavaMailSender.class), mock(TemplateEngine.class),
                mock(WebhookDestinationValidator.class), mock(WebhookDeliveryService.class),
                mock(CommunicationUsageMeteringService.class), transactionManager,
                "noreply@example.com", "https://graph.example.com", "https://app.example.com");

        service.deliverDue();

        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo("retrying");
        assertThat(delivery.getLastError()).isNotBlank();
    }
}
