package com.sauti.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sauti.session.CallSessionStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalToolCallingLlmProviderTest {
    private final LocalToolCallingLlmProvider provider =
            new LocalToolCallingLlmProvider(mock(CallSessionStore.class));

    @Test
    void respondsInSwahili() {
        assertThat(provider.completeTurn(context("sw", "Habari")).responseText())
                .contains("Ninaweza kukusaidiaje");
    }

    @Test
    void respondsInArabic() {
        assertThat(provider.completeTurn(context("ar", "مرحبا")).responseText())
                .contains("كيف يمكنني مساعدتك");
    }

    private LlmToolTurnContext context(String language, String transcript) {
        return new LlmToolTurnContext(
                new AgentContext(
                        UUID.randomUUID(),
                        "Amina",
                        false,
                        "Africa/Nairobi",
                        null,
                        List.of("speak to a human"),
                        "standard"
                ),
                "Prompt",
                language,
                List.of(new ConversationMessage("user", transcript)),
                transcript,
                "+254700000000",
                UUID.randomUUID(),
                "CA123",
                List.of(),
                List.of(),
                null
        );
    }
}
