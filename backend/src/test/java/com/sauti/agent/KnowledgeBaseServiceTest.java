package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KnowledgeBaseServiceTest {
    private final KnowledgeBaseService service = new KnowledgeBaseService();

    @Test
    void normalizesWhitespaceAndBuildsReadableChunks() {
        var value = "  Services  \r\n\r\n\r\n  We offer consultations and follow-up visits.  ";

        assertThat(service.normalize(value))
                .isEqualTo("Services\n\nWe offer consultations and follow-up visits.");
        assertThat(service.chunks(value)).containsExactly(
                "Services\n\nWe offer consultations and follow-up visits."
        );
    }

    @Test
    void protectsThePromptBudgetAndMarksOmittedKnowledge() {
        var value = ("policy information ".repeat(600)).substring(0, KnowledgeBaseService.STORAGE_LIMIT);

        var block = service.promptBlock(value);

        assertThat(block).contains("Approved Business Knowledge");
        assertThat(block).contains("Additional knowledge omitted");
        assertThat(block.length()).isLessThan(3_500);
    }

    @Test
    void rejectsContentAboveTheStorageLimit() {
        assertThatThrownBy(() -> service.normalize("x".repeat(KnowledgeBaseService.STORAGE_LIMIT + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000 character limit");
    }
}
