package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebVoiceSpeechBufferTest {
    @Test
    void holdsTinyTokenFragmentsUntilTheyFormASpeakablePhrase() {
        var buffer = new StringBuilder("Bonjour, je peux vous aider avec votre rendez-vous, quelle date préférez-vous ? ");

        var phrase = WebVoiceSessionService.takeSpeakablePhrase(buffer, false);

        assertThat(phrase).isEqualTo("Bonjour, je peux vous aider avec votre rendez-vous, quelle date préférez-vous ? ");
        assertThat(buffer).isEmpty();
    }

    @Test
    void flushesTheFinalFragmentWithoutDroppingWords() {
        var buffer = new StringBuilder(" avec votre rendez-vous aujourd'hui.");

        var phrase = WebVoiceSessionService.takeSpeakablePhrase(buffer, true);

        assertThat(phrase).isEqualTo("avec votre rendez-vous aujourd'hui. ");
        assertThat(buffer).isEmpty();
    }

    @Test
    void doesNotSplitSpokenTimeAbbreviations() {
        var buffer = new StringBuilder("Wednesday at 3 P.M. works. Would you like that slot? ");

        var phrase = WebVoiceSessionService.takeSpeakablePhrase(buffer, false);

        assertThat(phrase).isEqualTo("Wednesday at 3 P.M. works. ");
        assertThat(buffer.toString()).isEqualTo("Would you like that slot? ");
    }
}
