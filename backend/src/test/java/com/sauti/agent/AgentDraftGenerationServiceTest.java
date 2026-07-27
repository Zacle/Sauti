package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AgentDraftGenerationServiceTest {
    @Test
    void localPromptGenerationIncludesDurationAndRequiredAndOptionalVariables() {
        var service = new AgentDraftGenerationService(
                new ObjectMapper(),
                "heuristic",
                "http://localhost:8090/agent-draft",
                "",
                "unused",
                ""
        );

        var draft = service.generate(
                "Create a salon receptionist that can book appointments and answer common questions."
        );

        assertThat(draft.bookingEnabled()).isTrue();
        assertThat(draft.defaultBookingDurationMinutes()).isEqualTo(60);
        assertThat(draft.variables()).anyMatch(AgentDraftGenerationDtos.GeneratedVariable::required);
        assertThat(draft.variables()).anyMatch(variable -> !variable.required());
        assertThat(draft.variables()).allSatisfy(variable ->
                assertThat(draft.systemPrompt()).contains("{{" + variable.key() + "}}")
        );
    }
}
