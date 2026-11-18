package com.sauti.tenant.domain;

/** Too many invitations from one workspace in a short time. Maps to 429. */
public class InvitationLimitExceededException extends RuntimeException {
    public InvitationLimitExceededException(String message) {
        super(message);
    }
}
