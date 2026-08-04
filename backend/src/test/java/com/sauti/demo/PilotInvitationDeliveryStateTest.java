package com.sauti.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PilotInvitationDeliveryStateTest {
    @Test
    void tracksProviderAcceptanceAndSanitizedFailureWithoutClaimingMailboxDelivery() {
        var invitation = invitation();
        var first = OffsetDateTime.now();
        invitation.recordDeliveryFailure(first, "MailSendException");
        assertThat(invitation.getDeliveryStatus()).isEqualTo("failed");
        assertThat(invitation.getDeliveryAttempts()).isEqualTo(1);
        assertThat(invitation.getLastDeliveryError()).isEqualTo("MailSendException");

        invitation.recordSent(first.plusMinutes(1));
        assertThat(invitation.getDeliveryStatus()).isEqualTo("sent");
        assertThat(invitation.getDeliveryAttempts()).isEqualTo(2);
        assertThat(invitation.getSentAt()).isNotNull();
        assertThat(invitation.getLastDeliveryError()).isNull();
    }

    private PilotInvitation invitation() {
        var request = new DemoRequest("Acme", "Amina", "owner@example.com", "KE", null,
                "Healthcare", "under-100", "voice", "Answer calls", null);
        return new PilotInvitation(request, "hash", OffsetDateTime.now().plusHours(1));
    }
}
