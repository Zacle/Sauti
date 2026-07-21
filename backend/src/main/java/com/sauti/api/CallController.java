package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.call.CallDtos.CallResponse;
import com.sauti.call.CallDtos.CallTurnResponse;
import com.sauti.call.CallDtos.CompleteTestCallRequest;
import com.sauti.call.CallDtos.SimulatedTurnRequest;
import com.sauti.call.CallDtos.SimulatedTurnResponse;
import com.sauti.call.CallDtos.StartTestCallRequest;
import com.sauti.call.CallDtos.StartTestCallResponse;
import com.sauti.call.CallDtos.TestAudioTurnResponse;
import com.sauti.call.CallDtos.TestCallSettings;
import com.sauti.call.BrowserSpeechToTextService;
import com.sauti.call.CallPipelineService;
import com.sauti.call.CallQueryService;
import com.sauti.call.CallRecordingService;
import com.sauti.call.WebVoiceTokenService;
import com.sauti.call.OpenAiRealtimeService;
import com.sauti.call.CartesiaRealtimeTextToSpeechClient;
import com.sauti.call.RealtimeDtos.RealtimeToolRequest;
import com.sauti.call.RealtimeDtos.RealtimeTranscriptRequest;
import com.sauti.call.RealtimeDtos.RealtimeTranscriptResponse;
import com.sauti.voice.VoiceCatalogService;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calls")
public class CallController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CallController.class);
    private final CallQueryService callQueryService;
    private final CallPipelineService callPipelineService;
    private final CallRecordingService callRecordingService;
    private final VoiceCatalogService voiceCatalogService;
    private final BrowserSpeechToTextService browserSpeechToTextService;
    private final WebVoiceTokenService webVoiceTokenService;
    private final String webVoiceWebsocketUrl;
    private final OpenAiRealtimeService openAiRealtimeService;
    private final CartesiaRealtimeTextToSpeechClient cartesiaClient;

    public CallController(
            CallQueryService callQueryService,
            CallPipelineService callPipelineService,
            CallRecordingService callRecordingService,
            VoiceCatalogService voiceCatalogService,
            BrowserSpeechToTextService browserSpeechToTextService,
            WebVoiceTokenService webVoiceTokenService,
            OpenAiRealtimeService openAiRealtimeService,
            CartesiaRealtimeTextToSpeechClient cartesiaClient,
            @Value("${sauti.web-voice.public-websocket-base-url:ws://localhost:8082}") String webVoiceWebsocketUrl
    ) {
        this.callQueryService = callQueryService;
        this.callPipelineService = callPipelineService;
        this.callRecordingService = callRecordingService;
        this.voiceCatalogService = voiceCatalogService;
        this.browserSpeechToTextService = browserSpeechToTextService;
        this.webVoiceTokenService = webVoiceTokenService;
        this.openAiRealtimeService = openAiRealtimeService;
        this.cartesiaClient = cartesiaClient;
        this.webVoiceWebsocketUrl = webVoiceWebsocketUrl;
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
        var call = callPipelineService.startTestCall(user.tenantId(), request.agentId(), request.ttsVoiceId());
        var greeting = callQueryService.firstAgentResponse(user.tenantId(), call.getId());
        var agentKey = call.getAgent().getId().toString();
        var token = webVoiceTokenService.issue(call.getTwilioCallSid(), agentKey);
        var mode = realtimeMode(call);
        var websocketPath = "hybrid_realtime".equals(mode) ? "/ws/hybrid-voice/" : "/ws/web-voice/";
        var greetingAudio = cachedHybridGreeting(call, greeting, mode);
        return new StartTestCallResponse(
                CallResponse.from(call),
                greeting,
                greetingAudio,
                TestCallSettings.from(call.getAgent()),
                webVoiceWebsocketUrl + websocketPath + call.getTwilioCallSid() + "?token=" + token,
                token,
                16000,
                mode,
                openAiRealtimeService.hasTool(call, "check_availability")
        );
    }

    private String cachedHybridGreeting(com.sauti.call.Call call, String greeting, String mode) {
        if (!"hybrid_realtime".equals(mode) || greeting == null || greeting.isBlank()
                || !openAiRealtimeService.usesCartesiaVoice(call)) return null;
        try {
            var language = call.getLanguageDetected() == null
                    ? call.getAgent().getDefaultLanguage()
                    : call.getLanguageDetected();
            var audio = CompletableFuture.supplyAsync(() ->
                            voiceCatalogService.cachedCartesiaGreeting(
                                    call.getAgent().getTtsVoiceId(), language, greeting
                            ))
                    .completeOnTimeout(null, 1500, TimeUnit.MILLISECONDS)
                    .join();
            return audio == null ? null : Base64.getEncoder().encodeToString(audio);
        } catch (RuntimeException exception) {
            LOGGER.warn("Unable to prepare cached Cartesia greeting for test call={}", call.getId(), exception);
            return null;
        }
    }

    private String realtimeMode(com.sauti.call.Call call) {
        if (!openAiRealtimeService.enabled()) return "cascade";
        if ((openAiRealtimeService.usesCartesiaVoice(call) || openAiRealtimeService.usesOpenAiVoice(call))
                && cartesiaClient.isConfigured()) return "hybrid_realtime";
        return "cascade";
    }

    @PostMapping(value = "/{id}/realtime/connect", consumes = "application/sdp", produces = "application/sdp")
    ResponseEntity<String> connectRealtime(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody String sdpOffer
    ) {
        var call = callQueryService.get(user.tenantId(), id);
        if (!"test".equals(call.getDirection()) || !call.isActive()) {
            throw new IllegalArgumentException("The browser test call is not active");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/sdp"))
                .body(openAiRealtimeService.createWebRtcSession(call, sdpOffer));
    }

    @PostMapping("/{id}/realtime/transcript")
    RealtimeTranscriptResponse recordRealtimeTranscript(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody RealtimeTranscriptRequest request
    ) {
        callPipelineService.recordRealtimeTranscript(user.tenantId(), id, request.role(), request.text(), request.interrupted());
        if (!"caller".equalsIgnoreCase(request.role())) return new RealtimeTranscriptResponse("");
        var call = callQueryService.get(user.tenantId(), id);
        return openAiRealtimeService.prepareCallerResponse(call, request.text());
    }

    @PostMapping("/{id}/realtime/tool")
    com.sauti.llm.LlmToolResult realtimeTool(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody RealtimeToolRequest request
    ) {
        var call = callQueryService.get(user.tenantId(), id);
        if (!"test".equals(call.getDirection()) || !call.isActive()) {
            throw new IllegalArgumentException("The browser test call is not active");
        }
        return openAiRealtimeService.executeTool(call, request.callId(), request.name(), request.arguments());
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
        return CallResponse.from(callPipelineService.completeTestCall(user.tenantId(), id, outcome));
    }

    @PostMapping("/{id}/test-interruption")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    void markTestInterruption(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        callPipelineService.markTestCallInterrupted(user.tenantId(), id);
    }

    @PostMapping("/{id}/test-reminder")
    TestAudioTurnResponse testReminder(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        var reminder = callPipelineService.addTestReminder(user.tenantId(), id);
        return new TestAudioTurnResponse("", reminder.language(), reminder.text(), "", null, 0, 0, 0, 0);
    }

    @PostMapping("/{id}/test-farewell")
    TestAudioTurnResponse testFarewell(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        var farewell = callPipelineService.addTestFarewell(user.tenantId(), id);
        return new TestAudioTurnResponse("", farewell.language(), farewell.text(), "no-response", null, 0, 0, 0, 0);
    }

    @PostMapping(value = "/{id}/test-turn-audio", consumes = {"audio/webm", "application/octet-stream"})
    TestAudioTurnResponse testAudioTurn(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody byte[] audio
    ) {
        var call = callQueryService.get(user.tenantId(), id);
        if (!"test".equals(call.getDirection()) || !call.isActive()) {
            throw new IllegalArgumentException("The browser test call is not active");
        }
        var totalStart = System.nanoTime();
        var sttStart = System.nanoTime();
        var callerTranscript = transcribeTestAudio(call, audio);
        var sttMs = elapsedMs(sttStart);
        var turn = processTestTranscriptTurn(user.tenantId(), call, callerTranscript, sttMs);
        var persistedTurn = callQueryService.lastTurn(user.tenantId(), id);
        var acceptedTranscript = turn.acceptedTranscript() ? callerTranscript : "";
        var ttsStart = System.nanoTime();
        var synthesized = synthesizeTestTurn(call, turn.language(), turn.response());
        var ttsMs = elapsedMs(ttsStart);
        if (persistedTurn != null) {
            persistedTurn = callQueryService.recordLatestTtsLatency(user.tenantId(), id, ttsMs);
        }
        return new TestAudioTurnResponse(
                acceptedTranscript,
                turn.language(),
                turn.response(),
                turn.outcome(),
                synthesized.length == 0 ? null : Base64.getEncoder().encodeToString(synthesized),
                sttMs,
                persistedTurn == null ? 0 : persistedTurn.getLlmLatencyMs(),
                ttsMs,
                elapsedMs(totalStart)
        );
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

    @PostMapping(value = "/{id}/test-audio", produces = "audio/mpeg")
    ResponseEntity<byte[]> testAudio(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        var call = callQueryService.get(user.tenantId(), id);
        var lastTurn = callQueryService.lastTurn(user.tenantId(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(synthesize(call, lastTurn.getLanguage(), lastTurn.getAgentResponse()));
    }

    private byte[] synthesize(com.sauti.call.Call call, String language, String text) {
        var voiceId = call.getAgent().getTtsVoiceId();
        if (voiceId == null || voiceId.isBlank()) {
            voiceId = compatibleVoiceId(language);
            if (voiceId.isBlank()) return new byte[0];
        }
        try {
            return voiceCatalogService.synthesize(voiceId, language, text);
        } catch (RuntimeException exception) {
            LOGGER.warn("Configured test voice could not synthesize language={} callId={}; trying compatible fallback",
                    language, call.getId(), exception);
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

    private String transcribeTestAudio(com.sauti.call.Call call, byte[] audio) {
        try {
            return browserSpeechToTextService.transcribe(call.getAgent(), audio);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IllegalStateException exception) {
            LOGGER.warn("Browser test speech recognition failed callId={}", call.getId(), exception);
            throw new IllegalArgumentException("Speech recognition is unavailable. Check the speech provider configuration or type a test message instead.");
        }
    }

    private SimulatedTurnResponse processTestTranscriptTurn(
            UUID tenantId,
            com.sauti.call.Call call,
            String callerTranscript,
            int sttMs
    ) {
        try {
            return callPipelineService.processTextTurn(
                    tenantId, call.getTwilioCallSid(), callerTranscript, sttMs);
        } catch (RuntimeException exception) {
            LOGGER.warn("Browser test turn failed after transcription callId={}", call.getId(), exception);
            throw new IllegalArgumentException("The agent could not respond to that voice turn. Check the agent tools, prompt, and LLM configuration, or type a test message instead.");
        }
    }

    private byte[] synthesizeTestTurn(com.sauti.call.Call call, String language, String text) {
        if (text == null || text.isBlank()) {
            return new byte[0];
        }
        try {
            return synthesize(call, language, text);
        } catch (RuntimeException exception) {
            LOGGER.warn("Browser test voice synthesis failed callId={}", call.getId(), exception);
            return new byte[0];
        }
    }

    private int elapsedMs(long startedAt) {
        return (int) ((System.nanoTime() - startedAt) / 1_000_000L);
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
