package com.sauti.tool;

import java.net.InetAddress;
import java.net.URI;
import org.springframework.stereotype.Component;

@Component
public class WebhookDestinationValidator {
    public void validateHttpsPublicUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("webhookUrl is required");
        }
        try {
            var uri = URI.create(rawUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("webhookUrl must use HTTPS");
            }
            var host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("webhookUrl host is required");
            }
            validatePublicHost(host);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid webhookUrl", exception);
        }
    }

    public void validatePublicHost(String host) {
        try {
            for (var address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("webhookUrl host is not allowed");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid webhookUrl host", exception);
        }
    }
}
