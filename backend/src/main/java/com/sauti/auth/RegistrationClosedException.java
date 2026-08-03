package com.sauti.auth;

public class RegistrationClosedException extends RuntimeException {
    public RegistrationClosedException() {
        super("New workspaces are currently available by demo request only.");
    }
}
