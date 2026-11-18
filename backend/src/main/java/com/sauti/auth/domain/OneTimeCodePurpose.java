package com.sauti.auth.domain;

/** Why a short emailed code was issued. Codes for one purpose never satisfy another. */
public enum OneTimeCodePurpose {
    EMAIL_VERIFICATION("email:verify:"),
    PASSWORD_RESET("password:reset:");

    /** At most this many wrong guesses before the code is discarded and a new one is needed. */
    public static final int MAX_ATTEMPTS = 5;

    private final String keyPrefix;

    OneTimeCodePurpose(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
