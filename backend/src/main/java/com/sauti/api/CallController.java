package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.call.BrowserVoiceRuntimePreparationService;
import com.sauti.call.CallDtos.CallResponse;
import com.sauti.call.CallDtos.CallTurnResponse;
import com.sauti.call.CallDtos.CompleteTestCallRequest;
import com.sauti.call.CallDtos.ProviderCallCorrelationRequest;
import com.sauti.call.CallDtos.SimulatedTurnRequest;
import com.sauti.call.CallDtos.SimulatedTurnResponse;
import com.sauti.call.CallDtos.StartTestCallRequest;
import com.sauti.call.CallDtos.StartTestCallResponse;
import com.sauti.call.CallDtos.StartupLatencyRequest;
import com.sauti.call.CallDtos.TestCallSettings;
import com.sauti.call.BrowserVoiceRuntimeSession;
import com.sauti.call.TelnyxAiBrowserVoiceRuntimeService;
import com.sauti.call.CallPipelineService;
import com.sauti.call.CallQueryService;
import com.sauti.call.CallRecordingService;
import com.sauti.call.WebVoiceTokenService;
import com.sauti.reliability.VoiceStartupMeasurementService;
import com.sauti.call.RealtimeDtos.RealtimeTranscriptRequest;
import com.sauti.call.RealtimeDtos.RealtimeTranscriptResponse;
import com.sauti.billing.BillingAccessPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calls")
public class CallController {
    private final CallQueryService callQueryService;
    private final CallPipelineService callPipelineService;
    private final CallRecordingService callRecordingService;
    private final WebVoiceTokenService webVoiceTokenService;
    private final TelnyxAiBrowserVoiceRuntimeService telnyxRuntime;
    private final BrowserVoiceRuntimePreparationService runtimePreparation;
    private final VoiceStartupMeasurementService startupMeasurements;
    private final BillingAccessPolicy billingAccess;

    public CallController(
            CallQueryService callQueryService,
            CallPipelineService callPipelineService,
            CallRecordingService callRecordingService,
            WebVoiceTokenService webVoiceTokenService,
            TelnyxAiBrowserVoiceRuntimeService telnyxRuntime,
            BrowserVoiceRuntimePreparationService runtimePreparation,
            VoiceStartupMeasurementService startupMeasurements,
            BillingAccessPolicy billingAccess
    ) {
        this.callQueryService = callQueryService;
        this.callPipelineService = callPipelineService;
        this.callRecordingService = callRecordingService;
        this.webVoiceTokenService = webVoiceTokenService;
        this.telnyxRuntime = telnyxRuntime;
        this.runtimePreparation = runtimePreparation;
        this.startupMeasurements = startupMeasurements;
        this.billingAccess = billingAccess;
    }

    @GetMapping
    List<CallResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return callQueryService.list(user.tenantId()).stream().map(CallResponse::from).toList();
    }

    @GetMapping("/{id}")
    CallResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return CallResponse.from(callQueryService.get(user.tenantId(), id));
    }

    @PostMapping("/test")
    StartTestCallResponse startTestCall(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody StartTestCallRequest request
    ) {
        billingAccess.requirePaidCommunication(user.tenantId());
        requireTelnyx();
        var call = callPipelineService.startTestCall(
                user.tenantId(), request.agentId(), request.ttsVoiceId(), request.language()
        );
        var greeting = callQueryService.firstAgentResponse(user.tenantId(), call.getId());
        var agentKey = call.getAgent().getId().toString();
        // Managed providers use this same call-scoped credential for server
        // callbacks, so it must remain valid through call cleanup.
        var token = webVoiceTokenService.issue(
                call.getTwilioCallSid(), agentKey, call.getAgent().getMaxCallDurationSeconds() + 120L);
        var runtime = telnyxRuntime.prepare(call, greeting, token);
        return new StartTestCallResponse(
                CallResponse.from(call),
                greeting,
                TestCallSettings.from(call.getAgent()),
                runtime
        );
    }

    @PostMapping("/test/runtime")
    BrowserVoiceRuntimeSession prepareTestRuntime(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody StartTestCallRequest request
    ) {
        billingAccess.requirePaidCommunication(user.tenantId());
        requireTelnyx();
        return runtimePreparation.prepare(
                user.tenantId(),
                request.agentId(),
                request.ttsVoiceId(),
                request.language()
        );
    }

    private void requireTelnyx() {
        if (!telnyxRuntime.isConfigured()) {
            throw new com.sauti.call.VoiceRuntimeUnavailableException(
                    "Telnyx test calls are not configured in the running backend. "
                            + "Set TELNYX_API_KEY, PUBLIC_BASE_URL, and TELNYX_TOOL_WEBHOOK_SECRET, then restart it."
            );
        }
    }

    @PostMapping("/{id}/realtime/transcript")
    RealtimeTranscriptResponse recordRealtimeTranscript(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody RealtimeTranscriptRequest request
    ) {
        callPipelineService.recordRealtimeTranscript(user.tenantId(), id, request.role(), request.text(), request.interrupted());
        if (!"caller".equalsIgnoreCase(request.role())) return new RealtimeTranscriptResponse("");
        // Realtime already has the stable agent prompt and conversation. This
        // endpoint is the durable transcript/analytics write, not a synchronous
        // prompt-rebuild gate in front of the next spoken response.
        return new RealtimeTranscriptResponse("");
    }

    @PostMapping("/{twilioCallSid}/simulate-turn")
    SimulatedTurnResponse simulateTurn(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String twilioCallSid,
            @RequestBody SimulatedTurnRequest request
    ) {
        return callPipelineService.processTextTurn(user.tenantId(), twilioCallSid, request.transcript());
    }

    @PostMapping("/{id}/complete-test")
    CallResponse completeTestCall(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody(required = false) CompleteTestCallRequest request
    ) {
        var outcome = request == null ? "completed" : request.outcome();
        var providerCallControlId = request == null ? "" : request.providerCallControlId();
        var providerCallLegId = request == null ? "" : request.providerCallLegId();
        return CallResponse.from(callPipelineService.completeTestCall(
                user.tenantId(), id, outcome, providerCallControlId, providerCallLegId
        ));
    }

    @PostMapping("/{id}/provider-correlation")
    CallResponse correlateTestCall(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ProviderCallCorrelationRequest request
    ) {
        return CallResponse.from(callPipelineService.correlateTestCall(
                user.tenantId(), id, request.providerCallControlId(), request.providerCallLegId()
        ));
    }

    @PostMapping("/{id}/startup-latency")
    void recordStartupLatency(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StartupLatencyRequest request
    ) {
        startupMeasurements.recordTestCall(user.tenantId(), id, request.latencyMs());
    }

    @GetMapping("/{id}/turns")
    List<CallTurnResponse> turns(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return callQueryService.turns(user.tenantId(), id).stream()
                .map(CallTurnResponse::from)
                .toList();
    }

    @PostMapping(value = "/{id}/recording", consumes = {"audio/webm", "application/octet-stream"})
    CallResponse saveRecording(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody byte[] audio
    ) {
        return CallResponse.from(callRecordingService.save(user.tenantId(), id, audio));
    }

    @GetMapping("/{id}/recording")
    ResponseEntity<byte[]> recording(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        var recording = callRecordingService.read(user.tenantId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(recording.mediaType()))
                .cacheControl(CacheControl.noStore())
                .body(recording.bytes());
    }
}
