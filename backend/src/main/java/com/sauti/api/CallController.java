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
import com.sauti.call.CallTurnRepository;
import com.sauti.voice.VoiceCatalogService;
import java.util.List;
import java.util.UUID;
import java.util.Base64;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
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
    private final CallTurnRepository callTurnRepository;
    private final CallRecordingService callRecordingService;
    private final VoiceCatalogService voiceCatalogService;
    private final BrowserSpeechToTextService browserSpeechToTextService;

    public CallController(
            CallQueryService callQueryService,
            CallPipelineService callPipelineService,
            CallTurnRepository callTurnRepository,
            CallRecordingService callRecordingService,
            VoiceCatalogService voiceCatalogService,
            BrowserSpeechToTextService browserSpeechToTextService
    ) {
        this.callQueryService = callQueryService;
        this.callPipelineService = callPipelineService;
        this.callTurnRepository = callTurnRepository;
        this.callRecordingService = callRecordingService;
        this.voiceCatalogService = voiceCatalogService;
        this.browserSpeechToTextService = browserSpeechToTextService;
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
        var call = callPipelineService.startTestCall(user.tenantId(), request.agentId());
        var greeting = callTurnRepository.findByCall_IdOrderByTurnIndexAsc(call.getId()).stream()
                .findFirst().map(turn -> turn.getAgentResponse()).orElse("");
        return new StartTestCallResponse(CallResponse.from(call), greeting, TestCallSettings.from(call.getAgent()));
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
        var callerTranscript = browserSpeechToTextService.transcribe(call.getAgent(), audio);
        var sttMs = elapsedMs(sttStart);
        var turn = callPipelineService.processTextTurn(
                user.tenantId(), call.getTwilioCallSid(), callerTranscript, sttMs);
        var persistedTurn = callTurnRepository.findFirstByCall_IdOrderByTurnIndexDesc(id).orElse(null);
        var ttsStart = System.nanoTime();
        var synthesized = turn.response() == null || turn.response().isBlank()
                ? new byte[0]
                : synthesize(call, turn.language(), turn.response());
        var ttsMs = elapsedMs(ttsStart);
        if (persistedTurn != null) {
            persistedTurn.recordTtsLatency(ttsMs);
            callTurnRepository.save(persistedTurn);
        }
        return new TestAudioTurnResponse(
                callerTranscript,
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
        callQueryService.get(user.tenantId(), id);
        return callTurnRepository.findByCall_IdOrderByTurnIndexAsc(id).stream()
                .map(CallTurnResponse::from)
                .toList();
    }

    @PostMapping(value = "/{id}/test-audio", produces = "audio/mpeg")
    ResponseEntity<byte[]> testAudio(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        var call = callQueryService.get(user.tenantId(), id);
        var lastTurn = callTurnRepository.findByCall_IdOrderByTurnIndexAsc(id).stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalArgumentException("Call has no agent response"));
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(synthesize(call, lastTurn.getLanguage(), lastTurn.getAgentResponse()));
    }

    private byte[] synthesize(com.sauti.call.Call call, String language, String text) {
        var voiceId = call.getAgent().getTtsVoiceId();
        if (voiceId == null || voiceId.isBlank()) {
            var match = voiceCatalogService.list().voices().stream()
                    .filter(voice -> voice.languages().contains(language))
                    .findFirst();
            if (match.isEmpty()) {
                return new byte[0];
            }
            voiceId = match.get().id();
        }
        return voiceCatalogService.synthesize(voiceId, language, text);
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
