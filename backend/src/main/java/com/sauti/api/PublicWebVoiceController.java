package com.sauti.api;

import com.sauti.agent.Agent;
import com.sauti.call.CallPipelineService;
import com.sauti.call.PublicWebVoiceAccessService;
import com.sauti.call.PublicWebVoiceRateLimitService;
import com.sauti.call.RealtimeDtos.RealtimeTranscriptRequest;
import com.sauti.call.RealtimeDtos.RealtimeTranscriptResponse;
import com.sauti.call.WebVoiceDtos.PublicAgentResponse;
import com.sauti.call.WebVoiceDtos.CompleteWebVoiceSessionRequest;
import com.sauti.call.WebVoiceDtos.StartWebVoiceSessionRequest;
import com.sauti.call.WebVoiceDtos.StartWebVoiceSessionResponse;
import com.sauti.call.WebVoiceTokenService;
import com.sauti.call.TelnyxAiBrowserVoiceRuntimeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/public/web-voice")
public class PublicWebVoiceController {
    private final PublicWebVoiceAccessService accessService;
    private final PublicWebVoiceRateLimitService rateLimitService;
    private final CallPipelineService callPipelineService;
    private final WebVoiceTokenService tokenService;
    private final TelnyxAiBrowserVoiceRuntimeService telnyxRuntime;

    public PublicWebVoiceController(
            PublicWebVoiceAccessService accessService,
            PublicWebVoiceRateLimitService rateLimitService,
            CallPipelineService callPipelineService,
            WebVoiceTokenService tokenService,
            TelnyxAiBrowserVoiceRuntimeService telnyxRuntime
    ) {
        this.accessService = accessService;
        this.rateLimitService = rateLimitService;
        this.callPipelineService = callPipelineService;
        this.tokenService = tokenService;
        this.telnyxRuntime = telnyxRuntime;
    }

    @GetMapping("/agents/{publicId}")
    PublicAgentResponse agent(@PathVariable String publicId) {
        var agent = publicAgent(publicId);
        return new PublicAgentResponse(
                agent.getWebVoicePublicId(),
                agent.getName(),
                agent.getDescription(),
                agent.getDefaultLanguage(),
                agent.getSupportedLanguages(),
                agent.isWebVoiceRequireConsent() || agent.isRecordCalls(),
                agent.isRecordCalls()
        );
    }

    @PostMapping("/agents/{publicId}/sessions")
    StartWebVoiceSessionResponse start(
            @PathVariable String publicId,
            @RequestBody(required = false) StartWebVoiceSessionRequest request,
            HttpServletRequest httpRequest
    ) {
        rateLimitService.checkSessionStart(publicId, clientAddress(httpRequest));
        var agent = publicAgent(publicId);
        validateSessionRequest(agent, request);
        if (!telnyxRuntime.isConfigured()) {
            throw new com.sauti.call.VoiceRuntimeUnavailableException(
                    "Telnyx Web Voice is not configured in the running backend."
            );
        }
        var preferredLanguage = request == null ? null : request.preferredLanguage();
        var call = callPipelineService.startWebCall(publicId, preferredLanguage);
        var token = tokenService.issue(
                call.getTwilioCallSid(),
                publicId,
                call.getAgent().getMaxCallDurationSeconds() + 120L
        );
        var greeting = callPipelineService.openingGreeting(call);
        var runtime = telnyxRuntime.prepare(call, greeting, token);
        return new StartWebVoiceSessionResponse(
                call.getId(),
                call.getTwilioCallSid(),
                token,
                greeting,
                call.getLanguageDetected(),
                runtime
        );
    }

    @PostMapping("/sessions/{sessionId}/realtime/transcript")
    RealtimeTranscriptResponse recordRealtimeTranscript(
            @PathVariable String sessionId,
            @RequestBody RealtimeTranscriptRequest transcript,
            HttpServletRequest request
    ) {
        var call = verifiedPublicCall(sessionId, request);
        callPipelineService.recordRealtimeTranscript(
                call.getTenant().getId(),
                call.getId(),
                transcript.role(),
                transcript.text(),
                transcript.interrupted()
        );
        return new RealtimeTranscriptResponse("");
    }

    @PostMapping("/sessions/{sessionId}/complete")
    void completeSession(
            @PathVariable String sessionId,
            @RequestBody(required = false) CompleteWebVoiceSessionRequest completion,
            HttpServletRequest request
    ) {
        var principal = verifyBearer(request);
        if (!sessionId.equals(principal.callSid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Web Voice session token");
        }
        var call = accessService.requireCall(sessionId, principal.publicAgentId());
        if (call.isActive()) {
            var providerCallControlId = call.getAgent().isRecordCalls() && completion != null
                    ? completion.providerCallControlId()
                    : "";
            callPipelineService.completeActiveCall(sessionId, "completed", providerCallControlId);
        }
    }

    private void validateSessionRequest(Agent agent, StartWebVoiceSessionRequest request) {
        if ((agent.isWebVoiceRequireConsent() || agent.isRecordCalls())
                && (request == null || !request.consentAccepted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Microphone consent is required");
        }
        if (!agent.getWebVoiceAllowedOrigins().isEmpty()) {
            var origin = request == null ? "" : request.origin();
            if (origin == null || agent.getWebVoiceAllowedOrigins().stream().noneMatch(origin::equalsIgnoreCase)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "This website is not allowed to start Web Voice sessions"
                );
            }
        }
        var preferredLanguage = request == null ? null : request.preferredLanguage();
        if (preferredLanguage != null
                && !preferredLanguage.isBlank()
                && !agent.getSupportedLanguages().contains(
                        preferredLanguage.toLowerCase(java.util.Locale.ROOT)
                )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested language is not supported by this agent"
            );
        }
    }

    private Agent publicAgent(String publicId) {
        return accessService.requirePublicAgent(publicId);
    }

    private com.sauti.call.Call verifiedPublicCall(String sessionId, HttpServletRequest request) {
        var principal = verifyBearer(request);
        if (!sessionId.equals(principal.callSid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Web Voice session token");
        }
        return accessService.requireActiveCall(sessionId, principal.publicAgentId());
    }

    private String clientAddress(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }

    private WebVoiceTokenService.WebVoicePrincipal verifyBearer(HttpServletRequest request) {
        var authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Web Voice session token is required"
            );
        }
        try {
            return tokenService.verify(authorization.substring("Bearer ".length()).trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Web Voice session token");
        }
    }
}
