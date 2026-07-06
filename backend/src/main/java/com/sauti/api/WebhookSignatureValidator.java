package com.sauti.api;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared HMAC-SHA1 webhook signature validation used by both Twilio and SignalWire.
 * Both providers sign webhooks identically — only the header name differs.
 */
public class WebhookSignatureValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookSignatureValidator.class);

    private final String authToken;
    private final boolean validationEnabled;
    private final String publicBaseUrl;
    private final String providerLabel;

    public WebhookSignatureValidator(
            String authToken,
            boolean validationEnabled,
            String publicBaseUrl,
            String providerLabel
    ) {
        this.authToken = authToken;
        this.validationEnabled = validationEnabled;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        this.providerLabel = providerLabel;
    }

    public boolean isValid(HttpServletRequest request, String signature, Map<String, String> form) {
        if (!validationEnabled) {
            LOGGER.warn("{} signature validation is disabled; accepting webhook path={}", providerLabel, request.getServletPath());
            return true;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        var signedPayload = canonicalUrl(request) + canonicalForm(form);
        var expected = hmacSha1(signedPayload);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String canonicalUrl(HttpServletRequest request) {
        var url = publicBaseUrl + publicPath(request);
        if (request.getQueryString() == null || request.getQueryString().isBlank()) {
            return url;
        }
        return url + "?" + request.getQueryString();
    }

    private String publicPath(HttpServletRequest request) {
        var servletPath = request.getServletPath();
        var pathInfo = request.getPathInfo();
        var path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
        if (!path.isBlank()) {
            return path;
        }
        var contextPath = request.getContextPath();
        var requestUri = request.getRequestURI();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private String canonicalForm(Map<String, String> form) {
        var sorted = new TreeMap<>(form);
        var builder = new StringBuilder();
        sorted.forEach((key, value) -> builder.append(key).append(value == null ? "" : value));
        return builder.toString();
    }

    private String hmacSha1(String value) {
        try {
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to validate " + providerLabel + " webhook signature", exception);
        }
    }
}
