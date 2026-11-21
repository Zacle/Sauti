package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentDraftGenerationDtos.GeneratedAgentDraftResponse;
import com.sauti.agent.AgentDraftGenerationDtos.GeneratedVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AgentDraftValueGroundingTest {
    private static final String BRIEF =
            "Bright Smile Dental in Nairobi, Ngong Road. Open Monday to Friday 8 to 6. Cleaning costs KES 3,500.";

    @Test
    void keepsValuesTheBriefStatesAndDropsGuesses() {
        assertThat(AgentDraftGenerationService.groundedIn(BRIEF, "Bright Smile Dental")).isEqualTo("Bright Smile Dental");
        assertThat(AgentDraftGenerationService.groundedIn(BRIEF, "Cleaning – KES 3,500")).isEqualTo("Cleaning – KES 3,500");
        assertThat(AgentDraftGenerationService.groundedIn(BRIEF, "Cleaning – KES 4,000")).isEmpty();
        assertThat(AgentDraftGenerationService.groundedIn(BRIEF, "www.brightsmile.co.ke")).isEmpty();
        assertThat(AgentDraftGenerationService.groundedIn(BRIEF, "")).isEmpty();
        assertThat(AgentDraftGenerationService.groundedIn(BRIEF, null)).isEmpty();
    }

    @Test
    void theDraftCarriesGroundedValuesAndKeepsEveryPlaceholder() {
        var placeholders = new ArrayList<String>();
        var variables = new ArrayList<GeneratedVariable>();
        IntStream.range(0, 25).forEach(index -> {
            placeholders.add("{{detail_" + index + "}}");
            variables.add(new GeneratedVariable("detail_" + index, "Detail " + index, "", index == 0, ""));
        });
        variables.add(new GeneratedVariable("business_name", "Business name", "", true, "Bright Smile Dental"));
        variables.add(new GeneratedVariable("website", "Website", "", false, "www.invented.example"));
        var draft = draft("You are {{agent_name}} for {{business_name}}. Site {{website}}. "
                + String.join(" ", placeholders), variables);

        var validated = service().validateGenerated(draft, BRIEF);

        assertThat(validated.variables()).hasSize(27);
        assertThat(validated.variables()).filteredOn(variable -> variable.key().equals("business_name"))
                .extracting(GeneratedVariable::value).containsExactly("Bright Smile Dental");
        assertThat(validated.variables()).filteredOn(variable -> variable.key().equals("website"))
                .extracting(GeneratedVariable::value).containsExactly("");
    }

    @Test
    void refusesADraftWithMoreDetailsThanAnOwnerCouldReview() {
        var placeholders = IntStream.range(0, 70).mapToObj(index -> "{{detail_" + index + "}}").toList();

        assertThatThrownBy(() -> service().validateGenerated(draft(String.join(" ", placeholders), List.of()), BRIEF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than 60");
    }

    private static GeneratedAgentDraftResponse draft(String prompt, List<GeneratedVariable> variables) {
        return new GeneratedAgentDraftResponse("Front desk", "", "Greet once as {{agent_name}}.", prompt,
                false, 60, "en", List.of("en"), List.of(), variables);
    }

    private static AgentDraftGenerationService service() {
        return new AgentDraftGenerationService(new ObjectMapper(), "heuristic", "", "", "gemini", "");
    }
}
