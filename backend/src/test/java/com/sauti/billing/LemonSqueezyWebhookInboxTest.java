package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class LemonSqueezyWebhookInboxTest {
    private static final String SECRET = "test-signing-secret";
    private final BillingProviderEventRepository events = mock(BillingProviderEventRepository.class);
    private final LemonSqueezyWebhookInbox inbox = new LemonSqueezyWebhookInbox(events, new ObjectMapper(), SECRET);

    @Test
    void verifiesSignatureAndStoresEachPayloadOnce() throws Exception {
        var payload = "{\"meta\":{\"event_name\":\"subscription_created\"},\"data\":{}}";
        when(events.findByPayloadHash(any())).thenReturn(Optional.empty(), Optional.of(mock(BillingProviderEvent.class)));

        inbox.receive(payload, signature(payload));
        inbox.receive(payload, signature(payload));

        verify(events, times(1)).saveAndFlush(any(BillingProviderEvent.class));
    }

    @Test
    void rejectsInvalidSignatureBeforePersistence() {
        var payload = "{\"meta\":{\"event_name\":\"subscription_created\"}}";

        assertThatThrownBy(() -> inbox.receive(payload, "invalid"))
                .isInstanceOf(SecurityException.class);
    }

    private String signature(String payload) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
