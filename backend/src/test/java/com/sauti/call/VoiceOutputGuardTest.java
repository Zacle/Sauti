package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VoiceOutputGuardTest {

    @Test
    void rejectsTextualToolArgumentsInsteadOfTreatingThemAsCalls() {
        var payload = "```json\n{\"date\":\"2026-07-18\",\"time_preference\":\"midi\"}\n```";

        assertThat(VoiceOutputGuard.speechText(payload)).isEmpty();
        assertThat(VoiceOutputGuard.isProtocolPayload(payload)).isTrue();
    }

    @Test
    void detectsLeakedModelChannelAndFunctionMarkers() {
        var leaked = "analysis to=functions.get_business_hours code";

        assertThat(VoiceOutputGuard.isProtocolPayload(leaked)).isTrue();
    }

    @Test
    void keepsNaturalSpeechThatOnlyResemblesAProtocolPrefix() {
        assertThat(VoiceOutputGuard.isProtocolPayload("Analysis of your request is complete."))
                .isFalse();
    }

    @Test
    void stripsSpokenRoleWrappersWithoutChangingNaturalLabels() {
        assertThat(VoiceOutputGuard.speechText(
                "assistant: Hi Walker, a men's hairstyle costs 5 dollars."
        )).isEqualTo("Hi Walker, a men's hairstyle costs 5 dollars.");
        assertThat(VoiceOutputGuard.speechText("Assistant - Happy to help."))
                .isEqualTo("Happy to help.");
        assertThat(VoiceOutputGuard.speechText("Price: five dollars."))
                .isEqualTo("Price: five dollars.");
        assertThat(VoiceOutputGuard.speechText("Assistant manager appointments start at nine."))
                .isEqualTo("Assistant manager appointments start at nine.");
    }

    @Test
    void rejectsPrivateRolesAndGenericRoutedChannels() {
        assertThat(VoiceOutputGuard.speechText("analysis: I should inspect the tools."))
                .isEmpty();
        assertThat(VoiceOutputGuard.speechText("planner to=functions.get_business_hours code"))
                .isEmpty();
        assertThat(VoiceOutputGuard.speechText("functions.get_business_hours({})"))
                .isEmpty();
        assertThat(VoiceOutputGuard.speechText("Analysis of your request is complete."))
                .isEqualTo("Analysis of your request is complete.");
    }

    @Test
    void rejectsProtocolThatAppearsAfterANaturalPrefix() {
        assertThat(VoiceOutputGuard.speechText(
                "Hi Walker.\nanalysis to=functions.get_business_hours code"
        )).isEmpty();
        assertThat(VoiceOutputGuard.speechText(
                "Hi Walker. functions.get_business_hours({})"
        )).isEmpty();
        assertThat(VoiceOutputGuard.speechText(
                "Sure, here are the arguments: {date: tomorrow, time: noon}"
        )).isEmpty();
    }
}
