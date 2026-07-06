package com.sauti;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "sauti.telephony.provider=telnyx",
        "sauti.telnyx.api-key=test-key",
        "sauti.telnyx.connection-id=test-connection",
        "sauti.telnyx.validate-signature=false"
})
@AutoConfigureMockMvc
class TelnyxWebhookSecurityTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void permitsValidatedTelnyxWebhookDeliveryWithoutJwt() throws Exception {
        mvc.perform(post("/webhooks/telnyx/call-control")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "data": {
                                    "id": "event-security-test",
                                    "event_type": "streaming.started",
                                    "occurred_at": "2026-07-05T00:00:00Z",
                                    "payload": {"call_control_id": "call-control-test"}
                                  }
                                }
                                """))
                .andExpect(status().isOk());
    }
}
