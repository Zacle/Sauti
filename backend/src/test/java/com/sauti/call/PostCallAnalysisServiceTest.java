package com.sauti.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.billing.CommunicationUsageMeteringService;
import com.sauti.integration.PostCallIntegrationService;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;

/** A call is summarised by whichever model answers, and the answer is kept within bounds. */
class PostCallAnalysisServiceTest {
    private static final PostCallAnalysisService.AnalysisRequest REQUEST = new PostCallAnalysisService.AnalysisRequest(
            UUID.randomUUID(), "completed", "Caller: Can I book a cleaning?\nAgent: Booked for Monday.\n",
            Set.of("summary", "successful", "sentiment", "intent"));

    @Test
    void theSecondModelAnswersWhenTheFirstFails() throws Exception {
        var failing = mock(ChatModel.class);
        when(failing.call(any(Prompt.class))).thenThrow(new IllegalStateException("quota"));
        var working = answering("""
                ```json
                {"summary":"Booked a cleaning for Monday.","successful":true,"sentiment":"Happy","intent":"Book a Cleaning!"}
                ```""");

        var result = service(List.of(failing, working)).analyze(REQUEST);

        assertThat(result.summary()).isEqualTo("Booked a cleaning for Monday.");
        assertThat(result.successful()).isTrue();
        // Outside the allowed set: kept neutral rather than stored as given.
        assertThat(result.sentiment()).isEqualTo("neutral");
        assertThat(result.intent()).isEqualTo("book_a_cleaning");
    }

    @Test
    void theFirstAnswerIsUsed() throws Exception {
        var first = answering("{\"summary\":\"First.\",\"successful\":true,\"sentiment\":\"positive\",\"intent\":\"book\"}");
        var second = mock(ChatModel.class);

        assertThat(service(List.of(first, second)).analyze(REQUEST).summary()).isEqualTo("First.");
        verify(second, never()).call(any(Prompt.class));
    }

    @Test
    void failsWhenEveryModelFailsAndSkipsWithoutAny() {
        var failing = mock(ChatModel.class);
        when(failing.call(any(Prompt.class))).thenThrow(new IllegalStateException("down"));

        assertThatThrownBy(() -> service(List.of(failing)).analyze(REQUEST)).hasMessage("down");
        assertThatThrownBy(() -> service(List.of()).analyze(REQUEST)).isInstanceOf(IllegalStateException.class);
        assertThat(PostCallAnalysisService.models("", "gemini-3.1-flash-lite", "", "gpt-5.4-mini")).isEmpty();
        assertThat(PostCallAnalysisService.models("key", "gemini-3.1-flash-lite", "key", "gpt-5.4-mini")).hasSize(2);
        assertThat(PostCallAnalysisService.models("", "gemini-3.1-flash-lite", "key", "gpt-5.4-mini")).hasSize(1);
    }

    private static ChatModel answering(String text) {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return model;
    }

    @SuppressWarnings("unchecked")
    private static PostCallAnalysisService service(List<ChatModel> models) {
        return new PostCallAnalysisService(new ObjectMapper(), mock(CallRecordJobRepository.class),
                mock(PostCallIntegrationService.class), mock(CommunicationUsageMeteringService.class),
                mock(ObjectProvider.class), models, Clock.systemUTC());
    }
}
