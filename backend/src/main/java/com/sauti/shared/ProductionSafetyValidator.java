package com.sauti.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails production startup before an insecure or development-only configuration can serve traffic. */
@Component
@Profile("production")
public class ProductionSafetyValidator implements ApplicationRunner {
    private final Environment environment;

    public ProductionSafetyValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        validate();
    }

    public void validate() {
        var errors = new ArrayList<String>();
        requireSecret(errors, "sauti.jwt.secret", 32);
        requireSecret(errors, "sauti.web-voice.token-secret", 32);
        requireSecret(errors, "sauti.tools.encryption-key", 32);
        requireSecret(errors, "sauti.webhooks.signing-secret", 24);
        requireValue(errors, "sauti.providers.mode", List.of("live"));
        rejectValue(errors, "sauti.llm.provider", List.of("fake", "heuristic"));
        requireValue(errors, "sauti.telephony.provider", List.of("telnyx"));
        requireFalse(errors, "sauti.auth.expose-dev-tokens");
        requireFalse(errors, "sauti.auth.public-registration-enabled");
        requireFalse(errors, "spring.h2.console.enabled");
        requirePrefix(errors, "spring.datasource.url", "jdbc:postgresql:");
        requirePrefix(errors, "sauti.dashboard.base-url", "https://");
        requireValue(errors, "server.forward-headers-strategy", List.of("native"));
        validateOrigins(errors);
        validateWebSocketOrigins(errors);
        validateProviderSignatures(errors);
        validatePublicDemo(errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", errors));
        }
    }

    private void validateOrigins(List<String> errors) {
        validateExplicitHttpsOrigins(errors, "sauti.cors.allowed-origins");
    }

    private void validateWebSocketOrigins(List<String> errors) {
        validateExplicitHttpsOrigins(errors, "sauti.websocket.allowed-origin-patterns");
    }

    private void validateExplicitHttpsOrigins(List<String> errors, String key) {
        var value = property(key);
        if (value.isBlank()) {
            errors.add(key + " is required");
            return;
        }
        Arrays.stream(value.split(",")).map(String::trim).forEach(origin -> {
            if (!origin.startsWith("https://") || origin.contains("*") || origin.contains("localhost")) {
                errors.add(key + " must contain only explicit HTTPS origins");
            }
        });
    }

    private void validateProviderSignatures(List<String> errors) {
        switch (property("sauti.telephony.provider")) {
            case "telnyx" -> requireTrue(errors, "sauti.telnyx.validate-signature");
            default -> { }
        }
        if (!property("sauti.whatsapp.app-secret").isBlank()) {
            requireTrue(errors, "sauti.whatsapp.validate-signature");
        }
    }

    private void validatePublicDemo(List<String> errors) {
        if (!environment.getProperty("sauti.public-demo.enabled", Boolean.class, false)) return;
        if (property("sauti.public-demo.telnyx-agent-id").isBlank()) {
            errors.add("sauti.public-demo.telnyx-agent-id is required when the public demo is enabled");
        }
        var origins = property("sauti.public-demo.allowed-origins");
        if (origins.isBlank()) {
            errors.add("sauti.public-demo.allowed-origins is required when the public demo is enabled");
            return;
        }
        Arrays.stream(origins.split(",")).map(String::trim).forEach(origin -> {
            if (!origin.startsWith("https://") || origin.contains("*") || origin.contains("localhost")) {
                errors.add("sauti.public-demo.allowed-origins must contain only explicit HTTPS origins");
            }
        });
    }

    private void requireSecret(List<String> errors, String key, int minimumLength) {
        var value = property(key);
        var normalized = value.toLowerCase();
        if (value.length() < minimumLength
                || normalized.contains("dev-only")
                || normalized.contains("replace-with")
                || normalized.contains("change-me")) {
            errors.add(key + " must be a non-placeholder secret of at least " + minimumLength + " characters");
        }
    }

    private void requireValue(List<String> errors, String key, List<String> accepted) {
        if (!accepted.contains(property(key))) errors.add(key + " must be one of " + accepted);
    }

    private void rejectValue(List<String> errors, String key, List<String> rejected) {
        var value = property(key);
        if (value.isBlank() || rejected.contains(value)) errors.add(key + " must select a live provider");
    }

    private void requirePrefix(List<String> errors, String key, String prefix) {
        if (!property(key).startsWith(prefix)) errors.add(key + " must start with " + prefix);
    }

    private void requireTrue(List<String> errors, String key) {
        if (!environment.getProperty(key, Boolean.class, false)) errors.add(key + " must be true");
    }

    private void requireFalse(List<String> errors, String key) {
        if (environment.getProperty(key, Boolean.class, true)) errors.add(key + " must be false");
    }

    private String property(String key) {
        return environment.getProperty(key, "").trim().toLowerCase();
    }
}
