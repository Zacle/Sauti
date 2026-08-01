package com.sauti.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

class AiPhoneNumberEntityExtractorTest {
    @Test
    void buildsThePhoneFromTheOrderedSourceDigitsNotTheModelsCandidate() {
        var provider = mock(LlmToolCallingProvider.class);
        when(provider.completeTurn(any())).thenReturn(new LlmToolTurnResponse(
                "",
                List.of(new LlmToolCall(
                        "phone-1",
                        "return_phone_digit_sequence",
                        Map.of(
                                "status", "complete",
                                "digits", List.of("0", "1", "1", "5", "7", "5", "2", "4", "4", "1")
                        )
                ))
        ));

        assertThat(new AiPhoneNumberEntityExtractor(provider).extract(
                call(),
                "zéro un un cinq sept cinq deux quatre quatre un",
                "01157524441"
        )).isEqualTo("0115752441");
    }

    @Test
    void failsClosedForAnUnclearSequence() {
        var provider = mock(LlmToolCallingProvider.class);
        when(provider.completeTurn(any())).thenReturn(new LlmToolTurnResponse(
                "",
                List.of(new LlmToolCall(
                        "phone-1",
                        "return_phone_digit_sequence",
                        Map.of("status", "unclear", "digits", List.of())
                ))
        ));

        assertThat(new AiPhoneNumberEntityExtractor(provider).extract(
                call(), "zero a a sank", "0115"
        )).isEmpty();
    }

    @Test
    void exposesClearIncompleteDigitsWithoutTreatingThemAsACompletePhone() {
        var provider = mock(LlmToolCallingProvider.class);
        when(provider.completeTurn(any())).thenReturn(new LlmToolTurnResponse(
                "",
                List.of(new LlmToolCall(
                        "phone-1",
                        "return_phone_digit_sequence",
                        Map.of("status", "incomplete", "digits", List.of("0", "1", "0"))
                ))
        ));
        var extractor = new AiPhoneNumberEntityExtractor(provider);

        assertThat(extractor.extractSequence(call(), "zero one zero", "010"))
                .isEqualTo(new PhoneNumberEntityExtractor.Extraction("incomplete", "010"));
        assertThat(extractor.extract(call(), "zero one zero", "010")).isEmpty();
    }

    @Test
    void doesNotTrustTheModelsCandidateWhenSourceEvidenceIsMissing() {
        var provider = mock(LlmToolCallingProvider.class);

        assertThat(new AiPhoneNumberEntityExtractor(provider).extract(
                call(), "", "01157524441"
        )).isEmpty();
        verify(provider, never()).completeTurn(any());
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
