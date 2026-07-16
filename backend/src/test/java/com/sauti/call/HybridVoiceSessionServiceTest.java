package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
        var service = new HybridVoiceSessionService(
                repository, provider, new ObjectMapper(),
                new VoiceRuntimeMetrics(new SimpleMeterRegistry())
        );

        service.start("test-hybrid", agentId.toString(), socket);
        verify(provider).open(eq("fr"), eq("cartesia:french-voice"), listener.capture());
        service.accept("test-hybrid", "{\"type\":\"tts_delta\",\"text\":\"Bonjour, je peux vous aider avec votre rendez-vous. \"}");
        service.accept("test-hybrid", "{\"type\":\"tts_complete\"}");
        delayedTts.complete(tts);
        listener.getValue().onPcmAudio(new byte[] {1, 2, 3, 4});
        listener.getValue().onComplete();

        var writes = inOrder(tts);
        writes.verify(tts).speak(anyString(), eq(false));
        writes.verify(tts).speak("", true);
        var binary = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(socket).sendMessage(binary.capture());
        assertThat(binary.getValue().getPayloadLength()).isEqualTo(4);
        verify(socket, atLeastOnce()).sendMessage(any(TextMessage.class));
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
        service.accept("interrupt-hybrid", "{\"type\":\"interrupt\"}");

        verify(first).close();
        verify(provider, times(2)).open(eq("en"), eq("cartesia:english-voice"), any());
    }
}
