package com.sauti.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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

/** Direct edits (one detail, a tool, the timezone) are versions too, and restoring puts tools back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentEditVersionsApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void directEditsAreRecordedAndABurstSharesOneVersion() throws Exception {
        var owner = accounts.owner("Edit Clinic", "edits-" + System.nanoTime() + "@example.com", "KE");
        var agentId = create(owner);

        patchDetail(owner, agentId, "Bright Smile Westlands");
        patchDetail(owner, agentId, "Bright Smile Karen");
        mvc.perform(patch(owner.path("/agents/" + agentId + "/timezone")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timezone\":\"Africa/Nairobi\"}"))
                .andExpect(status().isOk());

        mvc.perform(get(owner.path("/agents/" + agentId + "/versions")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].kind").value("edit"))
                .andExpect(jsonPath("$[0].publishedBy").value(owner.email()))
                .andExpect(jsonPath("$[0].note").value("Changed Business name; Changed timezone to Africa/Nairobi"))
                .andExpect(jsonPath("$[0].parts[0]").value("integrations"))
                .andExpect(jsonPath("$[1].kind").value("publish"));

        // The edit version holds the newest state, so restoring it gives back the last value typed.
        mvc.perform(post(owner.path("/agents/" + agentId + "/versions/1/restore")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post(owner.path("/agents/" + agentId + "/versions/2/restore")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Africa/Nairobi"));
        mvc.perform(get(owner.path("/agents/" + agentId + "/variables")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[?(@.key=='business_name')].value").value("Bright Smile Karen"));
    }

    @Test
    void restoringAVersionTurnsItsToolsBackOn() throws Exception {
        var owner = accounts.owner("Tool Clinic", "tool-versions-" + System.nanoTime() + "@example.com", "KE");
        var agentId = create(owner);
        var tool = firstActiveTool(owner, agentId);

        mvc.perform(delete(owner.path("/agents/" + agentId + "/tools/" + tool.path("id").asText()))
                        .header("Authorization", owner.bearer()))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(get(owner.path("/agents/" + agentId + "/versions")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[0].note").value("Turned off tool " + tool.path("toolName").asText()));

        mvc.perform(post(owner.path("/agents/" + agentId + "/versions/1/restore")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk());
        mvc.perform(get(owner.path("/agents/" + agentId + "/tools/" + tool.path("id").asText()))
                        .header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(get(owner.path("/agents/" + agentId + "/versions")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[0].kind").value("publish"))
                .andExpect(jsonPath("$[0].note").value("Restored version 1"));
    }

    private JsonNode firstActiveTool(TestAccounts.Member owner, UUID agentId) throws Exception {
        var listed = mvc.perform(get(owner.path("/agents/" + agentId + "/tools")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andReturn();
        for (var tool : json.readTree(listed.getResponse().getContentAsString())) {
            if (tool.path("active").asBoolean()) return tool;
        }
        throw new AssertionError("The new agent has no active tool");
    }

    private void patchDetail(TestAccounts.Member owner, UUID agentId, String value) throws Exception {
        mvc.perform(patch(owner.path("/agents/" + agentId + "/variables/business_name"))
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"" + value + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID create(TestAccounts.Member owner) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"agent":{"name":"Front desk","greetingMessage":"Hello.",
                                  "systemPrompt":"You answer for {{business_name}}.",
                                  "defaultLanguage":"en","supportedLanguages":["en"]},
                                 "variables":[{"key":"business_name","label":"Business name",
                                   "value":"Bright Smile Dental","required":true}]}
                                """))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
    }
}
