package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class VoiceOutputGuardTest {

    @Test
    void detectsAndParsesProviderToolArguments() {
        var payload = "```json\n{\"date\":\"2026-07-18\",\"time_preference\":\"midi\"}\n```";

        assertThat(VoiceOutputGuard.isStructuredPayload(payload)).isTrue();
        assertThat(VoiceOutputGuard.parseObject(new ObjectMapper(), payload))
                .hasValueSatisfying(arguments -> assertThat(arguments)
                        .containsEntry("date", "2026-07-18")
                        .containsEntry("time_preference", "midi"));
    }

    @Test
    void keepsNaturalSpokenTextOutOfTheProtocolPath() {
        assertThat(VoiceOutputGuard.isStructuredPayload(
                "Je verifie les disponibilites pour demain midi."
        )).isFalse();
    }

    @Test
    void detectsLeakedModelChannelAndFunctionMarkers() {
        var leaked = "analysis to=functions.get_business_hours code";

        assertThat(VoiceOutputGuard.isProtocolPayload(leaked)).isTrue();
        assertThat(VoiceOutputGuard.classifyStreamingPrefix("ana"))
                .isEqualTo(VoiceOutputGuard.StreamDisposition.UNDECIDED);
        assertThat(VoiceOutputGuard.classifyStreamingPrefix("analysis "))
                .isEqualTo(VoiceOutputGuard.StreamDisposition.UNDECIDED);
        assertThat(VoiceOutputGuard.classifyStreamingPrefix(leaked))
                .isEqualTo(VoiceOutputGuard.StreamDisposition.PROTOCOL);
    }

    @Test
    void releasesNaturalSpeechThatOnlyResemblesAProtocolPrefix() {
        assertThat(VoiceOutputGuard.classifyStreamingPrefix("Analysis of your request is complete."))
                .isEqualTo(VoiceOutputGuard.StreamDisposition.SPEECH);
        assertThat(VoiceOutputGuard.isProtocolPayload("Analysis of your request is complete."))
                .isFalse();
    }

    @Test
    void createsRealtimeCallIdsWithinTheProviderLimit() {
        var callId = VoiceOutputGuard.realtimeCallId("recovered-availability");

        assertThat(callId).hasSizeLessThanOrEqualTo(32)
                .matches("[A-Za-z0-9_-]+");
    }
}
