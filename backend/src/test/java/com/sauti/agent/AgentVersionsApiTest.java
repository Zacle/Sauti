package com.sauti.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** Drafts never reach live calls; every publish is a version that can be restored. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentVersionsApiTest {
    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void aDraftWaitsWithoutChangingTheLiveAgent() throws Exception {
        var owner = accounts.owner("Draft Clinic", "draft-" + System.nanoTime() + "@example.com", "KE");
        var agentId = create(owner, "Front desk", "Bright Smile Dental");

        mvc.perform(put(owner.path("/agents/" + agentId + "/draft")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(setup("Reception", "Bright Smile Westlands")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedBy").value(owner.email()));

        mvc.perform(get(owner.path("/agents/" + agentId)).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.name").value("Front desk"));
        mvc.perform(get(owner.path("/agents/" + agentId + "/draft")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.setup.agent.name").value("Reception"))
                .andExpect(jsonPath("$.setup.values.business_name").value("Bright Smile Westlands"));

        mvc.perform(delete(owner.path("/agents/" + agentId + "/draft")).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get(owner.path("/agents/" + agentId + "/draft")).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());
    }

    @Test
    void publishingRecordsVersionsAndAnyOneCanBeRestored() throws Exception {
        var owner = accounts.owner("Version Clinic", "versions-" + System.nanoTime() + "@example.com", "KE");
        var agentId = create(owner, "Front desk", "Bright Smile Dental");
        mvc.perform(put(owner.path("/agents/" + agentId + "/draft")).header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(setup("Reception", "Bright Smile Westlands")));

        mvc.perform(put(owner.path("/agents/" + agentId + "/setup")).header("Authorization", owner.bearer())
                        .param("note", "Renamed for Westlands")
                        .contentType(MediaType.APPLICATION_JSON).content(setup("Reception", "Bright Smile Westlands")))
                .andExpect(status().isOk());

        // Publishing replaces the draft it came from.
        mvc.perform(get(owner.path("/agents/" + agentId + "/draft")).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get(owner.path("/agents/" + agentId + "/versions")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[0].note").value("Renamed for Westlands"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].note").value("Created"));

        mvc.perform(post(owner.path("/agents/" + agentId + "/versions/1/restore")).header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Front desk"));
        mvc.perform(get(owner.path("/agents/" + agentId + "/variables")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[?(@.key=='business_name')].value").value("Bright Smile Dental"));
        mvc.perform(get(owner.path("/agents/" + agentId + "/versions")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[0].version").value(3))
                .andExpect(jsonPath("$[0].note").value("Restored version 1"));
    }

    @Test
    void membersCanSeeHistoryButNotRestore() throws Exception {
        var owner = accounts.owner("Roles Versions", "roles-versions-" + System.nanoTime() + "@example.com", "KE");
        var member = accounts.member(owner.workspaceId(), "roles-versions-m-" + System.nanoTime() + "@example.com",
                com.sauti.tenant.domain.WorkspaceRole.MEMBER);
        var agentId = create(owner, "Front desk", "Bright Smile Dental");

        mvc.perform(get(member.path("/agents/" + agentId + "/versions")).header("Authorization", member.bearer()))
                .andExpect(status().isOk());
        mvc.perform(post(member.path("/agents/" + agentId + "/versions/1/restore")).header("Authorization", member.bearer()))
                .andExpect(status().isForbidden());
    }

    private UUID create(TestAccounts.Member owner, String name, String businessName) throws Exception {
        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(setup(name, businessName)))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());
    }

    private static String setup(String name, String businessName) {
        return """
                {"agent":{"name":"%s","greetingMessage":"Hello.","systemPrompt":"You answer for {{business_name}}.",
                  "defaultLanguage":"en","supportedLanguages":["en"]},
                 "variables":[{"key":"business_name","label":"Business name","value":"%s","required":true}],
                 "values":{"business_name":"%s"}}
                """.formatted(name, businessName, businessName);
    }
}
