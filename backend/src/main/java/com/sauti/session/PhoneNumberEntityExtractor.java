package com.sauti.session;

import com.sauti.call.Call;

/**
 * Extracts the exact ordered phone digits from a caller's multilingual source
 * utterance instead of trusting a conversational model's regenerated number.
 */
@FunctionalInterface
public interface PhoneNumberEntityExtractor {
    String extract(Call call, String sourceUtterance, String candidate);

    default Extraction extractSequence(Call call, String sourceUtterance, String candidate) {
        var value = extract(call, sourceUtterance, candidate);
        return value == null || value.isBlank()
                ? Extraction.unclear()
                : new Extraction("complete", value.replaceAll("\\D", ""));
    }

    record Extraction(String status, String digits) {
        public Extraction {
            status = status == null ? "unclear" : status.trim().toLowerCase(java.util.Locale.ROOT);
            digits = digits == null ? "" : digits.replaceAll("\\D", "");
            if (!java.util.Set.of("complete", "incomplete", "unclear").contains(status)) status = "unclear";
            if ("unclear".equals(status)) digits = "";
        }

        public static Extraction unclear() {
            return new Extraction("unclear", "");
        }

        public boolean complete() {
            return "complete".equals(status);
        }

        public boolean hasClearDigits() {
            return !digits.isBlank();
        }
    }
}
