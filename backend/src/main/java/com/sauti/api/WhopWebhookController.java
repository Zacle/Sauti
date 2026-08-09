package com.sauti.api;

import com.sauti.billing.WhopWebhookInbox;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/whop")
public class WhopWebhookController {
    private final WhopWebhookInbox inbox;

    public WhopWebhookController(WhopWebhookInbox inbox) { this.inbox = inbox; }

    @PostMapping
    ResponseEntity<Void> receive(
            @RequestBody String payload,
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
            @RequestHeader(value = "webhook-signature", required = false) String signature) {
        try {
            inbox.receive(payload, webhookId, timestamp, signature);
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
