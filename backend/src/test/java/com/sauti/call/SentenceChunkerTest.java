package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentenceChunkerTest {
    private final SentenceChunker chunker = new SentenceChunker();

    @Test
    void splitsOnSentenceBoundaries() {
        assertThat(chunker.chunks("Hello. I can help! What time works?"))
                .containsExactly("Hello. ", "I can help! ", "What time works? ");
    }

    @Test
    void ignoresBlankText() {
        assertThat(chunker.chunks("  ")).isEmpty();
    }

    @Test
    void keepsTextWithoutPunctuation() {
        assertThat(chunker.chunks("Je peux vous aider"))
                .containsExactly("Je peux vous aider ");
    }

    @Test
    void splitsLongSentencesAtWhitespaceInsteadOfInsideWords() {
        var text = ("This is a natural spoken phrase with several words and a comfortable rhythm ").repeat(4);

        assertThat(chunker.chunks(text))
                .hasSizeGreaterThan(1)
                .allSatisfy(chunk -> {
                    assertThat(chunk).endsWith(" ");
                    assertThat(chunk.trim()).doesNotEndWith("sever");
                });
    }

    @Test
    void splitsLongClausesAtNaturalPunctuation() {
        var text = "I hear you, and that makes sense because this is a fairly detailed request, "
                + "so let me check the next useful step for you.";

        assertThat(chunker.chunks(text))
                .containsExactly(
                        "I hear you, and that makes sense because this is a fairly detailed request, ",
                        "so let me check the next useful step for you. "
                );
    }

    @Test
    void doesNotSplitKnownAbbreviations() {
        assertThat(chunker.chunks("Book for Mr. Smith. Dr. Diallo is available. M. Diallo veut un rendez-vous."))
                .containsExactly("Book for Mr. Smith. ", "Dr. Diallo is available. ", "M. Diallo veut un rendez-vous. ");
    }

    @Test
    void keepsConsecutivePunctuationTogether() {
        assertThat(chunker.chunks("Really?! I can help."))
                .containsExactly("Really?! ", "I can help. ");
    }
}
