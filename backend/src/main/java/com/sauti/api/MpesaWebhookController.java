package com.sauti.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sauti.integration.DuringCallIntegrationFulfillment;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/mpesa")
public class MpesaWebhookController {
    private final DuringCallIntegrationFulfillment fulfillment;
    public MpesaWebhookController(DuringCallIntegrationFulfillment fulfillment) { this.fulfillment = fulfillment; }

    @PostMapping("/{connectionId}")
    Map<String, Boolean> callback(@PathVariable UUID connectionId, @RequestBody JsonNode payload) {
        fulfillment.callback(connectionId, payload);
        return Map.of("accepted", true);
    }
}
