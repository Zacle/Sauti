package com.sauti.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sauti.agent.Agent;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolCallingProvider;
import com.sauti.llm.LlmToolTurnResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiPersonNameEntityExtractorTest {
    @Test
    void extractsOnlyTheStructuredNameEntityInAnyCallerLanguage() {
        var provider = mock(LlmToolCallingProvider.class);
        when(provider.completeTurn(any())).thenReturn(new LlmToolTurnResponse(
                "",
                List.of(new LlmToolCall(
                        "name-1",
                        "return_person_name_entity",
                        Map.of("status", "complete", "name", "Zachary")
                ))
        ));
        var call = call();

        assertThat(new AiPersonNameEntityExtractor(provider).extract(
                call, "mon nom c'est zachary"
        )).isEqualTo("Zachary");
    }

    @Test
    void failsClosedWhenTheModelDoesNotReturnACompleteName() {
        var provider = mock(LlmToolCallingProvider.class);
        when(provider.completeTurn(any())).thenReturn(new LlmToolTurnResponse(
                "",
                List.of(new LlmToolCall(
                        "name-1",
                        "return_person_name_entity",
                        Map.of("status", "incomplete", "name", "")
                ))
        ));

        assertThat(new AiPersonNameEntityExtractor(provider).extract(call(), "my name is"))
                .isEmpty();
    }

    private Call call() {
        var call = mock(Call.class);
        var agent = mock(Agent.class);
        when(call.getId()).thenReturn(UUID.randomUUID());
        when(call.getAgent()).thenReturn(agent);
        when(call.getLanguageDetected()).thenReturn("fr");
        when(agent.getId()).thenReturn(UUID.randomUUID());
        when(agent.getDefaultLanguage()).thenReturn("fr");
        when(agent.getEscalationPhrases()).thenReturn(List.of());
        return call;
    }
}
