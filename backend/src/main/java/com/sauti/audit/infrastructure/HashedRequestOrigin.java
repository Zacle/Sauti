package com.sauti.audit.infrastructure;

import com.sauti.audit.application.port.RequestOrigin;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Hashes the client address with a server-side secret so the log can tell "same place as before"
 * without storing where anyone is. The container has already applied the trusted-proxy policy.
 */
@Component
public class HashedRequestOrigin implements RequestOrigin {
    private final byte[] pepper;

    public HashedRequestOrigin(@Value("${sauti.jwt.secret}") String pepper) {
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String ipHash() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        var address = attributes.getRequest().getRemoteAddr();
        if (address == null || address.isBlank()) return null;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper);
            digest.update(address.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest()).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
