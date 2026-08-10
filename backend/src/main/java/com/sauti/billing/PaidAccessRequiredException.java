package com.sauti.billing;

public class PaidAccessRequiredException extends RuntimeException {
    public PaidAccessRequiredException(String message) {
        super(message);
    }
}
