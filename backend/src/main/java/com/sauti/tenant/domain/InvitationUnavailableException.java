package com.sauti.tenant.domain;

/** The invitation link was revoked, already used, replaced by a newer one, or has expired. Maps to 410. */
public class InvitationUnavailableException extends RuntimeException {
    public InvitationUnavailableException() {
        super("This invitation is no longer valid. Ask a workspace admin to send a new one.");
    }
}
