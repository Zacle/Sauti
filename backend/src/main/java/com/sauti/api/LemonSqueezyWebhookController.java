package com.sauti.api;

import com.sauti.billing.LemonSqueezyWebhookInbox;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/lemon-squeezy")
public class LemonSqueezyWebhookController {
    private final LemonSqueezyWebhookInbox inbox;

    public LemonSqueezyWebhookController(LemonSqueezyWebhookInbox inbox) {
        this.inbox = inbox;
    }

    @PostMapping
    ResponseEntity<Void> receive(@RequestBody String payload,
                                 @RequestHeader(value = "X-Signature", required = false) String signature) {
        try {
            inbox.receive(payload, signature);
            return ResponseEntity.ok().build();
        } catch (SecurityException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
