package com.sauti.auth.infrastructure;

import com.sauti.auth.application.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordHasher implements PasswordHasher {
    private final PasswordEncoder encoder;
    private volatile String timingReference;

    public BcryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && hash != null && encoder.matches(rawPassword, hash);
    }

    @Override
    public void spendEquivalentTime(String rawPassword) {
        var reference = timingReference;
        if (reference == null) {
            reference = encoder.encode("sauti-timing-reference");
            timingReference = reference;
        }
        encoder.matches(rawPassword == null ? "" : rawPassword, reference);
    }
}
