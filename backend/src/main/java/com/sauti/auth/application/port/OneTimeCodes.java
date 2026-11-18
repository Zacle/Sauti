package com.sauti.auth.application.port;

import com.sauti.auth.domain.OneTimeCodePurpose;

/** Short numeric codes sent by email, with a limited number of guesses each. */
public interface OneTimeCodes {
    /** Issues a fresh code, replacing any previous one for the same purpose and email. */
    String issue(OneTimeCodePurpose purpose, String email);

    /** True only for the current code. Too many wrong guesses discard the code. */
    boolean verify(OneTimeCodePurpose purpose, String email, String code);

    void discard(OneTimeCodePurpose purpose, String email);
}
