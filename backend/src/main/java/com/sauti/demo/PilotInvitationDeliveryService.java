package com.sauti.demo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PilotInvitationDeliveryService {
    private final PilotInvitationRepository invitations;

    public PilotInvitationDeliveryService(PilotInvitationRepository invitations) {
        this.invitations = invitations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSent(UUID invitationId) {
        invitations.findById(invitationId).ifPresent(invitation ->
                invitation.recordSent(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID invitationId, Throwable error) {
        invitations.findById(invitationId).ifPresent(invitation -> invitation.recordDeliveryFailure(
                OffsetDateTime.now(ZoneOffset.UTC), error.getClass().getSimpleName()));
    }
}
