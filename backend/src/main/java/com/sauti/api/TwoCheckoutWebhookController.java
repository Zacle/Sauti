package com.sauti.api;

import com.sauti.billing.TwoCheckoutLcnInbox;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/2checkout")
public class TwoCheckoutWebhookController {
    private final TwoCheckoutLcnInbox inbox;

    public TwoCheckoutWebhookController(TwoCheckoutLcnInbox inbox) {
        this.inbox = inbox;
    }

    @PostMapping(value = "/lcn", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> receiveLcn(@RequestBody String payload) {
        try {
            return ResponseEntity.ok(inbox.receive(payload));
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
