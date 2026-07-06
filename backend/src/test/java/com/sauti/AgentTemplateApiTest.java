package com.sauti;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.auth.JwtService;
import com.sauti.auth.User;
import com.sauti.auth.UserRepository;
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
    JwtService jwtService;

    String ownerToken;
    String otherTenantToken;

    @BeforeEach
    void createTenants() {
        ownerToken = createTenantToken("Template Owner", "templates-owner@example.com", "KE");
        otherTenantToken = createTenantToken("Other Tenant", "templates-other@example.com", "TZ");
    }

    @Test
    void managesTenantTemplatesAndCreatesIndependentAgentCopy() throws Exception {
        var systemTemplatesJson = mvc.perform(get("/api/v1/agent-templates")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Medical Receptionist')].scope").value("system"))
                .andExpect(jsonPath("$[?(@.name == 'Medical Receptionist')].editable").value(false))
                .andReturn().getResponse().getContentAsString();

        String systemTemplateId = null;
        for (var template : objectMapper.readTree(systemTemplatesJson)) {
            if ("Medical Receptionist".equals(template.get("name").asText())) {
                systemTemplateId = template.get("id").asText();
                break;
            }
        }
        if (systemTemplateId == null) {
            throw new IllegalStateException("Medical Receptionist system template was not seeded");
        }

        var createdJson = mvc.perform(post("/api/v1/agent-templates")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("Clinic Reception", "Karibu, how may I help?", 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("tenant"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();

        String templateId = objectMapper.readTree(createdJson).get("id").asText();

        mvc.perform(get("/api/v1/agent-templates/" + templateId)
                        .header("Authorization", bearer(otherTenantToken)))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/agent-templates/" + templateId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("Clinic Reception v2", "Habari, how may I help?", 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Clinic Reception v2"))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(put("/api/v1/agent-templates/" + systemTemplateId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(templateBody("Changed system template", "Hello", 1)))
                .andExpect(status().isNotFound());

        var agentJson = mvc.perform(post("/api/v1/agents/from-template/" + systemTemplateId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Amina",
                                  "timezone": "Africa/Nairobi",
                                  "humanTransferNumber": "+254700000000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Amina"))
                .andExpect(jsonPath("$.defaultLanguage").value("en"))
                .andExpect(jsonPath("$.supportedLanguages[0]").value("en"))
                .andExpect(jsonPath("$.bookingEnabled").value(true))
                .andExpect(jsonPath("$.active").value(false))
                .andReturn().getResponse().getContentAsString();
        var agentId = objectMapper.readTree(agentJson).get("id").asText();

        mvc.perform(get("/api/v1/agents/" + agentId + "/variables")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'clinic_name')].required").value(true))
                .andExpect(jsonPath("$[?(@.key == 'clinic_name')].filled").value(false));

        mvc.perform(post("/api/v1/agents/" + agentId + "/activate")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/agents/" + agentId + "/variables/clinic_name")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"Nairobi Family Health\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filled").value(true));

        mvc.perform(post("/api/v1/agents/" + agentId + "/variables")
                        .header("Authorization", bearer(ownerToken))
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

        mvc.perform(post("/api/v1/agents/" + agentId + "/variables")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"Invalid Key","label":"Invalid","value":"","required":false}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/agents/" + agentId + "/variables")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "variables": [
                                    {"key":"clinic_name","value":"Nairobi Family Health"},
                                    {"key":"clinic_hours","value":"Mon-Fri 8:00-18:00"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/agents/" + agentId + "/provision-number")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/agents/" + agentId + "/activate")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(delete("/api/v1/agent-templates/" + templateId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/agent-templates/" + templateId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidConfigurationAndUnsupportedLanguage() throws Exception {
        mvc.perform(post("/api/v1/agent-templates")
                        .header("Authorization", bearer(ownerToken))
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

    private String createTenantToken(String businessName, String email, String countryCode) {
        var tenant = tenantRepository.save(new Tenant(businessName, email, countryCode));
        var user = new User(tenant, email, "not-used");
        user.verifyEmail();
        userRepository.save(user);
        return jwtService.issueAccessToken(user);
    }

    private String templateBody(String name, String greeting, int revision) {
        return """
                {
                  "name": "%s",
                  "description": "Answers calls for a clinic.",
                  "category": "Healthcare",
                  "greetingMessage": "%s",
                  "systemPrompt": "Help callers and book appointments. Revision %d.",
                  "defaultLanguage": "sw",
                  "supportedLanguages": ["sw", "en"],
                  "configurationJson": "{\\"bookingEnabled\\":true,\\"tools\\":[\\"calendar_booking\\"]}",
                  "published": true
                }
                """.formatted(name, greeting, revision);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
