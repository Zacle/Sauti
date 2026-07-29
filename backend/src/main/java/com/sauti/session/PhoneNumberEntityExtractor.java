package com.sauti.session;

import com.sauti.call.Call;

/**
 * Extracts the exact ordered phone digits from a caller's multilingual source
 * utterance instead of trusting a conversational model's regenerated number.
 */
@FunctionalInterface
public interface PhoneNumberEntityExtractor {
    String extract(Call call, String sourceUtterance, String candidate);
}
