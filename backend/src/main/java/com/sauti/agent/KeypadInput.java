package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Keypad (DTMF) input on phone calls: whether it is on, how input ends, and what key presses mean. */
@Embeddable
public class KeypadInput {
    @Column(name = "dtmf_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "dtmf_termination_key", nullable = false)
    private String terminationKey = "#";

    @Column(name = "dtmf_input_timeout_seconds", nullable = false)
    private int inputTimeoutSeconds = 5;

    @Column(name = "dtmf_max_digits", nullable = false)
    private int maxDigits = 8;

    /** One "digits=meaning" pair per line. */
    @Column(name = "dtmf_digit_mappings")
    private String digitMappings;

    protected KeypadInput() {
    }

    static KeypadInput defaults() {
        return new KeypadInput();
    }

    void enable(Boolean enabled) {
        this.enabled = enabled != null && enabled;
    }

    void configure(String terminationKey, Integer inputTimeoutSeconds, Integer maxDigits, Map<String, String> mappings) {
        this.terminationKey = "#".equals(terminationKey) || "*".equals(terminationKey) ? terminationKey : "#";
        this.inputTimeoutSeconds = range(inputTimeoutSeconds, 1, 30, 5);
        this.maxDigits = range(maxDigits, 1, 32, 8);
        if (mappings == null || mappings.isEmpty()) {
            this.digitMappings = null;
            return;
        }
        this.digitMappings = mappings.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> Map.entry(entry.getKey().trim(), entry.getValue().trim()))
                .filter(entry -> entry.getKey().matches("[0-9*#]{1,32}") && !entry.getValue().isBlank())
                .map(entry -> entry.getKey() + "=" + entry.getValue().replace("\r", " ").replace("\n", " "))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    public boolean isEnabled() { return enabled; }
    public String getTerminationKey() { return terminationKey; }
    public int getInputTimeoutSeconds() { return inputTimeoutSeconds; }
    public int getMaxDigits() { return maxDigits; }

    public Map<String, String> getDigitMappings() {
        var mappings = new LinkedHashMap<String, String>();
        if (digitMappings == null || digitMappings.isBlank()) return mappings;
        digitMappings.lines().forEach(line -> {
            var separator = line.indexOf('=');
            if (separator <= 0) return;
            var digits = line.substring(0, separator).trim();
            var meaning = line.substring(separator + 1).trim();
            if (!digits.isBlank() && !meaning.isBlank()) mappings.put(digits, meaning);
        });
        return mappings;
    }

    private static int range(Integer value, int minimum, int maximum, int fallback) {
        return value == null ? fallback : Math.max(minimum, Math.min(maximum, value));
    }
}
