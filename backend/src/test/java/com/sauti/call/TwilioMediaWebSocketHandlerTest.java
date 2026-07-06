package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.Agent;
import com.sauti.tenant.Tenant;
import java.net.URI;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class TwilioMediaWebSocketHandlerTest {
    @Mock
    CallRepository callRepository;

    @Mock
    CallPipelineService callPipelineService;

    @Mock
    TwilioMediaStreamService mediaStreamService;

    TwilioMediaWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TwilioMediaWebSocketHandler(new ObjectMapper(), callRepository, callPipelineService, mediaStreamService);
    }

    @Test
    void startStoresStreamMetadataAndInitializesMediaSession() throws Exception {
        var session = session("CA123");
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(activeCall("CA123")));

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "event": "start",
                  "start": {
                    "streamSid": "MZ123",
                    "callSid": "CA123",
                    "mediaFormat": {"encoding": "audio/x-mulaw", "sampleRate": 8000, "channels": 1},
                    "customParameters": {"tenantId": "tenant-1", "agentId": "agent-1"}
                  }
                }
                """));

        assertThat(session.getAttributes())
                .containsEntry("twilioStreamSid", "MZ123")
                .containsEntry("twilioCallSid", "CA123");
        verify(mediaStreamService).start(
                eq("CA123"),
                eq("MZ123"),
                argThat(format -> "audio/x-mulaw".equals(format.encoding()) && format.sampleRate() == 8000 && format.channels() == 1),
                argThat(parameters -> "tenant-1".equals(parameters.get("tenantId")) && "agent-1".equals(parameters.get("agentId"))),
                any(TwilioOutboundMediaSender.class)
        );
    }

    @Test
    void mediaFrameDecodesPayloadAndDoesNotProcessAiTurn() throws Exception {
        var session = session("CA123");
        session.getAttributes().put("twilioCallSid", "CA123");
        session.getAttributes().put("twilioStreamSid", "MZ123");
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(activeCall("CA123")));

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "event": "media",
                  "streamSid": "MZ123",
                  "sequenceNumber": "3",
                  "media": {
                    "chunk": "1",
                    "timestamp": "20",
                    "payload": "AQIDBA=="
                  }
                }
                """));

        var audioCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mediaStreamService).acceptInboundAudio(
                eq("CA123"),
                eq("MZ123"),
                eq("3"),
                eq("1"),
                eq("20"),
                audioCaptor.capture()
        );
        assertThat(audioCaptor.getValue()).containsExactly(1, 2, 3, 4);
        verify(callPipelineService, never()).processTurn(any(Call.class), any(byte[].class));
    }

    @Test
    void blankMediaPayloadIsIgnored() throws Exception {
        var session = session("CA123");
        session.getAttributes().put("twilioCallSid", "CA123");
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(activeCall("CA123")));

        handler.handleTextMessage(session, new TextMessage("""
                {"event": "media", "media": {"payload": ""}}
                """));

        verify(mediaStreamService, never()).acceptInboundAudio(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void invalidJsonClosesAsBadData() throws Exception {
        var session = session("CA123");

        handler.handleTextMessage(session, new TextMessage("{bad json"));

        verify(session).close(CloseStatus.BAD_DATA);
    }

    @Test
    void invalidBase64ClosesAsBadData() throws Exception {
        var session = session("CA123");
        session.getAttributes().put("twilioCallSid", "CA123");
        when(callRepository.findByTwilioCallSid("CA123")).thenReturn(Optional.of(activeCall("CA123")));

        handler.handleTextMessage(session, new TextMessage("""
                {"event": "media", "media": {"payload": "%%%"}}
                """));

        verify(session).close(CloseStatus.BAD_DATA);
    }

    @Test
    void markFrameIsForwardedToMediaSession() throws Exception {
        var session = session("CA123");
        session.getAttributes().put("twilioCallSid", "CA123");
        session.getAttributes().put("twilioStreamSid", "MZ123");

        handler.handleTextMessage(session, new TextMessage("""
                {"event": "mark", "streamSid": "MZ123", "mark": {"name": "turn-1-end"}}
                """));

        verify(mediaStreamService).markReceived("CA123", "MZ123", "turn-1-end");
    }

    @Test
    void stopCompletesCallAndClosesNormally() throws Exception {
        var session = session("CA123");
        session.getAttributes().put("twilioCallSid", "CA123");
        session.getAttributes().put("twilioStreamSid", "MZ123");

        handler.handleTextMessage(session, new TextMessage("""
                {"event": "stop", "streamSid": "MZ123"}
                """));

        verify(mediaStreamService, never()).stop(any(), any());
        verify(callPipelineService, never()).completeActiveCall(any(), any());
        verify(session).close(CloseStatus.NORMAL);
    }

    @Test
    void connectionClosedPerformsCleanupOnce() {
        var session = session("CA123");
        session.getAttributes().put("twilioCallSid", "CA123");
        session.getAttributes().put("twilioStreamSid", "MZ123");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(mediaStreamService).stop("CA123", "MZ123");
        verify(callPipelineService).completeActiveCall("CA123", "completed");
    }

    @Test
    void missingCallClosesNormallyWithoutProcessingAudio() throws Exception {
        var session = session("CA404");
        session.getAttributes().put("twilioCallSid", "CA404");
        when(callRepository.findByTwilioCallSid("CA404")).thenReturn(Optional.empty());

        handler.handleTextMessage(session, new TextMessage("""
                {"event": "media", "streamSid": "MZ123", "media": {"payload": "AQI="}}
                """));

        verify(session).close(CloseStatus.NORMAL);
        verify(mediaStreamService, never()).acceptInboundAudio(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    private WebSocketSession session(String callSid) {
        var session = mock(WebSocketSession.class);
        lenient().when(session.getAttributes()).thenReturn(new HashMap<>());
        lenient().when(session.getUri()).thenReturn(URI.create("ws://localhost/ws/twilio/media/" + callSid));
        return session;
    }

    private Call activeCall(String callSid) {
        var tenant = new Tenant("Demo Clinic", "owner@example.com", "SN");
        var agent = new Agent(tenant, "Amina", "Bonjour", "Prompt");
        agent.activate();
        return new Call(tenant, agent, callSid, "+221771234567", "inbound");
    }
}
