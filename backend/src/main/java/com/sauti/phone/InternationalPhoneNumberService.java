package com.sauti.phone;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class InternationalPhoneNumberService {
    private final PhoneNumberUtil phoneNumbers = PhoneNumberUtil.getInstance();
    private final List<CountryCallingCode> countries = phoneNumbers.getSupportedRegions().stream()
            .map(region -> new CountryCallingCode(
                    region,
                    new Locale("", region).getDisplayCountry(Locale.ENGLISH),
                    "+" + phoneNumbers.getCountryCodeForRegion(region)))
            .sorted(Comparator.comparing(CountryCallingCode::name)
                    .thenComparing(CountryCallingCode::region))
            .toList();

    public List<CountryCallingCode> countries() {
        return countries;
    }

    public String normalize(String value, String region) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        var normalizedRegion = region == null ? "" : region.trim().toUpperCase(Locale.ROOT);
        if (!phoneNumbers.getSupportedRegions().contains(normalizedRegion)) {
            throw new IllegalArgumentException("Select a valid country calling code");
        }
        try {
            var parsed = phoneNumbers.parse(value.trim(), normalizedRegion);
            if (!phoneNumbers.isValidNumber(parsed)) {
                throw invalid();
            }
            return phoneNumbers.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException exception) {
            throw invalid();
        }
    }

    public String normalizeAndRequireMatch(String enteredNumber, String region, String providerNumber) {
        var entered = normalize(enteredNumber, region);
        var authoritative = normalize(providerNumber, region);
        if (!entered.equals(authoritative)) {
            throw new IllegalArgumentException(
                    "The business number entered does not match the phone number selected in Meta");
        }
        return authoritative;
    }

    public record CountryCallingCode(String region, String name, String dialingCode) { }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(
                "Enter a valid phone number after the selected country calling code");
    }
}
