package com.sauti.api;

import com.sauti.telnyx.TelnyxCallControlService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/webhooks/telnyx")
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "telnyx")
public class TelnyxWebhookController {
    private final TelnyxSignatureValidator signatureValidator;
    private final TelnyxCallControlService callControlService;

    public TelnyxWebhookController(
            TelnyxSignatureValidator signatureValidator,
            TelnyxCallControlService callControlService
    ) {
        this.signatureValidator = signatureValidator;
        this.callControlService = callControlService;
    }

    @PostMapping("/call-control")
    @ResponseStatus(HttpStatus.OK)
    void callControl(
            @RequestBody String payload,
            @RequestHeader(name = "telnyx-timestamp", required = false) String timestamp,
            @RequestHeader(name = "telnyx-signature-ed25519", required = false) String signature
    ) {
        if (!signatureValidator.isValid(payload, timestamp, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Telnyx webhook signature");
        }
        callControlService.accept(payload);
    }
}
