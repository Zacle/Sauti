package com.sauti.call;

import java.util.Map;

/**
 * Provider-neutral bootstrap data for an externally orchestrated browser voice call.
 * The opaque configuration is interpreted only by the matching dashboard adapter.
 */
public record BrowserVoiceRuntimeSession(
        String provider,
        String clientToken,
        String apiBaseUrl,
        Map<String, Object> configuration
) {
}
