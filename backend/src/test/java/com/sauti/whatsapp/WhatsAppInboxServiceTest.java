package com.sauti.whatsapp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.integration.IntegrationService;
import com.sauti.billing.CommunicationUsageMeteringService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WhatsAppInboxServiceTest {
    @Test
    void preventsHumanSendingUntilTheConversationIsTakenOver() {
        var fixture = fixture();

        assertThatThrownBy(() -> fixture.service.sendHuman(
                fixture.tenantId, fixture.conversation.getId(), "Hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Take over");
    }

    @Test
    void neverReturnsAConversationFromAnotherTenant() {
        var fixture = fixture();
        when(fixture.conversations.findByIdAndTenantId(fixture.conversation.getId(), fixture.tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.messages(fixture.tenantId, fixture.conversation.getId()))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }

    @Test
    void appliesProviderDeliveryStatusToTheMatchingOutboundMessage() {
        var fixture = fixture();
        var message = mock(WhatsAppMessage.class);
        when(fixture.messages.findByProviderMessageId("wamid-out")).thenReturn(Optional.of(message));

        fixture.service.providerStatus("wamid-out", "delivered", null);

        verify(message).status("delivered", null);
        verify(fixture.messages).save(message);
    }

    private Fixture fixture() {
        var tenantId = UUID.randomUUID();
        var agentId = UUID.randomUUID();
        var conversation = new WhatsAppConversation(
                tenantId, agentId, "phone-1", "254700000000", "Amina");
        var conversations = mock(WhatsAppConversationRepository.class);
        var messages = mock(WhatsAppMessageRepository.class);
        when(conversations.findByIdAndTenantId(conversation.getId(), tenantId))
                .thenReturn(Optional.of(conversation));
        when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new Fixture(tenantId, conversation, conversations, messages,
                new WhatsAppInboxService(conversations, messages,
                        mock(IntegrationService.class), mock(WhatsAppMessageSender.class),
                        mock(CommunicationUsageMeteringService.class)));
    }

    private record Fixture(UUID tenantId, WhatsAppConversation conversation,
                           WhatsAppConversationRepository conversations,
                           WhatsAppMessageRepository messages,
                           WhatsAppInboxService service) { }
}
