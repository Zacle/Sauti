package com.sauti.auth;

/** The refresh token is missing, expired, revoked or was reused. The person must sign in again. Maps to 401. */
public class InvalidSessionException extends RuntimeException {
    public InvalidSessionException() {
        super("Your session has expired. Please sign in again.");
    }
}
