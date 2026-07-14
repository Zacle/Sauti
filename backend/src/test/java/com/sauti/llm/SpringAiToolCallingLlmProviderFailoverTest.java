package com.sauti.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class SpringAiToolCallingLlmProviderFailoverTest {

    @Test
    void completeTurnUsesOpenAiWithoutCallingGeminiWhenPrimarySucceeds() {
        var openAi = mock(ChatModel.class);
        var gemini = mock(ChatModel.class);
        var primaryResponse = response("OpenAI primary");
        when(openAi.call(any(Prompt.class))).thenReturn(primaryResponse);
        var provider = provider(openAi, gemini);

        var result = provider.completeTurn(context());

        assertThat(result.responseText()).isEqualTo("OpenAI primary");
        verify(openAi).call(any(Prompt.class));
        verify(gemini, never()).call(any(Prompt.class));
    }

    @Test
    void completeTurnFallsBackToGeminiWhenOpenAiFails() {
        var openAi = mock(ChatModel.class);
        var gemini = mock(ChatModel.class);
        var fallbackResponse = response("Gemini fallback");
        when(openAi.call(any(Prompt.class))).thenThrow(new IllegalStateException("OpenAI unavailable"));
        when(gemini.call(any(Prompt.class))).thenReturn(fallbackResponse);
        var provider = provider(openAi, gemini);

        var result = provider.completeTurn(context());

        assertThat(result.responseText()).isEqualTo("Gemini fallback");
        verify(openAi).call(any(Prompt.class));
        verify(gemini).call(any(Prompt.class));
    }

    @Test
    void streamTurnFallsBackBeforeOpenAiEmitsText() {
        var openAi = mock(ChatModel.class);
        var gemini = mock(ChatModel.class);
        var fallbackResponse = response("Gemini stream");
        when(openAi.stream(any(Prompt.class))).thenReturn(Flux.error(new IllegalStateException("OpenAI unavailable")));
        when(gemini.stream(any(Prompt.class))).thenReturn(Flux.just(fallbackResponse));
        var provider = provider(openAi, gemini);
        var deltas = new ArrayList<String>();

        var result = provider.streamTurn(context(), deltas::add);

        assertThat(result.responseText()).isEqualTo("Gemini stream");
        assertThat(deltas).containsExactly("Gemini stream");
    }

    private SpringAiToolCallingLlmProvider provider(ChatModel openAi, ChatModel gemini) {
        return new SpringAiToolCallingLlmProvider(
                new ObjectMapper(),
                "gpt-5.4-mini",
                "gemini-3.1-flash-lite",
                openAi,
                gemini
        );
    }

    private LlmToolTurnContext context() {
        return new LlmToolTurnContext(
                new AgentContext(UUID.randomUUID(), "Sarah", true, "UTC", null, List.of(), "standard"),
                "You are a concise voice agent.",
                "en",
                List.of(new ConversationMessage("user", "Hello", List.of(), null)),
                "Hello",
                "+15551234567",
                UUID.randomUUID(),
                "test-call",
                List.of(),
                List.of()
        );
    }

    private ChatResponse response(String text) {
        var response = mock(ChatResponse.class);
        var generation = mock(Generation.class);
        when(generation.getOutput()).thenReturn(AssistantMessage.builder().content(text).build());
        when(response.getResult()).thenReturn(generation);
        return response;
    }
}
