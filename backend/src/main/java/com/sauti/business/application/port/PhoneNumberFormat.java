package com.sauti.business.application.port;

/** Turns a phone number as typed into its international (E.164) form, or rejects it. */
public interface PhoneNumberFormat {
    String international(String typed, String defaultCountryCode);
}
