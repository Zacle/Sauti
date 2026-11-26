package com.sauti.whatsapp;

import com.sauti.call.CallPipelineService;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps WhatsApp conversations moving without a new customer message: customer messages left unfinished
 * are processed again, and a conversation whose customer has been quiet for 24 hours is ended, so its
 * summary, CRM sync and follow-ups run then rather than whenever the customer next writes.
 */
@Component
public class WhatsAppInboxWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppInboxWorker.class);
    private static final int QUIET_BATCH = 50;

    private final WhatsAppChannelService channel;
    private final WhatsAppInboxService inbox;
    private final CallPipelineService pipeline;

    public WhatsAppInboxWorker(WhatsAppChannelService channel, WhatsAppInboxService inbox,
                               CallPipelineService pipeline) {
        this.channel = channel;
        this.inbox = inbox;
        this.pipeline = pipeline;
    }

    @Scheduled(initialDelayString = "${sauti.whatsapp.retry-initial-delay-ms:60000}",
            fixedDelayString = "${sauti.whatsapp.retry-delay-ms:60000}")
    public void retryUnfinished() {
        try {
            channel.retryUnfinished();
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not queue unfinished WhatsApp messages", exception);
        }
    }

    @Scheduled(initialDelayString = "${sauti.whatsapp.quiet-sweep-initial-delay-ms:120000}",
            fixedDelayString = "${sauti.whatsapp.quiet-sweep-delay-ms:600000}")
    public int endQuietConversations() {
        var cutoff = OffsetDateTime.now().minus(CallPipelineService.WHATSAPP_CONVERSATION_IDLE);
        var ended = 0;
        for (var session : inbox.quietOpenSessions(cutoff, QUIET_BATCH)) {
            try {
                pipeline.endWhatsAppConversation(session.agentId(), session.customerNumber());
                inbox.sessionEnded(session.conversationId());
                ended++;
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not end a quiet WhatsApp conversation conversationId={}",
                        session.conversationId(), exception);
            }
        }
        return ended;
    }
}
