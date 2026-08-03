package com.sauti.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TwoCheckoutLcnInboxTest {
    private static final String SECRET = "AABBCCDDEEFF";
    private final BillingProviderEventRepository events = mock(BillingProviderEventRepository.class);
    private final TwoCheckoutLcnInbox inbox = new TwoCheckoutLcnInbox(events, new ObjectMapper(), SECRET);

    @Test
    void validatesSha256PersistsOnceAndReturnsRequiredReceipt() throws Exception {
        var unsigned = "LICENSE_CODE=3C343D0FAF&EXPIRATION_DATE=2026-09-03"
                + "&EXTERNAL_CUSTOMER_REFERENCE=signed-ref&STATUS=ACTIVE"
                + "&LICENSE_PRODUCT_CODE=launch-monthly&DISPATCH_REASON=LICENCE_CHANGE";
        var payload = unsigned + "&SIGNATURE_SHA2_256=" + signature(unsigned);
        when(events.findByProviderAndPayloadHash(any(), any())).thenReturn(Optional.empty());

        var receipt = inbox.receive(payload);

        assertThat(receipt).matches("<sig algo=\"sha256\" date=\"[0-9]{14}\">[0-9a-f]{64}</sig>");
        verify(events).saveAndFlush(any(BillingProviderEvent.class));
    }

    @Test
    void rejectsAlteredNotificationBeforePersistence() {
        var payload = "LICENSE_CODE=changed&EXPIRATION_DATE=2026-09-03&STATUS=ACTIVE"
                + "&SIGNATURE_SHA2_256=" + "0".repeat(64);

        assertThatThrownBy(() -> inbox.receive(payload)).isInstanceOf(SecurityException.class);
    }

    private String signature(String rawPayload) throws Exception {
        var form = TwoCheckoutLcnInbox.parse(rawPayload);
        var source = new StringBuilder();
        for (var entry : form.ordered()) {
            var bytes = entry.value().getBytes(StandardCharsets.UTF_8);
            source.append(bytes.length).append(entry.value());
        }
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(source.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
