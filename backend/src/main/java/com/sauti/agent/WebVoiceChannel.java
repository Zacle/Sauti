package com.sauti.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Website voice calls: whether they are on, which sites may embed them, and whether callers consent first. */
@Embeddable
public class WebVoiceChannel {
    @Column(name = "web_voice_enabled", nullable = false)
    private boolean enabled;

    /** The id used in the public embed snippet, so the agent id is never exposed. */
    @Column(name = "web_voice_public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "web_voice_allowed_origins")
    private String allowedOrigins;

    @Column(name = "web_voice_require_consent", nullable = false)
    private boolean requireConsent = true;

    protected WebVoiceChannel() {
    }

    static WebVoiceChannel newChannel() {
        var channel = new WebVoiceChannel();
        channel.publicId = UUID.randomUUID().toString();
        return channel;
    }

    void configure(Boolean enabled, List<String> origins, Boolean requireConsent) {
        this.enabled = enabled != null && enabled;
        var cleaned = origins == null ? List.<String>of() : origins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .distinct()
                .toList();
        this.allowedOrigins = cleaned.isEmpty() ? null : String.join(",", cleaned);
        this.requireConsent = requireConsent == null || requireConsent;
        if (publicId == null || publicId.isBlank()) publicId = UUID.randomUUID().toString();
    }

    public boolean isEnabled() { return enabled; }
    public String getPublicId() { return publicId; }
    public boolean isRequireConsent() { return requireConsent; }

    public List<String> getAllowedOrigins() {
        return allowedOrigins == null || allowedOrigins.isBlank() ? List.of()
                : Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }
}
