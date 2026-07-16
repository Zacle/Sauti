package com.sauti.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WhatsAppSignatureValidatorTest {
    @Test
    void acceptsValidMetaSignatureAndRejectsTampering() throws Exception {
        var validator = new WhatsAppSignatureValidator("app-secret", true);
        var payload = "{\"object\":\"whatsapp_business_account\"}";
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        assertThat(validator.isValid(payload, signature)).isTrue();
        assertThat(validator.isValid(payload + "x", signature)).isFalse();
        assertThat(validator.isValid(payload, null)).isFalse();
    }

    @Test
    void allowsUnsignedLocalDevelopmentWhenValidationIsDisabled() {
        assertThat(new WhatsAppSignatureValidator("", false).isValid("{}", null)).isTrue();
    }

    @Test
    void rejectsUnsignedWebhooksWhenOptionalChannelIsNotConfigured() {
        assertThat(new WhatsAppSignatureValidator("", true).isValid("{}", null)).isFalse();
    }
}
