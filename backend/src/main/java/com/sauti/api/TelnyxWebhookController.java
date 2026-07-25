package com.sauti.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.sauti.call.ManagedVoiceToolService;
import com.sauti.telnyx.TelnyxCallControlService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
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
    private final ManagedVoiceToolService toolService;
    private final String toolWebhookSecret;

    public TelnyxWebhookController(
            TelnyxSignatureValidator signatureValidator,
            TelnyxCallControlService callControlService,
            ManagedVoiceToolService toolService,
            @Value("${sauti.telnyx.tool-webhook-secret:}") String toolWebhookSecret
    ) {
        this.signatureValidator = signatureValidator;
        this.callControlService = callControlService;
        this.toolService = toolService;
        this.toolWebhookSecret = toolWebhookSecret == null ? "" : toolWebhookSecret.trim();
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

    @PostMapping("/tools/{toolName}")
    Map<String, Object> tool(
            @org.springframework.web.bind.annotation.PathVariable String toolName,
            @org.springframework.web.bind.annotation.RequestParam String callSid,
            @RequestHeader(name = "x-sauti-tool-secret", required = false) String suppliedSecret,
            @RequestHeader(name = "x-telnyx-tool-call-id", required = false) String invocationId,
            @RequestBody JsonNode payload
    ) {
        if (toolWebhookSecret.isBlank() || !constantTimeEquals(toolWebhookSecret, suppliedSecret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Telnyx tool credential");
        }
        return toolService.executeTelnyxWebhook(callSid, invocationId, toolName, payload);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }
}
