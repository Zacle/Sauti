package com.sauti.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.call.Call;
import com.sauti.call.CallPipelineService;
import com.sauti.call.BrowserSpeechToTextService;
import com.sauti.agent.Agent;
import com.sauti.tenant.Tenant;
import com.sauti.voice.VoiceCatalogService;
import com.sauti.integration.IntegrationService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WhatsAppChannelServiceTest {
    @Test
    void persistsFailedStateWhenSendingReplyFails() throws Exception {
        var repository = mock(WhatsAppInboundMessageRepository.class);
        var pipeline = mock(CallPipelineService.class);
        var sender = mock(WhatsAppMessageSender.class);
        var speech = mock(BrowserSpeechToTextService.class);
        var voices = mock(VoiceCatalogService.class);
        var converter = mock(OggOpusAudioConverter.class);
        var integrations = mock(IntegrationService.class);
        var inbound = new WhatsAppInboundMessage("wamid-1", "phone-1", "254700000000", "audio");
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var tenant = mock(Tenant.class);
        var tenantId = java.util.UUID.randomUUID();
        var agentId = java.util.UUID.randomUUID();
        var media = new WhatsAppMessageSender.DownloadedMedia(new byte[]{1}, "audio/ogg");

        when(repository.existsByProviderMessageId("wamid-1")).thenReturn(false);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByProviderMessageId("wamid-1")).thenReturn(Optional.of(inbound));
        when(pipeline.startWhatsAppConversation("phone-1", "254700000000")).thenReturn(call);
        when(call.getAgent()).thenReturn(agent);
        when(call.getTenant()).thenReturn(tenant);
        when(tenant.getId()).thenReturn(tenantId);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("voice-1");
        when(integrations.runtime(any(), any(), org.mockito.ArgumentMatchers.eq("whatsapp")))
                .thenReturn(new IntegrationService.RuntimeConfiguration(
                        java.util.UUID.randomUUID(), java.util.Map.of(),
                        java.util.Map.of("accessToken", "workspace-token")));
        when(sender.downloadMedia("media-1", "workspace-token")).thenReturn(media);
        when(speech.transcribe(agent, media.bytes(), media.contentType())).thenReturn("Hello");
        when(pipeline.processLiveTranscriptTurn(call, "Hello"))
                .thenReturn(new CallPipelineService.TurnResult("en", "Reply", new byte[0], ""));
        when(voices.synthesize("voice-1", "en", "Reply")).thenReturn(new byte[]{2});
        when(converter.fromMp3(any())).thenReturn(new byte[]{3});
        org.mockito.Mockito.doThrow(new IllegalStateException("Meta unavailable"))
                .when(sender).sendVoiceNote(
                        org.mockito.ArgumentMatchers.eq("phone-1"),
                        org.mockito.ArgumentMatchers.eq("254700000000"),
                        org.mockito.ArgumentMatchers.any(byte[].class),
                        org.mockito.ArgumentMatchers.eq("workspace-token"));

        var service = new WhatsAppChannelService(
                new ObjectMapper(), repository, pipeline, sender,
                speech,
                voices, converter, integrations);
        try {
            service.accept("""
                    {"entry":[{"changes":[{"value":{
                      "metadata":{"phone_number_id":"phone-1"},
                      "messages":[{"id":"wamid-1","from":"254700000000","type":"audio","audio":{"id":"media-1"}}]
                    }}]}]}
                    """);
            for (int attempt = 0; attempt < 40 && !"failed".equals(inbound.getStatus()); attempt++) {
                Thread.sleep(25);
            }
            assertThat(inbound.getStatus()).isEqualTo("failed");
            assertThat(inbound.getFailureReason()).contains("Meta unavailable");
        } finally {
            service.stop();
        }
    }
}
