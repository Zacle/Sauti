package com.sauti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.testsupport.TestAccounts;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext
class AgentTemplateApiTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TenantRepository tenantRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TestAccounts accounts;

    TestAccounts.Member owner;
    TestAccounts.Member otherTenant;

    @BeforeEach
    void createTenants() {
        owner = createOwner("Template Owner", "templates-owner@example.com", "KE");
        accounts.approveProviderSpending(owner.workspaceId());
        otherTenant = createOwner("Other Tenant", "templates-other@example.com", "TZ");
    }

    @Test
    void managesTenantTemplatesAndCreatesIndependentAgentCopy() throws Exception {
        var systemTemplatesJson = mvc.perform(get(owner.path("/agent-templates"))
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Medical Receptionist')].scope").value("system"))
                .andExpect(jsonPath("$[?(@.name == 'Medical Receptionist')].editable").value(false))
                .andReturn().getResponse().getContentAsString();

        String systemTemplateId = null;
        String systemTemplateConfiguration = null;
        for (var template : objectMapper.readTree(systemTemplatesJson)) {
            if ("Medical Receptionist".equals(template.get("name").asText())) {
                systemTemplateId = template.get("id").asText();
                systemTemplateConfiguration = template.get("configurationJson").asText();
                break;
            }
        }
        if (systemTemplateId == null) {
            throw new IllegalStateException("Medical Receptionist system template was not seeded");
        }
        assertThat(systemTemplateConfiguration).contains("\"bookingDurationMinutes\":60");

        var createdJson = mvc.perform(post(owner.path("/agent-templates"))
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("Clinic Reception", "Karibu, how may I help?", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("tenant"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        String templateId = objectMapper.readTree(createdJson).get("id").asText();

        mvc.perform(get(otherTenant.path("/agent-templates/") + templateId)
                        .header("Authorization", otherTenant.bearer()))
                .andExpect(status().isNotFound());

        mvc.perform(put(owner.path("/agent-templates/") + templateId)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("Clinic Reception v2", "Habari, how may I help?", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Clinic Reception v2"))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(put(owner.path("/agent-templates/") + systemTemplateId)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("Changed system template", "Hello", 1)))
                .andExpect(status().isNotFound());

        mvc.perform(put(owner.path("/tenant/workspace-profile"))
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessName": "Workspace Defaults Clinic",
                                  "timezone": "Africa/Nairobi",
                                  "defaultBookingDurationMinutes": 45
                                }
                                """))
                .andExpect(status().isOk());

        var agentJson = mvc.perform(post(owner.path("/agents/from-template/") + systemTemplateId)
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Amina",
                                  "humanTransferNumber": "+254700000000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Amina"))
                .andExpect(jsonPath("$.defaultLanguage").value("en"))
                .andExpect(jsonPath("$.supportedLanguages[0]").value("en"))
                .andExpect(jsonPath("$.bookingEnabled").value(true))
                .andExpect(jsonPath("$.timezone").value("Africa/Nairobi"))
                .andExpect(jsonPath("$.defaultBookingDurationMinutes").value(45))
                .andExpect(jsonPath("$.active").value(false))
                .andReturn().getResponse().getContentAsString();
        var agentId = objectMapper.readTree(agentJson).get("id").asText();

        var variablesJson = mvc.perform(get(owner.path("/agents/") + agentId + "/variables")
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'business_name')].required").value(true))
                .andExpect(jsonPath("$[?(@.key == 'business_name')].filled").value(false))
                .andExpect(jsonPath("$[?(@.required == false)]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post(owner.path("/agents/") + agentId + "/activate")
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isBadRequest());

        mvc.perform(patch(owner.path("/agents/") + agentId + "/variables/business_name")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"Nairobi Family Health\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filled").value(true));

        for (var variable : objectMapper.readTree(variablesJson)) {
            var key = variable.path("key").asText();
            if (!variable.path("required").asBoolean() || "business_name".equals(key)) continue;
            var configuredValue = switch (key) {
                case "business_hours" -> "Mon-Fri 09:00-17:00";
                case "after_hours_behavior" -> "take_message";
                case "business_phone" -> "+254712345678";
                default -> "Configured test value";
            };
            mvc.perform(patch(owner.path("/agents/") + agentId + "/variables/" + key)
                            .header("Authorization", owner.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of("value", configuredValue))))
                    .andExpect(status().isOk());
        }

        mvc.perform(post(owner.path("/agents/") + agentId + "/variables")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key":"parking_instructions",
                                  "label":"Parking instructions",
                                  "description":"Directions for callers arriving by car",
                                  "value":"Use the rear entrance",
                                  "required":false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("parking_instructions"))
                .andExpect(jsonPath("$.filled").value(true));

        mvc.perform(post(owner.path("/agents/") + agentId + "/variables")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"Invalid Key","label":"Invalid","value":"","required":false}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(put(owner.path("/agents/") + agentId + "/variables")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "variables": [
                                    {"key":"business_name","value":"Nairobi Family Health"},
                                    {"key":"business_hours","value":"Mon-Fri 8:00-18:00"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(post(owner.path("/agents/") + agentId + "/provision-number")
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+254712345679\",\"providerChargesConfirmed\":true}"))
                .andExpect(status().isOk());

        mvc.perform(post(owner.path("/agents/") + agentId + "/activate")
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(delete(owner.path("/agent-templates/") + templateId)
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());

        mvc.perform(get(owner.path("/agent-templates/") + templateId)
                        .header("Authorization", owner.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidConfigurationAndUnsupportedLanguage() throws Exception {
        mvc.perform(post(owner.path("/agent-templates"))
                        .header("Authorization", owner.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid",
                                  "description": "Invalid template",
                                  "category": "Test",
                                  "greetingMessage": "Hello",
                                  "systemPrompt": "Help the caller.",
                                  "defaultLanguage": "de",
                                  "supportedLanguages": ["de"],
                                  "configurationJson": "[]",
                                  "published": false
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private TestAccounts.Member createOwner(String businessName, String email, String countryCode) {
        return accounts.owner(businessName, email, countryCode);
    }

    private String templateBody(String name, String greeting, int revision) {
        return """
                {
                  "name": "%s",
                  "description": "Answers calls for a clinic.",
                  "category": "Healthcare",
                  "greetingMessage": "%s",
                  "systemPrompt": "Help callers and book appointments. Revision %d.",
                  "defaultLanguage": "en",
                  "supportedLanguages": ["en", "fr"],
                  "configurationJson": "{\\"bookingEnabled\\":true,\\"tools\\":[\\"calendar_booking\\"]}",
                  "published": true
                }
                """.formatted(name, greeting, revision);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
