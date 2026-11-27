package com.sauti.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sauti.call.CallPipelineService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WhatsAppInboxWorkerTest {
    @Test
    void endsQuietConversationsAndKeepsGoingPastAFailure() {
        var inbox = mock(WhatsAppInboxService.class);
        var pipeline = mock(CallPipelineService.class);
        var broken = new WhatsAppInboxService.QuietSession(UUID.randomUUID(), UUID.randomUUID(), "254700000001");
        var quiet = new WhatsAppInboxService.QuietSession(UUID.randomUUID(), UUID.randomUUID(), "254700000002");
        when(inbox.quietOpenSessions(any(), anyInt())).thenReturn(List.of(broken, quiet));
        when(pipeline.endWhatsAppConversation(broken.agentId(), broken.customerNumber()))
                .thenThrow(new IllegalStateException("database busy"));
        var worker = new WhatsAppInboxWorker(mock(WhatsAppChannelService.class), inbox, pipeline);

        assertThat(worker.endQuietConversations()).isEqualTo(1);

        verify(pipeline).endWhatsAppConversation(quiet.agentId(), quiet.customerNumber());
        verify(inbox).sessionEnded(quiet.conversationId());
        verify(inbox, never()).sessionEnded(broken.conversationId());
    }

    @Test
    void closesConversationsQuietForAWeekAndKeepsGoingPastAFailure() {
        var inbox = mock(WhatsAppInboxService.class);
        var channel = mock(WhatsAppChannelService.class);
        var broken = new WhatsAppInboxService.InactiveConversation(UUID.randomUUID(), UUID.randomUUID());
        var quiet = new WhatsAppInboxService.InactiveConversation(UUID.randomUUID(), UUID.randomUUID());
        var cutoff = org.mockito.ArgumentCaptor.forClass(java.time.OffsetDateTime.class);
        when(inbox.inactiveOpen(cutoff.capture(), anyInt())).thenReturn(List.of(broken, quiet));
        when(channel.close(broken.tenantId(), broken.conversationId(), WhatsAppConversation.CLOSED_INACTIVE))
                .thenThrow(new IllegalStateException("database busy"));
        var worker = new WhatsAppInboxWorker(channel, inbox, mock(CallPipelineService.class));

        assertThat(worker.closeInactiveConversations()).isEqualTo(1);

        verify(channel).close(quiet.tenantId(), quiet.conversationId(), WhatsAppConversation.CLOSED_INACTIVE);
        assertThat(cutoff.getValue()).isBefore(java.time.OffsetDateTime.now().minusDays(7).plusMinutes(1))
                .isAfter(java.time.OffsetDateTime.now().minusDays(7).minusMinutes(1));
    }
}
