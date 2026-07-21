package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class HybridVoiceSessionServiceTest {
    @Test
    void streamsBufferedTextThroughOneWarmCartesiaSessionAndForwardsPcm() throws Exception {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var tts = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getTwilioCallSid()).thenReturn("test-hybrid");
        when(call.getLanguageDetected()).thenReturn("fr");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:french-voice");
        when(repository.findByTwilioCallSid("test-hybrid")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        var delayedTts = new CompletableFuture<RealtimeTtsSession>();
        when(provider.open(eq("fr"), eq("cartesia:french-voice"), any())).thenReturn(delayedTts);
        var listener = ArgumentCaptor.forClass(TtsAudioListener.class);
        var registry = new SimpleMeterRegistry();
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(registry)
        );

        service.start("test-hybrid", agentId.toString(), socket);
        verify(provider).open(eq("fr"), eq("cartesia:french-voice"), listener.capture());
        service.accept("test-hybrid", "{\"type\":\"turn_started\",\"generation\":0}");
        service.accept("test-hybrid", "{\"type\":\"tts_delta\",\"text\":\"Bonjour, je peux vous aider avec votre rendez-vous. \"}");
        service.accept("test-hybrid", "{\"type\":\"tts_complete\"}");
        delayedTts.complete(tts);
        listener.getValue().onPcmAudio(new byte[] {1, 2, 3, 4});
        listener.getValue().onComplete();
        service.accept("test-hybrid", "{\"type\":\"playback_started\",\"generation\":0,\"id\":\"legacy-1\"}");
        service.accept("test-hybrid", "{\"type\":\"playback_underrun\"}");

        var writes = inOrder(tts);
        writes.verify(tts).speak("Bonjour, je peux vous aider avec votre rendez-vous.", true);
        var binary = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(socket).sendMessage(binary.capture());
        assertThat(binary.getValue().getPayloadLength()).isEqualTo(4);
        verify(socket, atLeastOnce()).sendMessage(any(TextMessage.class));
        assertThat(registry.find("sauti.voice.latency")
                .tag("stage", "transcript_to_speech_ready").timer().count()).isEqualTo(1);
        assertThat(registry.find("sauti.voice.latency")
                .tag("stage", "speech_ready_to_first_audio").timer().count()).isEqualTo(1);
        assertThat(registry.find("sauti.voice.latency")
                .tag("stage", "transcript_to_playback").timer().count()).isEqualTo(1);
        assertThat(registry.find("sauti.voice.playback.underruns").counter().count()).isEqualTo(1);
    }

    @Test
    void validatesTheCompleteMessageBeforeSynthesizingAndStripsTheAssistantLabel() throws Exception {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var tts = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getTwilioCallSid()).thenReturn("guarded-hybrid");
        when(call.getLanguageDetected()).thenReturn("en");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:english-voice");
        when(repository.findByTwilioCallSid("guarded-hybrid")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        when(provider.open(eq("en"), eq("cartesia:english-voice"), any()))
                .thenReturn(CompletableFuture.completedFuture(tts));
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("guarded-hybrid", agentId.toString(), socket);
        service.accept("guarded-hybrid", "{\"type\":\"tts_delta\",\"text\":\"assis\"}");
        service.accept("guarded-hybrid", "{\"type\":\"tts_delta\","
                + "\"text\":\"tant: Hi Walker, a haircut costs 5 dollars.\"}");

        verify(tts, never()).speak(anyString(), eq(false));

        service.accept("guarded-hybrid", "{\"type\":\"tts_complete\"}");

        var spoken = ArgumentCaptor.forClass(String.class);
        verify(tts).speak(spoken.capture(), eq(true));
        assertThat(spoken.getAllValues().get(0).trim())
                .isEqualTo("Hi Walker, a haircut costs 5 dollars.");
    }

    @Test
    void neverSynthesizesProtocolThatFollowsNaturalLookingText() {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var tts = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("web");
        when(call.getTwilioCallSid()).thenReturn("protocol-hybrid");
        when(call.getLanguageDetected()).thenReturn("en");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:english-voice");
        when(repository.findByTwilioCallSid("protocol-hybrid")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        when(provider.open(eq("en"), eq("cartesia:english-voice"), any()))
                .thenReturn(CompletableFuture.completedFuture(tts));
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("protocol-hybrid", agentId.toString(), socket);
        service.accept("protocol-hybrid", "{\"type\":\"tts_delta\","
                + "\"text\":\"Hi Walker.\\nanalysis to=functions.get_business_hours code\"}");
        service.accept("protocol-hybrid", "{\"type\":\"tts_complete\"}");

        verify(tts, never()).speak(anyString(), anyBoolean());
    }

    @Test
    void interruptionClosesTheOldCartesiaContextAndOpensANewOne() {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var first = mock(RealtimeTtsSession.class);
        var second = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getTwilioCallSid()).thenReturn("interrupt-hybrid");
        when(call.getLanguageDetected()).thenReturn("en");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:english-voice");
        when(repository.findByTwilioCallSid("interrupt-hybrid")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        when(provider.open(eq("en"), eq("cartesia:english-voice"), any()))
                .thenReturn(CompletableFuture.completedFuture(first), CompletableFuture.completedFuture(second));
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("interrupt-hybrid", agentId.toString(), socket);
        service.accept("interrupt-hybrid", "{\"type\":\"speak\",\"generation\":0,"
                + "\"id\":\"active\",\"text\":\"This response is still active.\"}");
        service.accept("interrupt-hybrid", "{\"type\":\"interrupt\"}");

        verify(first).close();
        verify(provider, times(2)).open(eq("en"), eq("cartesia:english-voice"), any());
    }

    @Test
    void speaksTheSameCompletedResponseOnlyOnceEvenWithDifferentMessageIds() {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var tts = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getTwilioCallSid()).thenReturn("dedupe-hybrid");
        when(call.getLanguageDetected()).thenReturn("en");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:english-voice");
        when(repository.findByTwilioCallSid("dedupe-hybrid")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        when(provider.open(eq("en"), eq("cartesia:english-voice"), any()))
                .thenReturn(CompletableFuture.completedFuture(tts));
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("dedupe-hybrid", agentId.toString(), socket);
        service.accept("dedupe-hybrid", "{\"type\":\"speak\",\"generation\":2,"
                + "\"id\":\"response-a\",\"text\":\"Please confirm the booking details.\"}");
        service.accept("dedupe-hybrid", "{\"type\":\"speak\",\"generation\":2,"
                + "\"id\":\"response-b\",\"text\":\"  Please confirm the booking details.  \"}");

        verify(tts).speak(anyString(), eq(true));
    }

    @Test
    void waitsForCartesiaCompletionBeforeStartingTheNextResponse() {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var tts = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("web");
        when(call.getTwilioCallSid()).thenReturn("serial-hybrid");
        when(call.getLanguageDetected()).thenReturn("en");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:english-voice");
        when(repository.findByTwilioCallSid("serial-hybrid")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        when(provider.open(eq("en"), eq("cartesia:english-voice"), any()))
                .thenReturn(CompletableFuture.completedFuture(tts));
        var listener = ArgumentCaptor.forClass(TtsAudioListener.class);
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("serial-hybrid", agentId.toString(), socket);
        verify(provider).open(eq("en"), eq("cartesia:english-voice"), listener.capture());
        service.accept("serial-hybrid", "{\"type\":\"speak\",\"generation\":3,"
                + "\"id\":\"first\",\"text\":\"First response.\"}");
        service.accept("serial-hybrid", "{\"type\":\"speak\",\"generation\":3,"
                + "\"id\":\"second\",\"text\":\"Second response.\"}");

        verify(tts).speak("First response.", true);
        listener.getValue().onComplete();
        verify(tts).speak("Second response.", true);
    }

    @Test
    void ignoresLateSpeechFromTheGenerationThatWasInterrupted() {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var first = mock(RealtimeTtsSession.class);
        var second = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getTwilioCallSid()).thenReturn("stale-hybrid");
        when(call.getLanguageDetected()).thenReturn("en");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:english-voice");
        when(repository.findByTwilioCallSid("stale-hybrid")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        when(provider.open(eq("en"), eq("cartesia:english-voice"), any()))
                .thenReturn(CompletableFuture.completedFuture(first), CompletableFuture.completedFuture(second));
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("stale-hybrid", agentId.toString(), socket);
        service.accept("stale-hybrid", "{\"type\":\"speak\",\"generation\":0,"
                + "\"id\":\"old-a\",\"text\":\"Old response.\"}");
        service.accept("stale-hybrid", "{\"type\":\"interrupt\",\"generation\":1}");
        service.accept("stale-hybrid", "{\"type\":\"speak\",\"generation\":0,"
                + "\"id\":\"old-b\",\"text\":\"Old response arriving late.\"}");

        verify(second, never()).speak(anyString(), anyBoolean());

        service.accept("stale-hybrid", "{\"type\":\"speak\",\"generation\":1,"
                + "\"id\":\"new\",\"text\":\"New response.\"}");
        verify(second).speak("New response.", true);
    }

    @Test
    void providerErrorEndsSpeakingAndTheNextTurnReopensTheConnection() throws Exception {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var first = mock(RealtimeTtsSession.class);
        var second = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("test");
        when(call.getTwilioCallSid()).thenReturn("failed-hybrid");
        when(call.getLanguageDetected()).thenReturn("en");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:english-voice");
        when(repository.findByTwilioCallSid("failed-hybrid")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        when(provider.open(eq("en"), eq("cartesia:english-voice"), any()))
                .thenReturn(CompletableFuture.completedFuture(first), CompletableFuture.completedFuture(second));
        var listener = ArgumentCaptor.forClass(TtsAudioListener.class);
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("failed-hybrid", agentId.toString(), socket);
        verify(provider).open(eq("en"), eq("cartesia:english-voice"), listener.capture());
        service.accept("failed-hybrid", "{\"type\":\"speak\",\"generation\":0,"
                + "\"id\":\"failed\",\"text\":\"This response starts and then fails.\"}");
        listener.getValue().onPcmAudio(new byte[] {1, 2, 3, 4});
        listener.getValue().onError(new IllegalStateException("provider disconnected"));
        service.accept("failed-hybrid", "{\"type\":\"speak\",\"generation\":1,"
                + "\"id\":\"retry\",\"text\":\"This is the next caller turn.\"}");

        verify(first).speak("This response starts and then fails.", true);
        verify(first).close();
        verify(provider, times(2)).open(eq("en"), eq("cartesia:english-voice"), any());
        verify(second).speak("This is the next caller turn.", true);
        var events = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, atLeastOnce()).sendMessage(events.capture());
        assertThat(events.getAllValues()).extracting(TextMessage::getPayload)
                .anyMatch(payload -> payload.contains("\"type\":\"speaking\"")
                        && payload.contains("\"value\":false"))
                .anyMatch(payload -> payload.contains("\"type\":\"error\""));
    }

    @Test
    void browserPlaybackStallClearsSpeakingAndReopensCartesia() throws Exception {
        var repository = mock(CallRepository.class);
        var provider = mock(RealtimeTextToSpeechProvider.class);
        var first = mock(RealtimeTtsSession.class);
        var second = mock(RealtimeTtsSession.class);
        var socket = mock(WebSocketSession.class);
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        var agentId = UUID.randomUUID();
        when(call.isActive()).thenReturn(true);
        when(call.getDirection()).thenReturn("web");
        when(call.getTwilioCallSid()).thenReturn("stalled-playback");
        when(call.getLanguageDetected()).thenReturn("en");
        when(call.getAgent()).thenReturn(agent);
        when(agent.getId()).thenReturn(agentId);
        when(agent.getTtsVoiceId()).thenReturn("cartesia:english-voice");
        when(repository.findByTwilioCallSid("stalled-playback")).thenReturn(Optional.of(call));
        when(socket.isOpen()).thenReturn(true);
        when(provider.open(eq("en"), eq("cartesia:english-voice"), any()))
                .thenReturn(CompletableFuture.completedFuture(first), CompletableFuture.completedFuture(second));
        var listener = ArgumentCaptor.forClass(TtsAudioListener.class);
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("stalled-playback", agentId.toString(), socket);
        verify(provider).open(eq("en"), eq("cartesia:english-voice"), listener.capture());
        service.accept("stalled-playback", "{\"type\":\"speak\",\"generation\":0,"
                + "\"id\":\"active\",\"text\":\"This response started speaking.\"}");
        listener.getValue().onPcmAudio(new byte[] {1, 2, 3, 4});

        service.accept("stalled-playback", "{\"type\":\"playback_stalled\"}");

        verify(first).close();
        verify(provider, times(2)).open(eq("en"), eq("cartesia:english-voice"), any());
        var events = ArgumentCaptor.forClass(TextMessage.class);
        verify(socket, atLeastOnce()).sendMessage(events.capture());
        assertThat(events.getAllValues()).extracting(TextMessage::getPayload)
                .anyMatch(payload -> payload.contains("\"type\":\"clear_audio\""))
                .anyMatch(payload -> payload.contains("\"type\":\"speaking\"")
                        && payload.contains("\"value\":false"));
    }
}
