package com.sauti.auth.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Who may use the Sauti platform console. Still configured by email for now; a separate admin
 * identity with mandatory two-factor sign-in replaces this in a later phase.
 */
@Component
public class PlatformAdminPolicy {
    private final Set<String> adminEmails;

    public PlatformAdminPolicy(@Value("${sauti.admin.emails:}") String configuredEmails) {
        this.adminEmails = Arrays.stream(configuredEmails.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
