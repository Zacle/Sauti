package com.sauti.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.billing.BillingAccessPolicy;
import com.sauti.call.BrowserSpeechToTextService;
import com.sauti.call.Call;
import com.sauti.call.CallPipelineService;
import com.sauti.integration.IntegrationService;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import com.sauti.tenant.Tenant;
import com.sauti.voice.VoiceCatalogService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WhatsAppChannelServiceTest {
    private final Harness h = new Harness();

    @AfterEach
    void stop() {
        h.service.stop();
    }

    @Test
    void mapsMetaDeliveryStatusesIntoTheInbox() {
        h.service.accept("""
                {"entry":[{"changes":[{"value":{"statuses":[{
                  "id":"wamid-out","status":"delivered"
                }]}}]}]}
                """);
        verify(h.inbox).providerStatus("wamid-out", "delivered", "");
    }

    @Test
    void keepsWhatArrivedSoTheMessageCanBeProcessedAgain() throws Exception {
        h.agentReplies("Hello", "Hi there");

        h.service.accept("""
                {"entry":[{"changes":[{"value":{
                  "metadata":{"phone_number_id":"phone-1"},
                  "contacts":[{"profile":{"name":"Amina"}}],
                  "messages":[{"id":"wamid-1","from":"254700000000","type":"text","text":{"body":"Hello"}}]
                }}]}]}
                """);
        var stored = h.awaitStatus("wamid-1", "completed");

        assertThat(stored.getBody()).isEqualTo("Hello");
        assertThat(stored.getCustomerName()).isEqualTo("Amina");
        assertThat(stored.getAttempts()).isEqualTo(1);
        verify(h.inbox).sendAiText(eq(h.call), any(UUID.class), eq("Hi there"));
    }

    @Test
    void aVoiceNoteReachesTheInboxEvenWhenItCannotBeTranscribed() {
        h.claim(new WhatsAppInboundMessage("wamid-2", "phone-1", "254700000000", "audio",
                "Amina", "", "media-1", "audio/ogg"));
        when(h.sender.downloadMedia("media-1", "workspace-token"))
                .thenThrow(new IllegalStateException("Meta unavailable"));

        h.service.process("wamid-2");

        verify(h.inbox).recordInbound(h.call, "wamid-2", "Amina", "audio", "", "media-1", "audio/ogg");
        var stored = h.stored("wamid-2");
        assertThat(stored.getStatus()).isEqualTo("failed");
        assertThat(stored.getFailureReason()).contains("Meta unavailable");
        assertThat(stored.canRetry()).isTrue();
        // Not flagged yet: it will be tried again.
        verify(h.inbox, never()).needsAttention(anyString(), anyString());
    }

    @Test
    void theLastFailedTryLeavesTheMessageForStaff() {
        h.claim(new WhatsAppInboundMessage("wamid-3", "phone-1", "254700000000", "text",
                "", "Can I book?", "", ""));
        when(h.pipeline.processLiveTranscriptTurn(h.call, "Can I book?"))
                .thenThrow(new IllegalStateException("Model timed out"));

        for (var attempt = 0; attempt < WhatsAppInboundMessage.MAX_ATTEMPTS; attempt++) {
            h.service.process("wamid-3");
        }

        assertThat(h.stored("wamid-3").canRetry()).isFalse();
        verify(h.inbox).needsAttention("wamid-3", WhatsAppConversation.AGENT_FAILED);
    }

    @Test
    void pausedRepliesAreHeldForStaffWithoutCallingTheAgent() {
        when(h.billing.automatedMessages(any()))
                .thenReturn(new BillingAccessPolicy.AccessDecision(false, "messages_over_limit", null, null));
        h.claim(new WhatsAppInboundMessage("wamid-4", "phone-1", "254700000000", "text",
                "", "Are you open?", "", ""));

        h.service.process("wamid-4");

        verify(h.inbox).needsAttention("wamid-4", WhatsAppConversation.AGENT_PAUSED);
        verify(h.pipeline, never()).processLiveTranscriptTurn(any(Call.class), anyString());
        assertThat(h.stored("wamid-4").getStatus()).isEqualTo("completed");
    }

    @Test
    void aConversationTakenOverWaitsForTheTeam() {
        h.mode = "human";
        h.claim(new WhatsAppInboundMessage("wamid-5", "phone-1", "254700000000", "text",
                "", "Thanks", "", ""));

        h.service.process("wamid-5");

        verify(h.inbox).needsAttention("wamid-5", WhatsAppConversation.WAITING_FOR_TEAM);
        verify(h.pipeline, never()).processLiveTranscriptTurn(any(Call.class), anyString());
    }

    @Test
    void aMessageAlreadyAnsweredIsNotAnsweredTwice() {
        h.created = false;
        when(h.inbox.alreadyAnswered("wamid-6")).thenReturn(true);
        h.claim(new WhatsAppInboundMessage("wamid-6", "phone-1", "254700000000", "text",
                "", "Hello", "", ""));

        h.service.process("wamid-6");

        verify(h.pipeline, never()).processLiveTranscriptTurn(any(Call.class), anyString());
        assertThat(h.stored("wamid-6").getStatus()).isEqualTo("completed");
    }

    @Test
    void unfinishedMessagesAreQueuedAgainAndAnswered() throws Exception {
        var lost = new WhatsAppInboundMessage("wamid-7", "phone-1", "254700000000", "text",
                "", "Still there?", "", "");
        lost.markProcessing();
        h.claim(lost);
        when(h.repository.findRetryable(any(), any(), eq(WhatsAppInboundMessage.MAX_ATTEMPTS), any()))
                .thenReturn(List.of(lost));
        h.agentReplies("Still there?", "Yes, how can I help?");

        assertThat(h.service.retryUnfinished()).isEqualTo(1);

        var stored = h.awaitStatus("wamid-7", "completed");
        assertThat(stored.getAttempts()).isEqualTo(2);
        verify(h.inbox).sendAiText(eq(h.call), any(UUID.class), eq("Yes, how can I help?"));
    }

    @Test
    void aVoiceNoteGainsItsTranscriptAndGetsAVoiceReply() {
        h.claim(new WhatsAppInboundMessage("wamid-8", "phone-1", "254700000000", "audio",
                "", "", "media-1", "audio/ogg"));
        var media = new WhatsAppMessageSender.DownloadedMedia(new byte[]{1}, "audio/ogg");
        when(h.sender.downloadMedia("media-1", "workspace-token")).thenReturn(media);
        when(h.speech.transcribe(h.agent, media.bytes(), media.contentType())).thenReturn("Hello");
        h.agentReplies("Hello", "Reply");
        when(h.agent.getTtsVoiceId()).thenReturn("voice-1");
        when(h.voices.synthesize("voice-1", "en", "Reply")).thenReturn(new byte[]{2});
        when(h.converter.fromMp3(any())).thenReturn(new byte[]{3});

        h.service.process("wamid-8");

        verify(h.inbox).transcribed("wamid-8", "Hello");
        verify(h.inbox).sendAiVoice(eq(h.call), any(UUID.class), eq("Reply"), any(byte[].class));
        assertThat(h.stored("wamid-8").getStatus()).isEqualTo("completed");
    }

    private static final class Harness {
        final Map<String, WhatsAppInboundMessage> rows = new ConcurrentHashMap<>();
        final WhatsAppInboundMessageRepository repository = mock(WhatsAppInboundMessageRepository.class);
        final CallPipelineService pipeline = mock(CallPipelineService.class);
        final WhatsAppMessageSender sender = mock(WhatsAppMessageSender.class);
        final WhatsAppInboxService inbox = mock(WhatsAppInboxService.class);
        final BrowserSpeechToTextService speech = mock(BrowserSpeechToTextService.class);
        final VoiceCatalogService voices = mock(VoiceCatalogService.class);
        final OggOpusAudioConverter converter = mock(OggOpusAudioConverter.class);
        final IntegrationService integrations = mock(IntegrationService.class);
        final BillingAccessPolicy billing = mock(BillingAccessPolicy.class);
        final Call call = mock(Call.class);
        final Agent agent = mock(Agent.class);
        final UUID conversationId = UUID.randomUUID();
        volatile String mode = "ai";
        volatile boolean created = true;
        final WhatsAppChannelService service;

        Harness() {
            var tenant = mock(Tenant.class);
            when(tenant.getId()).thenReturn(UUID.randomUUID());
            when(agent.getId()).thenReturn(UUID.randomUUID());
            when(call.getTenant()).thenReturn(tenant);
            when(call.getAgent()).thenReturn(agent);
            when(repository.save(any())).thenAnswer(invocation -> {
                WhatsAppInboundMessage row = invocation.getArgument(0);
                rows.put(row.getProviderMessageId(), row);
                return row;
            });
            when(repository.existsByProviderMessageId(anyString()))
                    .thenAnswer(invocation -> rows.containsKey(invocation.<String>getArgument(0)));
            when(repository.findByProviderMessageId(anyString()))
                    .thenAnswer(invocation -> Optional.ofNullable(rows.get(invocation.<String>getArgument(0))));
            when(inbox.lastCustomerMessageAt(anyString(), anyString())).thenReturn(Optional.empty());
            when(pipeline.startWhatsAppConversation(eq("phone-1"), eq("254700000000"), any()))
                    .thenReturn(call);
            when(inbox.recordInbound(eq(call), anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                    .thenAnswer(invocation -> new WhatsAppInboxService.InboundResult(conversationId, mode, created));
            when(integrations.runtime(any(), any(), eq("whatsapp")))
                    .thenReturn(new IntegrationService.RuntimeConfiguration(
                            UUID.randomUUID(), Map.of(), Map.of("accessToken", "workspace-token")));
            when(billing.automatedMessages(any()))
                    .thenReturn(new BillingAccessPolicy.AccessDecision(true, "active", null, null));
            service = new WhatsAppChannelService(new ObjectMapper(), repository, pipeline, sender, inbox,
                    speech, voices, converter, integrations, mock(PilotProvisioningPolicyService.class), billing);
        }

        void claim(WhatsAppInboundMessage message) {
            rows.put(message.getProviderMessageId(), message);
        }

        WhatsAppInboundMessage stored(String id) {
            return rows.get(id);
        }

        void agentReplies(String prompt, String reply) {
            when(pipeline.processLiveTranscriptTurn(call, prompt))
                    .thenReturn(new CallPipelineService.TurnResult("en", reply, new byte[0], ""));
        }

        WhatsAppInboundMessage awaitStatus(String id, String status) throws InterruptedException {
            for (var attempt = 0; attempt < 200; attempt++) {
                var row = rows.get(id);
                if (row != null && status.equals(row.getStatus())) return row;
                Thread.sleep(10);
            }
            throw new AssertionError("Message " + id + " never reached " + status + ": "
                    + (rows.get(id) == null ? "missing" : rows.get(id).getStatus()));
        }
    }
}
