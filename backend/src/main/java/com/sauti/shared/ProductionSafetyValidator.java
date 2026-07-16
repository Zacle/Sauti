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
        rejectValue(errors, "sauti.telephony.provider", List.of("fake"));
        requireFalse(errors, "sauti.auth.expose-dev-tokens");
        requireFalse(errors, "spring.h2.console.enabled");
        requirePrefix(errors, "spring.datasource.url", "jdbc:postgresql:");
        requirePrefix(errors, "sauti.dashboard.base-url", "https://");
        requirePrefix(errors, "sauti.web-voice.public-websocket-base-url", "wss://");
        validateOrigins(errors);
        validateProviderSignatures(errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Unsafe production configuration: " + String.join("; ", errors));
        }
    }

    private void validateOrigins(List<String> errors) {
        var value = property("sauti.cors.allowed-origins");
        if (value.isBlank()) {
            errors.add("sauti.cors.allowed-origins is required");
            return;
        }
        Arrays.stream(value.split(",")).map(String::trim).forEach(origin -> {
            if (!origin.startsWith("https://") || origin.contains("*") || origin.contains("localhost")) {
                errors.add("sauti.cors.allowed-origins must contain only explicit HTTPS origins");
            }
        });
    }

    private void validateProviderSignatures(List<String> errors) {
        switch (property("sauti.telephony.provider")) {
            case "twilio" -> requireTrue(errors, "sauti.twilio.validate-signature");
            case "signalwire" -> requireTrue(errors, "sauti.signalwire.validate-signature");
            case "telnyx" -> requireTrue(errors, "sauti.telnyx.validate-signature");
            default -> { }
        }
        if (!property("sauti.whatsapp.app-secret").isBlank()) {
            requireTrue(errors, "sauti.whatsapp.validate-signature");
        }
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
