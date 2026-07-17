package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.dashboard.DashboardEventPublisher;
import com.sauti.session.CallSessionStore;
import com.sauti.tenant.Tenant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DefaultTwilioMediaStreamServiceTest {
    private final CallRepository callRepository = mock(CallRepository.class);
    private final CallPipelineService callPipelineService = mock(CallPipelineService.class);
    private final AudioCodecConverter audioCodecConverter = mock(AudioCodecConverter.class);
    private final FakeRealtimeSpeechToTextProvider sttProvider = new FakeRealtimeSpeechToTextProvider();
    private final FakeRealtimeTextToSpeechProvider ttsProvider = new FakeRealtimeTextToSpeechProvider();
    private final TelephonyRealtimeConversationProvider realtimeConversationProvider = mock(TelephonyRealtimeConversationProvider.class);
    private final TwilioMediaFrameFactory frameFactory = new TwilioMediaFrameFactory(new ObjectMapper());
    private final TelnyxMediaFrameFactory telnyxFrameFactory = new TelnyxMediaFrameFactory(new ObjectMapper());
    private final SentenceChunker sentenceChunker = new SentenceChunker();
    private final CallSessionStore callSessionStore = mock(CallSessionStore.class);
    private final DashboardEventPublisher dashboardEventPublisher = mock(DashboardEventPublisher.class);
    private final CallTransferService callTransferService = mock(CallTransferService.class);
    private final VoiceRuntimeMetrics metrics = new VoiceRuntimeMetrics(new SimpleMeterRegistry());
    private final DefaultTwilioMediaStreamService service = new DefaultTwilioMediaStreamService(
            callRepository,
            callPipelineService,
            audioCodecConverter,
            sttProvider,
            ttsProvider,
            realtimeConversationProvider,
            frameFactory,
            telnyxFrameFactory,
            sentenceChunker,
            callSessionStore,
            dashboardEventPublisher,
            callTransferService,
            metrics,
            0.70,
            150,
            0
    );

    @Test
    void streamsInboundAudioToSttAndFinalTranscriptBackToTwilioAudio() {
        var call = activeCall("CA123");
        var frames = new CopyOnWriteArrayList<String>();
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {1, 2})).thenReturn(new byte[] {10, 20, 30, 40});
        when(audioCodecConverter.pcm16kToTwilioMulaw8k(new byte[] {5, 6, 7, 8})).thenReturn(new byte[] {9, 10});
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(call));
        when(callPipelineService.processLiveTranscriptTurn(any(Call.class), org.mockito.ArgumentMatchers.eq("book tomorrow")))
                .thenReturn(new CallPipelineService.TurnResult("en", "Yes. I can help with that.", new byte[0], ""));

        service.start("CA123", "MZ123", new TwilioMediaFormat("audio/x-mulaw", 8000, 1), Map.of(), frames::add);
        assertThat(ttsProvider.openCount).isZero();
        service.acceptInboundAudio("CA123", "MZ123", "1", "1", "20", new byte[] {1, 2});

        awaitUntil(() -> sttProvider.session.audioFrames.size() == 1);
        assertThat(sttProvider.session.audioFrames).containsExactly(new byte[] {10, 20, 30, 40});

        sttProvider.listener.onFinalTranscript("book tomorrow");

        verify(callPipelineService, timeout(1000)).processLiveTranscriptTurn(call, "book tomorrow");
        awaitUntil(() -> frames.size() == 3);
        assertThat(ttsProvider.spokenText).containsExactly("Yes. ", "I can help with that. ", "");
        assertThat(ttsProvider.openCount).isEqualTo(1);
        assertThat(frames).hasSize(3);
        assertThat(frames.get(0)).contains("\"event\":\"media\"", "\"streamSid\":\"MZ123\"");
        assertThat(frames.get(1)).contains("\"event\":\"media\"", "\"streamSid\":\"MZ123\"");
        assertThat(frames.get(2)).contains("\"event\":\"mark\"", "\"name\":\"turn-1-end\"");
    }

    @Test
    void stopClosesSttSession() {
        service.start("CA123", "MZ123", TwilioMediaFormat.unknown(), Map.of(), ignored -> {
        });

        service.stop("CA123", "MZ123");

        assertThat(sttProvider.session.closed).isTrue();
    }

    @Test
    void partialTranscriptDuringAgentSpeechClearsTwilioBufferAndMarksInterruption() {
        var call = activeCall("CA123");
        var frames = new CopyOnWriteArrayList<String>();
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {1, 2})).thenReturn(new byte[] {10, 20, 30, 40});
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {3, 4})).thenReturn(new byte[] {50, 60, 70, 80});
        when(audioCodecConverter.pcm16kToTwilioMulaw8k(new byte[] {5, 6, 7, 8})).thenReturn(new byte[] {9, 10});
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(call));
        when(callPipelineService.processLiveTranscriptTurn(any(Call.class), eq("book tomorrow")))
                .thenReturn(new CallPipelineService.TurnResult("en", "Yes. I can help with that.", new byte[0], ""));

        service.start("CA123", "MZ123", new TwilioMediaFormat("audio/x-mulaw", 8000, 1), Map.of(), frames::add);
        service.acceptInboundAudio("CA123", "MZ123", "1", "1", "20", new byte[] {1, 2});
        sttProvider.listener.onFinalTranscript("book tomorrow");
        awaitUntil(() -> frames.stream().anyMatch(frame -> frame.contains("\"event\":\"mark\"")));
        service.acceptInboundAudio("CA123", "MZ123", "2", "2", "220", new byte[] {3, 4});

        sttProvider.listener.onPartialTranscript("wait", 0.92);

        awaitUntil(() -> frames.stream().anyMatch(frame -> frame.contains("\"event\":\"clear\"")));
        assertThat(frames).anySatisfy(frame -> assertThat(frame).contains("\"event\":\"clear\"", "\"streamSid\":\"MZ123\""));
        assertThat(ttsProvider.sessions.get(0).closed).isTrue();
        assertThat(ttsProvider.openCount).isEqualTo(1);
        verify(callSessionStore).markInterrupted("CA123");
        verify(callSessionStore).setSpeaking("CA123", false, "");
    }

    @Test
    void lowConfidencePartialTranscriptDoesNotBargeIn() {
        var call = activeCall("CA123");
        var frames = new CopyOnWriteArrayList<String>();
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {1, 2})).thenReturn(new byte[] {10, 20, 30, 40});
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {3, 4})).thenReturn(new byte[] {50, 60, 70, 80});
        when(audioCodecConverter.pcm16kToTwilioMulaw8k(new byte[] {5, 6, 7, 8})).thenReturn(new byte[] {9, 10});
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(call));
        when(callPipelineService.processLiveTranscriptTurn(any(Call.class), eq("book tomorrow")))
                .thenReturn(new CallPipelineService.TurnResult("en", "Yes. I can help with that.", new byte[0], ""));

        service.start("CA123", "MZ123", new TwilioMediaFormat("audio/x-mulaw", 8000, 1), Map.of(), frames::add);
        service.acceptInboundAudio("CA123", "MZ123", "1", "1", "20", new byte[] {1, 2});
        sttProvider.listener.onFinalTranscript("book tomorrow");
        awaitUntil(() -> frames.stream().anyMatch(frame -> frame.contains("\"event\":\"mark\"")));
        service.acceptInboundAudio("CA123", "MZ123", "2", "2", "220", new byte[] {3, 4});

        sttProvider.listener.onPartialTranscript("wait", 0.40);

        sleep(100);
        assertThat(frames).noneSatisfy(frame -> assertThat(frame).contains("\"event\":\"clear\""));
    }

    @Test
    void staleTtsAudioAfterBargeInIsSuppressedByGeneration() {
        var call = activeCall("CA123");
        var frames = new CopyOnWriteArrayList<String>();
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {1, 2})).thenReturn(new byte[] {10, 20, 30, 40});
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {3, 4})).thenReturn(new byte[] {50, 60, 70, 80});
        when(audioCodecConverter.pcm16kToTwilioMulaw8k(new byte[] {5, 6, 7, 8})).thenReturn(new byte[] {9, 10});
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(call));
        when(callPipelineService.processLiveTranscriptTurn(any(Call.class), eq("book tomorrow")))
                .thenReturn(new CallPipelineService.TurnResult("en", "Yes. I can help with that.", new byte[0], ""));

        service.start("CA123", "MZ123", new TwilioMediaFormat("audio/x-mulaw", 8000, 1), Map.of(), frames::add);
        service.acceptInboundAudio("CA123", "MZ123", "1", "1", "20", new byte[] {1, 2});
        sttProvider.listener.onFinalTranscript("book tomorrow");
        awaitUntil(() -> frames.stream().anyMatch(frame -> frame.contains("\"event\":\"mark\"")));
        service.acceptInboundAudio("CA123", "MZ123", "2", "2", "220", new byte[] {3, 4});
        sttProvider.listener.onPartialTranscript("wait", 0.92);
        awaitUntil(() -> frames.stream().anyMatch(frame -> frame.contains("\"event\":\"clear\"")));
        var framesAfterClear = frames.size();

        ttsProvider.sessions.get(0).emitAudio();
        sleep(100);

        assertThat(frames).hasSize(framesAfterClear);
    }

    @Test
    void partialTranscriptDuringGraceWindowDoesNotBargeIn() {
        var guardedService = new DefaultTwilioMediaStreamService(
                callRepository,
                callPipelineService,
                audioCodecConverter,
                sttProvider,
                ttsProvider,
                realtimeConversationProvider,
                frameFactory,
                telnyxFrameFactory,
                sentenceChunker,
                callSessionStore,
                dashboardEventPublisher,
                callTransferService,
                metrics,
                0.70,
                150,
                300
        );
        var call = activeCall("CA123");
        call.getAgent().updateCallBehavior(0.7, 300, null, null, null, null, null, null, null, null, null, null, null);
        var frames = new CopyOnWriteArrayList<String>();
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {1, 2})).thenReturn(new byte[] {10, 20, 30, 40});
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {3, 4})).thenReturn(new byte[] {50, 60, 70, 80});
        when(audioCodecConverter.pcm16kToTwilioMulaw8k(new byte[] {5, 6, 7, 8})).thenReturn(new byte[] {9, 10});
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(call));
        when(callPipelineService.processLiveTranscriptTurn(any(Call.class), eq("book tomorrow")))
                .thenReturn(new CallPipelineService.TurnResult("en", "Yes. I can help with that.", new byte[0], ""));

        guardedService.start("CA123", "MZ123", new TwilioMediaFormat("audio/x-mulaw", 8000, 1), Map.of(), frames::add);
        guardedService.acceptInboundAudio("CA123", "MZ123", "1", "1", "20", new byte[] {1, 2});
        sttProvider.listener.onFinalTranscript("book tomorrow");
        awaitUntil(() -> frames.stream().anyMatch(frame -> frame.contains("\"event\":\"mark\"")));
        guardedService.acceptInboundAudio("CA123", "MZ123", "2", "2", "220", new byte[] {3, 4});

        sttProvider.listener.onPartialTranscript("wait", 0.92);

        sleep(100);
        assertThat(frames).noneSatisfy(frame -> assertThat(frame).contains("\"event\":\"clear\""));
    }

    @Test
    void buffersDtmfAndResolvesConfiguredMenuOptionOnTerminationKey() {
        var call = activeCall("CA123");
        call.getAgent().updateCallBehavior(
                0.7, 0, null, null, null, null, null, true, null, null, null, null, null
        );
        call.getAgent().configureDtmf("#", 5, 8, Map.of("1", "Confirm the appointment"));
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(call));
        when(callPipelineService.processLiveTranscriptTurn(
                call,
                "Caller selected keypad option \"Confirm the appointment\" (digits: 1)."
        )).thenReturn(new CallPipelineService.TurnResult("en", "Your appointment is confirmed.", new byte[0], ""));

        service.start("CA123", "MZ123", TwilioMediaFormat.unknown(), Map.of(), ignored -> {
        });
        service.acceptDtmf("CA123", "1");
        service.acceptDtmf("CA123", "#");

        verify(callPipelineService, timeout(1000)).processLiveTranscriptTurn(
                call,
                "Caller selected keypad option \"Confirm the appointment\" (digits: 1)."
        );
        awaitUntil(() -> ttsProvider.spokenText.contains("Your appointment is confirmed. "));
    }

    @Test
    void routesPhoneAudioThroughRealtimeAndStreamsTextToCartesiaWithoutCascadeTurn() {
        var call = activeCall("CA-REALTIME");
        call.getAgent().updateTtsVoiceId("cartesia:voice-1");
        var frames = new CopyOnWriteArrayList<String>();
        var realtimeSession = mock(TelephonyRealtimeConversationProvider.Session.class);
        var realtimeListener = new java.util.concurrent.atomic.AtomicReference<TelephonyRealtimeConversationProvider.Listener>();
        when(realtimeConversationProvider.supports(call)).thenReturn(true);
        when(realtimeConversationProvider.open(eq(call), any())).thenAnswer(invocation -> {
            realtimeListener.set(invocation.getArgument(1));
            return CompletableFuture.completedFuture(realtimeSession);
        });
        when(audioCodecConverter.twilioMulaw8kToPcm16k(new byte[] {1, 2}))
                .thenReturn(new byte[] {10, 20, 30, 40});
        when(audioCodecConverter.pcm16kToTwilioMulaw8k(new byte[] {5, 6, 7, 8}))
                .thenReturn(new byte[] {9, 10});
        when(callRepository.findByTwilioCallSid("CA-REALTIME")).thenReturn(Optional.of(call));

        service.start(
                "CA-REALTIME",
                "MZ-REALTIME",
                new TwilioMediaFormat("audio/x-mulaw", 8000, 1),
                Map.of(),
                frames::add
        );
        service.acceptInboundAudio("CA-REALTIME", "MZ-REALTIME", "1", "1", "20", new byte[] {1, 2});

        verify(realtimeSession, timeout(1000)).sendPcmAudio(new byte[] {10, 20, 30, 40});
        realtimeListener.get().onCallerTranscript("I need an appointment");
        realtimeListener.get().onAgentTextDelta("Certainly. ");
        realtimeListener.get().onAgentTextDelta("What day works for you?");
        realtimeListener.get().onAgentTextComplete("Certainly. What day works for you?", false);

        verify(callPipelineService, timeout(1000)).recordRealtimeTranscript(
                call.getTenant().getId(), call.getId(), "caller", "I need an appointment", false
        );
        verify(callPipelineService, timeout(1000)).recordRealtimeTranscript(
                call.getTenant().getId(), call.getId(), "agent", "Certainly. What day works for you?", false
        );
        awaitUntil(() -> ttsProvider.spokenText.contains(""));
        awaitUntil(() -> frames.stream().anyMatch(frame -> frame.contains("\"event\":\"media\"")));
        assertThat(ttsProvider.spokenText).containsExactly("Certainly. ", "What day works for you? ", "");
        assertThat(frames).anySatisfy(frame -> assertThat(frame).contains("\"event\":\"media\""));
    }

    private void awaitUntil(BooleanSupplier condition) {
        var deadline = System.currentTimeMillis() + 1000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(10);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private Call activeCall(String callSid) {
        var tenant = new Tenant("Demo Clinic", "owner@example.com", "SN");
        var agent = new Agent(tenant, "Amina", "Bonjour", "Prompt");
        agent.updateCallBehavior(0.7, 0, null, null, null, null, null, null, null, null, null, null, null);
        agent.activate();
        return new Call(tenant, agent, callSid, "+221771234567", "inbound");
    }

    private static final class FakeRealtimeSpeechToTextProvider implements RealtimeSpeechToTextProvider {
        private RealtimeTranscriptListener listener;
        private FakeRealtimeSttSession session;

        @Override
        public CompletableFuture<RealtimeSttSession> open(RealtimeTranscriptListener listener) {
            this.listener = listener;
            this.session = new FakeRealtimeSttSession();
            return CompletableFuture.completedFuture(session);
        }
    }

    private static final class FakeRealtimeSttSession implements RealtimeSttSession {
        private final List<byte[]> audioFrames = new ArrayList<>();
        private boolean closed;

        @Override
        public void sendPcmAudio(byte[] pcm16kAudio) {
            audioFrames.add(pcm16kAudio);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FakeRealtimeTextToSpeechProvider implements RealtimeTextToSpeechProvider {
        private final List<String> spokenText = new ArrayList<>();
        private final List<FakeRealtimeTtsSession> sessions = new ArrayList<>();
        private int openCount;

        @Override
        public CompletableFuture<RealtimeTtsSession> open(String language, String voiceId, TtsAudioListener listener) {
            openCount++;
            var session = new FakeRealtimeTtsSession(listener, spokenText);
            sessions.add(session);
            return CompletableFuture.completedFuture(session);
        }
    }

    private static final class FakeRealtimeTtsSession implements RealtimeTtsSession {
        private final TtsAudioListener listener;
        private final List<String> spokenText;
        private boolean closed;

        private FakeRealtimeTtsSession(TtsAudioListener listener, List<String> spokenText) {
            this.listener = listener;
            this.spokenText = spokenText;
        }

        @Override
        public void speak(String text, boolean flush) {
            spokenText.add(text);
            emitAudioIfNeeded(text);
            if (flush) {
                listener.onComplete();
            }
        }

        private void emitAudio() {
            listener.onPcmAudio(new byte[] {5, 6, 7, 8});
        }

        private void emitAudioIfNeeded(String text) {
            if (!text.isBlank()) {
                emitAudio();
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
