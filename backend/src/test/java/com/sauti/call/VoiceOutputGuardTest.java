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
}
