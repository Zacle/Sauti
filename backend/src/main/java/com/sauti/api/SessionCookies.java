package com.sauti.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh token lives only in this cookie: HttpOnly so page scripts cannot read it, SameSite
 * Strict so other sites cannot send it, and scoped to the auth endpoints so it is not sent with
 * every API call.
 */
@Component
public class SessionCookies {
    public static final String NAME = "sauti_refresh";
    static final String PATH = "/api/v1/auth";

    private final boolean secure;

    public SessionCookies(@Value("${sauti.auth.refresh-cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    void issue(HttpServletResponse response, String refreshToken, OffsetDateTime expiresAt) {
        var maxAge = Duration.between(OffsetDateTime.now(), expiresAt);
        response.addHeader(HttpHeaders.SET_COOKIE, base(refreshToken).maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build().toString());
    }

    void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, base("").maxAge(Duration.ZERO).build().toString());
    }

    String read(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH);
    }
}
