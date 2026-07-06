package com.sauti.api;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.call.CallPipelineService;
import com.sauti.call.WebVoiceDtos.PublicAgentResponse;
import com.sauti.call.WebVoiceDtos.StartWebVoiceSessionRequest;
import com.sauti.call.WebVoiceDtos.StartWebVoiceSessionResponse;
import com.sauti.call.WebVoiceTokenService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
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
    private final AgentRepository agentRepository;
    private final CallPipelineService callPipelineService;
    private final WebVoiceTokenService tokenService;
    private final String websocketBaseUrl;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public PublicWebVoiceController(
            AgentRepository agentRepository,
            CallPipelineService callPipelineService,
            WebVoiceTokenService tokenService,
            @Value("${sauti.web-voice.public-websocket-base-url:ws://localhost:8082}") String websocketBaseUrl
    ) {
        this.agentRepository = agentRepository;
        this.callPipelineService = callPipelineService;
        this.tokenService = tokenService;
        this.websocketBaseUrl = websocketBaseUrl.replaceFirst("/+$", "");
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
        enforceRateLimit(publicId + ":" + clientAddress(httpRequest));
        var agent = publicAgent(publicId);
        if ((agent.isWebVoiceRequireConsent() || agent.isRecordCalls())
                && (request == null || !request.consentAccepted())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Microphone consent is required");
        }
        if (!agent.getWebVoiceAllowedOrigins().isEmpty()) {
            var origin = request == null ? "" : request.origin();
            if (origin == null || agent.getWebVoiceAllowedOrigins().stream().noneMatch(origin::equalsIgnoreCase)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This website is not allowed to start Web Voice sessions");
            }
        }
        var preferredLanguage = request == null ? null : request.preferredLanguage();
        if (preferredLanguage != null
                && !preferredLanguage.isBlank()
                && !agent.getSupportedLanguages().contains(preferredLanguage.toLowerCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested language is not supported by this agent");
        }
        var call = callPipelineService.startWebCall(publicId, preferredLanguage);
        var token = tokenService.issue(call.getTwilioCallSid(), publicId);
        return new StartWebVoiceSessionResponse(
                call.getId(),
                call.getTwilioCallSid(),
                token,
                websocketBaseUrl + "/ws/web-voice/" + call.getTwilioCallSid() + "?token=" + token,
                callPipelineService.resolveGreeting(agent),
                16000,
                call.getLanguageDetected()
        );
    }

    private Agent publicAgent(String publicId) {
        return agentRepository.findByWebVoicePublicId(publicId)
                .filter(Agent::isActive)
                .filter(Agent::isWebVoiceEnabled)
                .orElseThrow(() -> new EntityNotFoundException("Web Voice agent not found"));
    }

    private void enforceRateLimit(String client) {
        var now = System.currentTimeMillis();
        if (rateWindows.size() > 10_000) {
            rateWindows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= 60_000);
        }
        rateWindows.compute(client, (key, current) -> {
            var window = current == null || now - current.startedAt() >= 60_000
                    ? new RateWindow(now, 0)
                    : current;
            if (window.count() >= 10) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many Web Voice sessions");
            }
            return new RateWindow(window.startedAt(), window.count() + 1);
        });
    }

    private String clientAddress(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }

    private record RateWindow(long startedAt, int count) {
    }
}
