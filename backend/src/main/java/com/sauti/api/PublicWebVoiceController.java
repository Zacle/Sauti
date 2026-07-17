package com.sauti.api;

import com.sauti.agent.Agent;
import com.sauti.call.BrowserSpeechToTextService;
import com.sauti.call.CallPipelineService;
import com.sauti.call.PublicWebVoiceAccessService;
import com.sauti.call.PublicWebVoiceRateLimitService;
import com.sauti.call.WebVoiceDtos.PublicAgentResponse;
import com.sauti.call.WebVoiceDtos.StartWebVoiceSessionRequest;
import com.sauti.call.WebVoiceDtos.StartWebVoiceSessionResponse;
import com.sauti.call.WebVoiceDtos.WebVoiceAudioTurnResponse;
import com.sauti.call.WebVoiceTokenService;
import com.sauti.call.OpenAiRealtimeService;
import com.sauti.call.CartesiaRealtimeTextToSpeechClient;
import com.sauti.call.RealtimeDtos.RealtimeToolRequest;
import com.sauti.call.RealtimeDtos.RealtimeTranscriptRequest;
import com.sauti.call.RealtimeDtos.RealtimeTranscriptResponse;
import com.sauti.voice.VoiceCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicWebVoiceController.class);
    private static final Set<String> TURN_BASED_LANGUAGES = Set.of("fr", "ar");
    private final PublicWebVoiceAccessService accessService;
    private final PublicWebVoiceRateLimitService rateLimitService;
    private final CallPipelineService callPipelineService;
    private final WebVoiceTokenService tokenService;
    private final BrowserSpeechToTextService speechToTextService;
    private final VoiceCatalogService voiceCatalogService;
    private final String websocketBaseUrl;
    private final OpenAiRealtimeService openAiRealtimeService;
    private final CartesiaRealtimeTextToSpeechClient cartesiaClient;

    public PublicWebVoiceController(
            PublicWebVoiceAccessService accessService,
            PublicWebVoiceRateLimitService rateLimitService,
            CallPipelineService callPipelineService,
            WebVoiceTokenService tokenService,
            BrowserSpeechToTextService speechToTextService,
            VoiceCatalogService voiceCatalogService,
            OpenAiRealtimeService openAiRealtimeService,
            CartesiaRealtimeTextToSpeechClient cartesiaClient,
            @Value("${sauti.web-voice.public-websocket-base-url:ws://localhost:8082}") String websocketBaseUrl
    ) {
        this.accessService = accessService;
        this.rateLimitService = rateLimitService;
        this.callPipelineService = callPipelineService;
        this.tokenService = tokenService;
        this.speechToTextService = speechToTextService;
        this.voiceCatalogService = voiceCatalogService;
        this.openAiRealtimeService = openAiRealtimeService;
        this.cartesiaClient = cartesiaClient;
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
        rateLimitService.checkSessionStart(publicId, clientAddress(httpRequest));
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
        var language = call.getLanguageDetected();
        var mode = realtimeMode(call);
        var websocketPath = "hybrid_realtime".equals(mode) ? "/ws/hybrid-voice/" : "/ws/web-voice/";
        var greeting = callPipelineService.openingGreeting(call);
        var greetingAudio = cachedHybridGreeting(call, greeting, language, mode);
        return new StartWebVoiceSessionResponse(
                call.getId(),
                call.getTwilioCallSid(),
                token,
                websocketBaseUrl + websocketPath + call.getTwilioCallSid() + "?token=" + token,
                greeting,
                greetingAudio,
                16000,
                language,
                mode,
                openAiRealtimeService.hasTool(call, "check_availability")
        );
    }

    private String cachedHybridGreeting(
            com.sauti.call.Call call,
            String greeting,
            String language,
            String mode
    ) {
        if (!"hybrid_realtime".equals(mode) || greeting == null || greeting.isBlank()) return null;
        try {
            var audio = CompletableFuture.supplyAsync(() ->
                            voiceCatalogService.cachedCartesiaGreeting(
                                    call.getAgent().getTtsVoiceId(), language, greeting
                            ))
                    .completeOnTimeout(null, 1500, TimeUnit.MILLISECONDS)
                    .join();
            return audio == null ? null : Base64.getEncoder().encodeToString(audio);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to prepare cached Cartesia greeting for web call={}", call.getId(), exception);
            return null;
        }
    }

    private String realtimeMode(com.sauti.call.Call call) {
        if (!openAiRealtimeService.enabled()) return "realtime";
        if (openAiRealtimeService.usesOpenAiVoice(call)) return "openai_realtime";
        if (openAiRealtimeService.usesCartesiaVoice(call) && cartesiaClient.isConfigured()) return "hybrid_realtime";
        return "realtime";
    }

    @PostMapping(value = "/sessions/{sessionId}/realtime/connect", consumes = "application/sdp", produces = "application/sdp")
    ResponseEntity<String> connectRealtime(
            @PathVariable String sessionId,
            @RequestBody String sdpOffer,
            HttpServletRequest request
    ) {
        var call = verifiedPublicCall(sessionId, request);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/sdp"))
                .body(openAiRealtimeService.createWebRtcSession(call, sdpOffer));
    }

    @PostMapping("/sessions/{sessionId}/realtime/transcript")
    RealtimeTranscriptResponse recordRealtimeTranscript(
            @PathVariable String sessionId,
            @RequestBody RealtimeTranscriptRequest transcript,
            HttpServletRequest request
    ) {
        var call = verifiedPublicCall(sessionId, request);
        callPipelineService.recordRealtimeTranscript(
                call.getTenant().getId(), call.getId(), transcript.role(), transcript.text(), transcript.interrupted());
        var instructions = "caller".equalsIgnoreCase(transcript.role())
                ? openAiRealtimeService.realtimeInstructions(call, transcript.text())
                : "";
        return new RealtimeTranscriptResponse(instructions);
    }

    @PostMapping("/sessions/{sessionId}/realtime/tool")
    com.sauti.llm.LlmToolResult realtimeTool(
            @PathVariable String sessionId,
            @RequestBody RealtimeToolRequest tool,
            HttpServletRequest request
    ) {
        var call = verifiedPublicCall(sessionId, request);
        return openAiRealtimeService.executeTool(call, tool.callId(), tool.name(), tool.arguments());
    }

    @PostMapping(value = "/sessions/{sessionId}/turn-audio", consumes = {"audio/webm", "application/octet-stream"})
    WebVoiceAudioTurnResponse turnAudio(
            @PathVariable String sessionId,
            @RequestBody byte[] audio,
            HttpServletRequest request
    ) {
        var principal = verifyBearer(request);
        if (!sessionId.equals(principal.callSid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Web Voice session token");
        }
        var call = accessService.requireActiveCall(sessionId, principal.publicAgentId());
        if (!turnBased(call.getLanguageDetected())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This Web Voice session uses realtime audio");
        }
        var sttStart = System.nanoTime();
        var transcript = speechToTextService.transcribe(call.getAgent(), audio);
        var sttMs = (int) ((System.nanoTime() - sttStart) / 1_000_000L);
        var turn = callPipelineService.processTextTurn(
                call.getTenant().getId(),
                call.getTwilioCallSid(),
                transcript,
                sttMs
        );
        return new WebVoiceAudioTurnResponse(
                transcript,
                turn.language(),
                turn.response(),
                turn.outcome(),
                encodedAudio(call.getAgent(), turn.language(), turn.response())
        );
    }

    @PostMapping("/sessions/{sessionId}/complete")
    void completeSession(
            @PathVariable String sessionId,
            HttpServletRequest request
    ) {
        var principal = verifyBearer(request);
        if (!sessionId.equals(principal.callSid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid Web Voice session token");
        }
        var call = accessService.requireCall(sessionId, principal.publicAgentId());
        if (call.isActive()) {
            callPipelineService.completeActiveCall(sessionId, "completed");
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
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Web Voice session token is required");
        }
        try {
            return tokenService.verify(authorization.substring("Bearer ".length()).trim());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Web Voice session token");
        }
    }

    private boolean turnBased(String language) {
        return TURN_BASED_LANGUAGES.contains(language == null ? "" : language.toLowerCase(java.util.Locale.ROOT));
    }

    private String encodedAudio(Agent agent, String language, String text) {
        if (text == null || text.isBlank()) return null;
        try {
            var audio = synthesize(agent, language, text);
            return audio.length == 0 ? null : Base64.getEncoder().encodeToString(audio);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private byte[] synthesize(Agent agent, String language, String text) {
        var voiceId = agent.getTtsVoiceId();
        if (voiceId == null || voiceId.isBlank()) {
            voiceId = compatibleVoiceId(language);
            if (voiceId.isBlank()) return new byte[0];
        }
        try {
            return voiceCatalogService.synthesize(voiceId, language, text);
        } catch (RuntimeException exception) {
            var fallbackVoiceId = compatibleVoiceId(language);
            if (fallbackVoiceId.isBlank() || fallbackVoiceId.equals(voiceId)) return new byte[0];
            return voiceCatalogService.synthesize(fallbackVoiceId, language, text);
        }
    }

    private String compatibleVoiceId(String language) {
        return voiceCatalogService.list().voices().stream()
                .filter(voice -> voice.languages().contains(language))
                .map(com.sauti.voice.VoiceCatalogDtos.VoiceOption::id)
                .findFirst()
                .orElse("");
    }

}
