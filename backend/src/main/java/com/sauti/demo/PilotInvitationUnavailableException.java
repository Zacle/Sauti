package com.sauti.demo;

public class PilotInvitationUnavailableException extends RuntimeException {
    public PilotInvitationUnavailableException() {
        super("Invitation is expired or already used");
    }
}
