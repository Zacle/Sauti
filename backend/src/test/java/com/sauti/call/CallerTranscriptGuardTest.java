package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallerTranscriptGuardTest {
    @Test
    void rejectsEmptyAndNonSpeechCaptions() {
        assertThat(CallerTranscriptGuard.accepts(null)).isFalse();
        assertThat(CallerTranscriptGuard.accepts("   ")).isFalse();
        assertThat(CallerTranscriptGuard.accepts("[silence]")).isFalse();
        assertThat(CallerTranscriptGuard.accepts("(background noise)")).isFalse();
        assertThat(CallerTranscriptGuard.accepts("...")).isFalse();
    }

    @Test
    void keepsShortButRealCallerReplies() {
        assertThat(CallerTranscriptGuard.accepts("Oui.")).isTrue();
        assertThat(CallerTranscriptGuard.accepts("Mhm.")).isTrue();
        assertThat(CallerTranscriptGuard.accepts("Hello? ")).isTrue();
        assertThat(CallerTranscriptGuard.accepts("01115753441")).isTrue();
    }
}
