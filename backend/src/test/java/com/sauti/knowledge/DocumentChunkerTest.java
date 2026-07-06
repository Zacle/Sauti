package com.sauti.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentChunkerTest {
    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void createsBoundedOverlappingChunksWithoutDroppingContent() {
        var first = "first-section " + "policy ".repeat(180);
        var second = "second-section " + "appointment ".repeat(160);

        var chunks = chunker.chunks(first + "\n\n" + second);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length())
                .isLessThanOrEqualTo(DocumentChunker.CHUNK_SIZE));
        assertThat(String.join(" ", chunks)).contains("first-section", "second-section");
        assertThat(chunks.get(0).substring(chunks.get(0).length() - 80))
                .isSubstringOf(chunks.get(1));
    }
}
