package com.sauti.session;

public record PhoneNumberFragment(String target, String digits) {
    public PhoneNumberFragment {
        target = target == null ? "" : target.trim();
        digits = digits == null ? "" : digits.replaceAll("\\D", "");
        if (!java.util.Set.of("caller_phone", "new_caller_phone").contains(target)) {
            throw new IllegalArgumentException("Phone fragment target is invalid");
        }
        if (digits.isBlank() || digits.length() > 15) {
            throw new IllegalArgumentException("Phone fragment digits are invalid");
        }
    }
}
