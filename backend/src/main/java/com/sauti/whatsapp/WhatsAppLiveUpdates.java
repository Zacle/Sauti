package com.sauti.whatsapp;

import com.sauti.dashboard.DashboardEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells the workspace's open consoles that the inbox changed, once the change is saved, so the inbox
 * refreshes at once instead of at its next poll. A change made outside a transaction is sent straight away.
 */
@Component
class WhatsAppLiveUpdates {
    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppLiveUpdates.class);
    private final DashboardEventPublisher dashboardEvents;

    WhatsAppLiveUpdates(DashboardEventPublisher dashboardEvents) {
        this.dashboardEvents = dashboardEvents;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void changed(WhatsAppInboxChanged event) {
        try {
            dashboardEvents.whatsappChanged(event.tenantId(), event.conversationId());
        } catch (RuntimeException exception) {
            // Consoles still poll, so a missed signal only delays the update.
            LOGGER.debug("Could not signal a WhatsApp inbox change", exception);
        }
    }
}
