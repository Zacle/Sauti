package com.sauti.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** One request saves an agent with all its business details, and the preview shows what callers get. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentSetupApiTest {
    private static final String PROMPT = "You answer for {{business_name}}. Our website is {{website}}. "
            + "We are at {{business_address}}.";

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired AgentRepository agents;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void createsTheAgentAndEveryVariableWithItsValueInOneRequest() throws Exception {
        var owner = accounts.owner("Setup Clinic", "setup-" + System.nanoTime() + "@example.com", "KE");

        var agentId = create(owner, """
                [{"key":"business_name","label":"Business name","value":"Bright Smile Dental","required":true},
                 {"key":"website","label":"Website","value":"","required":false}]
                """);

        mvc.perform(get(owner.path("/agents/" + agentId + "/variables")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[?(@.key=='business_name')].value").value("Bright Smile Dental"))
                .andExpect(jsonPath("$[?(@.key=='website')].required").value(false))
                // Not sent by the client, but used by the prompt: still defined, and required.
                .andExpect(jsonPath("$[?(@.key=='business_address')].required").value(true));
    }

    @Test
    void updatingSetsValuesForExistingDetails() throws Exception {
        var owner = accounts.owner("Update Clinic", "update-setup-" + System.nanoTime() + "@example.com", "KE");
        var agentId = create(owner, "[]");

        mvc.perform(put(owner.path("/agents/" + agentId + "/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agent":%s,"values":{"business_name":"Bright Smile Dental","unknown_key":"ignored"}}
                                """.formatted(agentJson())))
                .andExpect(status().isOk());

        mvc.perform(get(owner.path("/agents/" + agentId + "/variables")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[?(@.key=='business_name')].value").value("Bright Smile Dental"));
    }

    @Test
    void thePreviewShowsTheFinalInstructionsAndWhatIsMissing() throws Exception {
        var owner = accounts.owner("Preview Clinic", "preview-" + System.nanoTime() + "@example.com", "KE");
        var agentId = create(owner, """
                [{"key":"business_name","label":"Business name","value":"Bright Smile Dental","required":true},
                 {"key":"website","label":"Website","value":"","required":false}]
                """);

        var response = mvc.perform(get(owner.path("/agents/" + agentId + "/instructions-preview"))
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.tools").isArray())
                .andReturn().getResponse().getContentAsString();
        var preview = json.readTree(response);

        assertThat(preview.path("instructions").asText())
                .contains("You answer for Bright Smile Dental.")
                .doesNotContain("Our website is")
                .doesNotContain("{{");
        assertThat(preview.path("warnings").findValuesAsText("kind"))
                .contains("empty_optional", "missing_required");
        // Every tool is listed with what it may do; anything that changes something asks the caller first.
        assertThat(preview.path("toolRules").findValuesAsText("name")).contains("update_conversation_state");
        preview.path("toolRules").forEach(rule -> {
            if (!"read_only".equals(rule.path("effect").asText())) {
                assertThat(rule.path("confirmation").asText()).isNotEqualTo("none");
            }
        });
    }

    private UUID create(TestAccounts.Member owner, String variables) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(variables)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
    }

    static String body(String variables) {
        return "{\"agent\":" + agentJson() + ",\"variables\":" + variables + "}";
    }

    private static String agentJson() {
        return """
                {"name":"Front desk","greetingMessage":"Hello.","systemPrompt":"%s",
                 "defaultLanguage":"en","supportedLanguages":["en"]}
                """.formatted(PROMPT);
    }
}
