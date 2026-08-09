package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WhopWebhookInboxTest {
    private static final byte[] SECRET = "whsec_whop-test-secret".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private final BillingProviderEventRepository events = mock(BillingProviderEventRepository.class);
    private final WhopWebhookInbox inbox = new WhopWebhookInbox(events, new ObjectMapper(),
            new String(SECRET, StandardCharsets.UTF_8), "biz_sauti", Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void verifiesStandardWebhookAndPersistsByEventId() throws Exception {
        var id = "msg_test_1";
        var timestamp = Long.toString(NOW.getEpochSecond());
        var payload = "{\"id\":\"msg_test_1\",\"type\":\"membership.activated\","
                + "\"company_id\":\"biz_sauti\",\"data\":{}}";
        when(events.findByProviderAndPayloadHash(any(), any())).thenReturn(Optional.empty());

        inbox.receive(payload, id, timestamp, "v1," + signature(id, timestamp, payload));

        verify(events).saveAndFlush(any(BillingProviderEvent.class));
    }

    @Test
    void rejectsOldOrWrongCompanyWebhook() throws Exception {
        var payload = "{\"id\":\"msg_test_2\",\"type\":\"payment.succeeded\","
                + "\"company_id\":\"biz_other\",\"data\":{}}";
        var old = Long.toString(NOW.minusSeconds(301).getEpochSecond());
        assertThatThrownBy(() -> inbox.receive(payload, "msg_test_2", old,
                "v1," + signature("msg_test_2", old, payload))).isInstanceOf(SecurityException.class);

        var current = Long.toString(NOW.getEpochSecond());
        assertThatThrownBy(() -> inbox.receive(payload, "msg_test_2", current,
                "v1," + signature("msg_test_2", current, payload))).isInstanceOf(SecurityException.class);
    }

    private String signature(String id, String timestamp, String payload) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET, "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(
                (id + "." + timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }
}
