package com.sauti.business.domain;

/** A business profile value that breaks a rule; the message is safe to show the owner. */
public class BusinessProfileException extends IllegalArgumentException {
    public BusinessProfileException(String message) {
        super(message);
    }
}
