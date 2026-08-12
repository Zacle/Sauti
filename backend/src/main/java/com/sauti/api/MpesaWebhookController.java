package com.sauti.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sauti.integration.DuringCallIntegrationFulfillment;
import com.sauti.integration.MpesaCallbackTokenService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/webhooks/mpesa")
public class MpesaWebhookController {
    private final DuringCallIntegrationFulfillment fulfillment;
    private final MpesaCallbackTokenService callbackTokens;
    public MpesaWebhookController(
            DuringCallIntegrationFulfillment fulfillment,
            MpesaCallbackTokenService callbackTokens
    ) {
        this.fulfillment = fulfillment;
        this.callbackTokens = callbackTokens;
    }

    @PostMapping("/{connectionId}")
    Map<String, Boolean> callback(
            @PathVariable UUID connectionId,
            @RequestParam(required = false) String token,
            @RequestBody JsonNode payload
    ) {
        if (!callbackTokens.isValid(connectionId, token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid M-Pesa callback credential");
        }
        fulfillment.callback(connectionId, payload);
        return Map.of("accepted", true);
    }
}
