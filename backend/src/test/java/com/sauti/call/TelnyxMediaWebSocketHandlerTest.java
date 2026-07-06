package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
class TelnyxMediaWebSocketHandlerTest {
    @Mock
    CallRepository callRepository;
    @Mock
    TwilioMediaStreamService mediaStreamService;

    TelnyxMediaWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelnyxMediaWebSocketHandler(new ObjectMapper(), callRepository, mediaStreamService);
    }

    @Test
    void startsAnL16MediaSessionFromTelnyxMetadata() throws Exception {
        var session = session("v3:call-control");
        when(callRepository.findByTwilioCallSid("v3:call-control"))
                .thenReturn(Optional.of(activeCall("v3:call-control")));

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "event": "start",
                  "stream_id": "stream-123",
                  "start": {
                    "call_control_id": "v3:call-control",
                    "media_format": {"encoding": "L16", "sample_rate": 16000, "channels": 1}
                  }
                }
                """));

        assertThat(session.getAttributes())
                .containsEntry("telnyxStreamId", "stream-123")
                .containsEntry("telnyxCallControlId", "v3:call-control");
        verify(mediaStreamService).start(
                eq("v3:call-control"),
                eq("stream-123"),
                argThat(format -> "L16".equals(format.encoding())
                        && format.sampleRate() == 16000
                        && format.channels() == 1),
                argThat(parameters -> "telnyx".equals(parameters.get("_mediaProvider"))),
                any(TwilioOutboundMediaSender.class)
        );
    }

    @Test
    void decodesMediaAndForwardsDtmf() throws Exception {
        var session = session("call-123");
        session.getAttributes().put("telnyxCallControlId", "call-123");
        session.getAttributes().put("telnyxStreamId", "stream-123");
        when(callRepository.findByTwilioCallSid("call-123")).thenReturn(Optional.of(activeCall("call-123")));

        handler.handleTextMessage(session, new TextMessage("""
                {
                  "event": "media",
                  "stream_id": "stream-123",
                  "sequence_number": "4",
                  "media": {"chunk": "2", "timestamp": "40", "payload": "AQIDBA=="}
                }
                """));
        handler.handleTextMessage(session, new TextMessage("""
                {"event": "dtmf", "dtmf": {"digit": "7"}}
                """));

        var audio = ArgumentCaptor.forClass(byte[].class);
        verify(mediaStreamService).acceptInboundAudio(
                eq("call-123"), eq("stream-123"), eq("4"), eq("2"), eq("40"), audio.capture()
        );
        assertThat(audio.getValue()).containsExactly(1, 2, 3, 4);
        verify(mediaStreamService).acceptDtmf("call-123", "7");
    }

    @Test
    void closedSocketCleansUpMediaWithoutCompletingTheCall() {
        var session = session("call-123");
        session.getAttributes().put("telnyxCallControlId", "call-123");
        session.getAttributes().put("telnyxStreamId", "stream-123");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(mediaStreamService).stop("call-123", "stream-123");
    }

    @Test
    void blankPayloadIsIgnored() throws Exception {
        var session = session("call-123");
        session.getAttributes().put("telnyxCallControlId", "call-123");
        when(callRepository.findByTwilioCallSid("call-123")).thenReturn(Optional.of(activeCall("call-123")));

        handler.handleTextMessage(session, new TextMessage("""
                {"event": "media", "media": {"payload": ""}}
                """));

        verify(mediaStreamService, never()).acceptInboundAudio(any(), any(), any(), any(), any(), any());
    }

    private WebSocketSession session(String callControlId) {
        var session = mock(WebSocketSession.class);
        lenient().when(session.getAttributes()).thenReturn(new HashMap<>());
        lenient().when(session.getUri())
                .thenReturn(URI.create("ws://localhost/ws/telnyx/media/" + callControlId));
        return session;
    }

    private Call activeCall(String callControlId) {
        var tenant = new Tenant("Demo Clinic", "owner@example.com", "CD");
        var agent = new Agent(tenant, "Amina", "Bonjour", "Prompt");
        agent.activate();
        return new Call(tenant, agent, callControlId, "+243990000000", "inbound");
    }
}
