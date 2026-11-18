package com.sauti.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/** Writes the same JSON error shape as the API exception handler, for errors raised in filters. */
final class SecurityErrors {
    private SecurityErrors() {
    }

    static void write(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        var safeMessage = message.replace("\"", "'");
        // "code" matches ApiError from the exception handler; "error" is kept for existing clients.
        response.getWriter().write("{\"code\":\"" + error + "\",\"error\":\"" + error
                + "\",\"message\":\"" + safeMessage + "\"}");
    }
}
