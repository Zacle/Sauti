package com.sauti.auth;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdminPolicy {
    public static final String ROLE = "PLATFORM_ADMIN";
    private final Set<String> adminEmails;

    public PlatformAdminPolicy(@Value("${sauti.admin.emails:}") String configuredEmails) {
        this.adminEmails = Arrays.stream(configuredEmails.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String roleFor(User user) {
        return allows(user.getEmail()) ? ROLE : user.getRole();
    }

    public boolean allows(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
