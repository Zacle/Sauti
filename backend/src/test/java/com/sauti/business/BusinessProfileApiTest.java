package com.sauti.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.auth.application.AuthRateLimitService;
import com.sauti.business.application.BusinessProfileChanged;
import com.sauti.tenant.domain.WorkspaceRole;
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

/** Business facts entered once for the workspace reach every agent that leaves them empty. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class BusinessProfileApiTest {
    private static final String PROFILE = """
            {"displayName":"Bright Smile Dental","address":"Ngong Road, Nairobi","phone":"0712 345 678",
             "services":[{"name":"Cleaning","priceAmount":3500,"currency":"KES","durationMinutes":30}],
             "faqs":[{"question":"Is there parking?","answer":"Yes, behind the building."}]}
            """;

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired ObjectMapper json;
    @Autowired ApplicationEvents events;
    @MockitoBean AuthRateLimitService rateLimits;

    @Test
    void savesTypedFactsAndNormalisesThePhoneNumber() throws Exception {
        var owner = accounts.owner("Profile Clinic", "profile-" + System.nanoTime() + "@example.com", "KE");

        mvc.perform(put(owner.path("/business-profile")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+254712345678"))
                .andExpect(jsonPath("$.services[0].durationMinutes").value(30));

        mvc.perform(get(owner.path("/business-profile")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$.displayName").value("Bright Smile Dental"))
                .andExpect(jsonPath("$.faqs[0].answer").value("Yes, behind the building."));
        assertThat(events.stream(BusinessProfileChanged.class))
                .contains(new BusinessProfileChanged(owner.workspaceId()));
    }

    @Test
    void anAgentWithoutItsOwnValuesUsesTheProfile() throws Exception {
        var owner = accounts.owner("Inherit Clinic", "inherit-" + System.nanoTime() + "@example.com", "KE");
        mvc.perform(put(owner.path("/business-profile")).header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(PROFILE)).andExpect(status().isOk());

        var created = mvc.perform(post(owner.path("/agents/setup")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agent":{"name":"Front desk","greetingMessage":"Hello.",
                                  "systemPrompt":"You answer for {{business_name}} at {{business_address}}.",
                                  "defaultLanguage":"en","supportedLanguages":["en"]},
                                 "variables":[{"key":"business_name","label":"Business name","value":"Bright Smile Westlands","required":true}]}
                                """))
                .andExpect(status().isCreated()).andReturn();
        var agentId = UUID.fromString(json.readTree(created.getResponse().getContentAsString()).path("id").asText());

        mvc.perform(get(owner.path("/agents/" + agentId + "/variables")).header("Authorization", owner.bearer()))
                .andExpect(jsonPath("$[?(@.key=='business_address')].filled").value(false))
                .andExpect(jsonPath("$[?(@.key=='business_address')].inheritedValue").value("Ngong Road, Nairobi"));

        var preview = json.readTree(mvc.perform(get(owner.path("/agents/" + agentId + "/instructions-preview"))
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(preview.path("instructions").asText())
                // The agent's own name wins over the profile; the empty address comes from the profile.
                .contains("You answer for Bright Smile Westlands at Ngong Road, Nairobi.")
                .contains("Cleaning — KES 3,500 — 30 min")
                .contains("Q: Is there parking? A: Yes, behind the building.");
        assertThat(preview.path("warnings").findValuesAsText("key")).doesNotContain("business_address");
    }

    @Test
    void membersCanReadButNotEditTheProfile() throws Exception {
        var owner = accounts.owner("Roles Clinic", "roles-owner-" + System.nanoTime() + "@example.com", "KE");
        var member = accounts.member(owner.workspaceId(), "roles-member-" + System.nanoTime() + "@example.com",
                WorkspaceRole.MEMBER);

        mvc.perform(get(member.path("/business-profile")).header("Authorization", member.bearer()))
                .andExpect(status().isOk());
        mvc.perform(put(member.path("/business-profile")).header("Authorization", member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidFactsAreRejectedWithAReadableMessage() throws Exception {
        var owner = accounts.owner("Invalid Clinic", "invalid-" + System.nanoTime() + "@example.com", "KE");

        mvc.perform(put(owner.path("/business-profile")).header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"services\":[{\"name\":\"Cleaning\",\"priceAmount\":10}]}"))
                .andExpect(status().isBadRequest());
    }
}
