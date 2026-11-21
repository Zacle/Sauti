package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.testsupport.TestAccounts;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Placeholders in a hand-written prompt are checked on save and always have a variable behind them. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class AgentPlaceholderApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired ApplicationEvents events;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void aManualAgentGetsARequiredVariableForEachPlaceholderItUses() throws Exception {
        var owner = accounts.owner("Bright Smile", "placeholders-" + System.nanoTime() + "@example.com", "KE");

        var agentId = create(owner, "Hello from {{business_name}}.",
                "You answer for {{business_name}} at {{business_address}}. Open {{business_hours}}.");

        mvc.perform(get(owner.path("/agents/" + agentId + "/variables")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.key=='business_name')].required").value(true))
                .andExpect(jsonPath("$[?(@.key=='business_address')].label").value("Business address"));

        // A client that then sends the full definition refines the automatic one instead of failing.
        mvc.perform(post(owner.path("/agents/" + agentId + "/variables")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"business_address","label":"Clinic address","value":"12 Ngong Road","required":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Clinic address"))
                .andExpect(jsonPath("$.value").value("12 Ngong Road"));
    }

    @Test
    void aMalformedPlaceholderIsRejectedWhenSaving() throws Exception {
        var owner = accounts.owner("Typo Clinic", "malformed-" + System.nanoTime() + "@example.com", "KE");

        mvc.perform(post(owner.path("/agents")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agent("Hello.", "You answer for {{Business Name}}.")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingAVariableAsksForTheLiveAssistantToBeRefreshed() throws Exception {
        var owner = accounts.owner("Refresh Clinic", "refresh-" + System.nanoTime() + "@example.com", "KE");
        var agentId = create(owner, "Hello.", "You answer for {{business_name}}.");
        events.clear();

        mvc.perform(patch(owner.path("/agents/" + agentId + "/variables/business_name"))
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"Bright Smile Dental\"}"))
                .andExpect(status().isOk());

        assertThat(events.stream(AgentConfigurationChanged.class))
                .contains(new AgentConfigurationChanged(owner.workspaceId(), agentId, "Changed Business name"));
    }

    private UUID create(TestAccounts.Member owner, String greeting, String prompt) throws Exception {
        var created = mvc.perform(post(owner.path("/agents")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agent(greeting, prompt)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
    }

    private static String agent(String greeting, String prompt) {
        return """
                {"name":"Front desk","greetingMessage":"%s","systemPrompt":"%s",
                 "defaultLanguage":"en","supportedLanguages":["en"]}
                """.formatted(greeting, prompt);
    }
}
