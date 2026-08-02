package com.sauti.integration;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.sauti.call.Call;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Resolves external-message recipients to provider-safe E.164 numbers. */
@Component
public class MessagingRecipientResolver {
    private static final Set<String> TRUSTED_NUMBER_DIRECTIONS = Set.of("inbound", "outbound", "whatsapp");
    private final PhoneNumberUtil phoneNumbers = PhoneNumberUtil.getInstance();

    public Recipient resolve(Call call, Object suppliedNumber) {
        var trusted = trustedCallingNumber(call);
        var supplied = suppliedNumber == null ? "" : String.valueOf(suppliedNumber).trim();
        var candidate = supplied.isBlank() ? trusted : supplied;
        if (candidate.isBlank()) {
            throw new IllegalArgumentException(
                    "No verified calling number is available. Ask for the complete destination number; "
                            + "a foreign number must include its country code.");
        }

        var e164 = normalize(candidate, call.getTenant().getCountryCode());
        var source = !trusted.isBlank() && e164.equals(normalize(trusted, call.getTenant().getCountryCode()))
                ? "calling_number" : "provided_number";
        return new Recipient(e164, mask(e164), source);
    }

    private String trustedCallingNumber(Call call) {
        var direction = call.getDirection() == null ? "" : call.getDirection().toLowerCase(Locale.ROOT);
        if (!TRUSTED_NUMBER_DIRECTIONS.contains(direction)) return "";
        return call.getCallerNumber() == null ? "" : call.getCallerNumber().trim();
    }

    private String normalize(String value, String region) {
        try {
            var parsed = phoneNumbers.parse(value, region == null ? "ZZ" : region.toUpperCase(Locale.ROOT));
            if (!phoneNumbers.isValidNumber(parsed)) throw invalidNumber();
            return phoneNumbers.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException exception) {
            throw invalidNumber();
        }
    }

    private IllegalArgumentException invalidNumber() {
        return new IllegalArgumentException(
                "The destination is not a valid phone number. Ask for the full number; "
                        + "a foreign number must include its country code.");
    }

    private String mask(String e164) {
        var visible = Math.min(4, Math.max(0, e164.length() - 1));
        return "+••••" + e164.substring(e164.length() - visible);
    }

    public record Recipient(String e164, String masked, String source) { }
}
