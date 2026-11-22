package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A search result shows the words it was found in, with who said them. */
class CallSearchTest {
    @Test
    void showsTheTranscriptLineThatMatched() {
        var transcript = "Agent: Hello, Bright Smile.\nCaller: Are you open on Sunday?\nAgent: We are closed.\n";

        assertThat(search("SUNDAY").excerpt(transcript, "Weekend hours."))
                .isEqualTo("Caller: Are you open on Sunday?");
        assertThat(search("weekend").excerpt(transcript, "Weekend hours.")).isEqualTo("Weekend hours.");
        assertThat(search("+2547").excerpt(transcript, "Weekend hours.")).isNull();
        assertThat(search("").excerpt(transcript, "Weekend hours.")).isNull();
    }

    @Test
    void cutsALongLineAroundTheMatchAndKeepsTheSpeaker() {
        var line = "Caller: " + "well ".repeat(60) + "I would like to move my whitening appointment " + "please ".repeat(40);

        var excerpt = search("whitening").excerpt(line, null);

        assertThat(excerpt).startsWith("Caller: …").endsWith("…").contains("whitening appointment");
        assertThat(excerpt.length()).isLessThanOrEqualTo(CallSearch.EXCERPT + 2);
    }

    @Test
    void wildcardsAreMatchedLiterally() {
        assertThat(search("100%").pattern()).isEqualTo("%100!%%");
        assertThat(search("a_b!").pattern()).isEqualTo("%a!_b!!%");
        assertThat(search("  ").pattern()).isNull();
    }

    private static CallSearch search(String text) {
        return new CallSearch(text, null, null, null, null, null, List.of());
    }
}
