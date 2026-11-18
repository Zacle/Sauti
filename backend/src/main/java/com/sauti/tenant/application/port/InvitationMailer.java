package com.sauti.tenant.application.port;

import java.time.OffsetDateTime;

/** Delivers a workspace invitation. Called only after the invitation has been committed. */
public interface InvitationMailer {
    void send(InvitationEmail email);

    record InvitationEmail(
            String toEmail,
            String workspaceName,
            String inviterEmail,
            String role,
            String acceptUrl,
            OffsetDateTime expiresAt
    ) {
    }
}
