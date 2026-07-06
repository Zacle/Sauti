package com.sauti.api;

import com.sauti.agent.TelephonyProvider;
import com.sauti.call.CallPipelineService;
import com.sauti.call.CallTransferService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/signalwire")
@ConditionalOnProperty(name = "sauti.telephony.provider", havingValue = "signalwire")
public class SignalWireWebhookController {
    private final CallPipelineService callPipelineService;
    private final CallTransferService callTransferService;
    private final TelephonyProvider telephonyProvider;
    private final SignalWireSignatureValidator signatureValidator;
    private final String publicBaseUrl;

    public SignalWireWebhookController(
            CallPipelineService callPipelineService,
            CallTransferService callTransferService,
            TelephonyProvider telephonyProvider,
            SignalWireSignatureValidator signatureValidator,
            @Value("${sauti.signalwire.public-base-url:${sauti.twilio.public-base-url}}") String publicBaseUrl
    ) {
        this.callPipelineService = callPipelineService;
        this.callTransferService = callTransferService;
        this.telephonyProvider = telephonyProvider;
        this.signatureValidator = signatureValidator;
        this.publicBaseUrl = publicBaseUrl;
    }

    @PostMapping(value = "/voice", produces = MediaType.APPLICATION_XML_VALUE)
    String inboundVoice(
            HttpServletRequest request,
            @RequestHeader(value = "X-SignalWire-Signature", required = false) String signature,
            @RequestParam Map<String, String> form
    ) {
        if (!signatureValidator.isValid(request, signature, form)) {
            throw new IllegalArgumentException("Invalid SignalWire signature");
        }
        var to = form.getOrDefault("To", "");
        var from = form.getOrDefault("From", "");
        var callSid = form.getOrDefault("CallSid", "dev-call");
        var call = callPipelineService.startInboundCall(to, callSid, from);
        var wsUrl = publicBaseUrl.replace("https://", "wss://").replace("http://", "ws://")
                + "/ws/twilio/media/" + call.getTwilioCallSid();
        return telephonyProvider.buildMediaStreamTwiMl(
                wsUrl,
                call.getTwilioCallSid(),
                call.getTenant().getId().toString(),
                call.getAgent().getId().toString(),
                call.getAgent().isRecordCalls()
        );
    }

    @PostMapping(value = "/transfer/{callId}", produces = MediaType.APPLICATION_XML_VALUE)
    String transferResult(
            HttpServletRequest request,
            @RequestHeader(value = "X-SignalWire-Signature", required = false) String signature,
            @PathVariable UUID callId,
            @RequestParam Map<String, String> form
    ) {
        if (!signatureValidator.isValid(request, signature, form)) {
            throw new IllegalArgumentException("Invalid SignalWire signature");
        }
        var result = callTransferService.handleDialResult(
                callId,
                form.getOrDefault("DialCallStatus", "failed"),
                form.getOrDefault("DialCallSid", "")
        );
        if (result.connected()) {
            callPipelineService.completeActiveCall(result.twilioCallSid(), "transferred");
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Hangup/></Response>";
        }
        var wsUrl = publicBaseUrl.replace("https://", "wss://").replace("http://", "ws://")
                + "/ws/twilio/media/" + result.twilioCallSid();
        return telephonyProvider.buildMediaStreamTwiMl(
                wsUrl,
                result.twilioCallSid(),
                result.tenantId().toString(),
                result.agentId().toString(),
                false,
                Map.of("transferFallback", result.status())
        );
    }

    @PostMapping("/status")
    void statusCallback(
            HttpServletRequest request,
            @RequestHeader(value = "X-SignalWire-Signature", required = false) String signature,
            @RequestParam Map<String, String> form
    ) {
        if (!signatureValidator.isValid(request, signature, form)) {
            throw new IllegalArgumentException("Invalid SignalWire signature");
        }
        callPipelineService.updateTwilioStatus(
                form.getOrDefault("CallSid", ""),
                form.getOrDefault("CallStatus", ""),
                parseInt(form.getOrDefault("CallDuration", form.get("RecordingDuration"))),
                form.getOrDefault("RecordingUrl", ""),
                form.getOrDefault("RecordingSid", "")
        );
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
