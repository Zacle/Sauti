package com.sauti.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.billing.CommunicationUsageMeteringService;
import com.sauti.integration.IntegrationService;
import com.sauti.integration.WhatsAppEmbeddedSignupService;
import com.sauti.integration.WhatsAppEmbeddedSignupService.TemplateOption;
import com.sauti.integration.WhatsAppEmbeddedSignupService.TemplateParameter;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import com.sauti.whatsapp.WhatsAppMessage.StaffSender;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Replies from the team: who sent them, and approved templates once the 24-hour window has closed. */
class WhatsAppInboxTeamRepliesTest {
    private static final TemplateOption FOLLOW_UP = new TemplateOption("t1", "follow_up", "en", "UTILITY",
            "POSITIONAL",
            List.of(new TemplateParameter("header.1", "header", 1, "1", "{{1}}"),
                    new TemplateParameter("body.2", "body", 2, "2", "{{2}}"),
                    new TemplateParameter("body.1", "body", 1, "1", "{{1}}")),
            "Hello {{1}}", "Your {{1}} is ready. Reply to book {{2}}.");

    @Test
    void aTeamReplyKeepsWhoSentIt() {
        var harness = new Harness();
        harness.conversation.assign("human");
        harness.conversation.receive("Amina", "Hi", java.time.OffsetDateTime.now());
        when(harness.sender.sendTextTracked(anyString(), anyString(), anyString(), any()))
                .thenReturn(new WhatsAppMessageSender.SendResult("wamid-1", "sent"));

        var sent = harness.service.sendHuman(harness.tenantId, harness.conversation.getId(), "On my way",
                new StaffSender(UUID.randomUUID(), "ana@clinic.example"));

        assertThat(sent.author()).isEqualTo("staff");
        assertThat(sent.sentBy()).isEqualTo("ana@clinic.example");
    }

    @Test
    void sendsAnApprovedTemplateWithItsValuesInOrder() {
        var harness = new Harness();
        harness.conversation.assign("human");
        harness.conversation.needsAttention(WhatsAppConversation.WAITING_FOR_TEAM);
        when(harness.sender.sendTemplateTracked(anyString(), anyString(), any(), any()))
                .thenReturn(new WhatsAppMessageSender.SendResult("wamid-t", "sent"));

        var sent = harness.service.sendTemplate(harness.tenantId, harness.conversation.getId(),
                new StaffSender(null, "ana@clinic.example"), "follow_up", "en",
                Map.of("header.1", "Amina", "body.1", "prescription", "body.2", "Friday"));

        assertThat(sent.type()).isEqualTo("template");
        assertThat(sent.templateName()).isEqualTo("follow_up");
        assertThat(sent.body()).isEqualTo("Hello Amina\n\nYour prescription is ready. Reply to book Friday.");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<String, ?>> template = ArgumentCaptor.forClass(Map.class);
        verify(harness.sender).sendTemplateTracked(eq("phone-1"), eq("254700000000"), template.capture(), any());
        assertThat((Map<String, Object>) template.getValue()).containsEntry("name", "follow_up")
                .containsEntry("language", Map.of("code", "en"));
        assertThat(template.getValue().get("components")).isEqualTo(List.of(
                Map.of("type", "header", "parameters", List.of(Map.of("type", "text", "text", "Amina"))),
                Map.of("type", "body", "parameters", List.of(
                        Map.of("type", "text", "text", "prescription"),
                        Map.of("type", "text", "text", "Friday")))));
        // Counted as one message like any team reply, and the conversation no longer waits on the team.
        verify(harness.metering).meterOutboundMessage(harness.tenantId, harness.conversation.getAgentId(),
                "whatsapp", "wamid-t", sent.id().toString(), "template");
        assertThat(harness.conversation.isNeedsAttention()).isFalse();
    }

    @Test
    void namedTemplatesSendEachValueWithItsName() {
        var named = new TemplateOption("t2", "welcome", "en", "UTILITY", "NAMED",
                List.of(new TemplateParameter("body.first_name", "body", 1, "first_name", "{{first_name}}")),
                "", "Hi {{first_name}}");

        var payload = WhatsAppInboxService.templatePayload(named, Map.of("body.first_name", "Amina"));

        assertThat(payload.get("components")).isEqualTo(List.of(Map.of("type", "body", "parameters", List.of(
                Map.of("type", "text", "text", "Amina", "parameter_name", "first_name")))));
        assertThat(WhatsAppInboxService.renderTemplate(named, Map.of("body.first_name", "Amina")))
                .isEqualTo("Hi Amina");
    }

    @Test
    void refusesATemplateWithAnEmptyValueOrBeforeTakingOver() {
        var harness = new Harness();
        assertThatThrownBy(() -> harness.service.sendTemplate(harness.tenantId, harness.conversation.getId(),
                null, "follow_up", "en", Map.of()))
                .hasMessageContaining("Take over");

        harness.conversation.assign("human");
        assertThatThrownBy(() -> harness.service.sendTemplate(harness.tenantId, harness.conversation.getId(),
                null, "follow_up", "en", Map.of("header.1", "Amina", "body.1", " ", "body.2", "Friday")))
                .hasMessageContaining("{{1}} in the template body");
        assertThatThrownBy(() -> harness.service.sendTemplate(harness.tenantId, harness.conversation.getId(),
                null, "unknown", "en", Map.of()))
                .hasMessageContaining("isn't approved");
        verify(harness.sender, never()).sendTemplateTracked(anyString(), anyString(), any(), any());
    }

    @Test
    void searchMatchesNumbersHoweverTheyAreTyped() {
        assertThat(WhatsAppInboxService.likePattern("+254 (700) 000-111")).isEqualTo("%254700000111%");
        assertThat(WhatsAppInboxService.likePattern("  Amina ")).isEqualTo("%amina%");
        assertThat(WhatsAppInboxService.likePattern("50%_off")).isEqualTo("%50!%!_off%");
        assertThat(WhatsAppInboxService.likePattern("")).isEqualTo("%");
    }

    @Test
    void anInboxCursorRoundTripsAndABadOneIsRefused() {
        var cursor = new WhatsAppInboxService.Cursor(java.time.OffsetDateTime.parse("2026-11-25T10:15:30.123456Z"),
                UUID.randomUUID());
        assertThat(WhatsAppInboxService.Cursor.parse(cursor.encode())).isEqualTo(cursor);
        assertThatThrownBy(() -> WhatsAppInboxService.Cursor.parse("not-a-cursor"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class Harness {
        final UUID tenantId = UUID.randomUUID();
        final WhatsAppConversation conversation = new WhatsAppConversation(
                tenantId, UUID.randomUUID(), "phone-1", "254700000000", "Amina");
        final WhatsAppConversationRepository conversations = mock(WhatsAppConversationRepository.class);
        final WhatsAppMessageRepository messages = mock(WhatsAppMessageRepository.class);
        final IntegrationService integrations = mock(IntegrationService.class);
        final WhatsAppMessageSender sender = mock(WhatsAppMessageSender.class);
        final CommunicationUsageMeteringService metering = mock(CommunicationUsageMeteringService.class);
        final WhatsAppEmbeddedSignupService catalog = mock(WhatsAppEmbeddedSignupService.class);
        final WhatsAppInboxService service = new WhatsAppInboxService(conversations, messages, integrations,
                sender, metering, mock(PilotProvisioningPolicyService.class), catalog);

        Harness() {
            var connectionId = UUID.randomUUID();
            when(conversations.findByIdAndTenantId(conversation.getId(), tenantId)).thenReturn(Optional.of(conversation));
            when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(integrations.runtime(tenantId, conversation.getAgentId(), "whatsapp"))
                    .thenReturn(new IntegrationService.RuntimeConfiguration(connectionId, Map.of(),
                            Map.of("accessToken", "token")));
            when(catalog.templates(tenantId, connectionId)).thenReturn(List.of(FOLLOW_UP));
        }
    }
}
