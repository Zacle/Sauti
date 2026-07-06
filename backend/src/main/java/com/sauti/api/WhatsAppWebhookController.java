package com.sauti.api;

import com.sauti.whatsapp.WhatsAppChannelService;
import com.sauti.whatsapp.WhatsAppSignatureValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/webhooks/whatsapp")
public class WhatsAppWebhookController {
    private final WhatsAppChannelService channelService;
    private final WhatsAppSignatureValidator signatureValidator;
    private final String verifyToken;

    public WhatsAppWebhookController(
            WhatsAppChannelService channelService,
            WhatsAppSignatureValidator signatureValidator,
            @Value("${sauti.whatsapp.verify-token:}") String verifyToken
    ) {
        this.channelService = channelService;
        this.signatureValidator = signatureValidator;
        this.verifyToken = verifyToken == null ? "" : verifyToken;
    }

    @GetMapping
    String verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String suppliedToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if (!"subscribe".equals(mode)
                || verifyToken.isBlank()
                || !verifyToken.equals(suppliedToken)
                || challenge == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "WhatsApp webhook verification failed");
        }
        return challenge;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    void receive(
            @RequestBody String payload,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature
    ) {
        if (!signatureValidator.isValid(payload, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid WhatsApp webhook signature");
        }
        channelService.accept(payload);
    }
}
