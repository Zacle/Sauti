package com.sauti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.auth.AuthEmailService;
import com.sauti.auth.AuthRateLimitService;
import com.sauti.auth.User;
import com.sauti.auth.VerificationCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthAgentFlowTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthEmailService authEmailService;

    @MockitoBean
    AuthRateLimitService authRateLimitService;

    @MockitoBean
    VerificationCodeService verificationCodeService;

    @Test
    void registerThenExerciseCoreAppFlows() throws Exception {
        mvc.perform(get("/api/v1/agents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        when(verificationCodeService.generateAndStoreEmailVerificationCode(any(User.class)))
                .thenReturn("111111", "222222");
        when(verificationCodeService.generateAndStorePasswordResetCode(any(User.class)))
                .thenReturn("333333");
        when(verificationCodeService.verifyEmailCode(any(User.class), eq("222222")))
                .thenReturn(true);
        when(verificationCodeService.verifyPasswordResetCode(any(User.class), eq("333333")))
                .thenReturn(true);

        String token = registerVerifyResetPasswordAndReturnToken();

        String agentBody = """
                {
                  "name": "Amina",
                  "description": "Multilingual clinic booking agent",
                  "greetingMessage": "Bonjour, vous etes bien chez Demo Clinic.",
                  "systemPrompt": "You answer calls for Demo Clinic.",
                  "defaultLanguage": "fr",
                  "supportedLanguages": ["fr", "en"],
                  "ttsVoiceId": "provider-voice-123",
                  "humanTransferNumber": "+221770000000",
                  "escalationPhrases": ["speak to a human"],
                  "bookingEnabled": true,
                  "timezone": "Africa/Dakar",
                  "operatingHours": "always",
                  "maxCallDurationSeconds": 420,
                  "saveTranscript": true,
                  "recordCalls": false
                }
                """;

        var agentJson = mvc.perform(post("/api/v1/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Amina"))
                .andExpect(jsonPath("$.description").value("Multilingual clinic booking agent"))
                .andExpect(jsonPath("$.supportedLanguages[1]").value("en"))
                .andExpect(jsonPath("$.ttsVoiceId").value("provider-voice-123"))
                .andExpect(jsonPath("$.twilioPhoneNumber").doesNotExist())
                .andExpect(jsonPath("$.bookingEnabled").value(true))
                .andExpect(jsonPath("$.maxCallDurationSeconds").value(420))
                .andExpect(jsonPath("$.saveTranscript").value(true))
                .andExpect(jsonPath("$.active").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode agent = objectMapper.readTree(agentJson);
        String agentId = agent.get("id").asText();

        mvc.perform(get("/api/v1/integrations/catalog")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'whatsapp')].postCall").value(true))
                .andExpect(jsonPath("$[?(@.provider == 'mpesa')].duringCall").value(true));

        var connectionJson = mvc.perform(post("/api/v1/integrations/connections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "whatsapp",
                                  "configuration": {
                                    "wabaId": "waba-1",
                                    "phoneNumberId": "phone-1",
                                    "templateName": "follow_up",
                                    "templateLanguage": "en_US"
                                  },
                                  "credentials": {"accessToken": "customer-secret-token"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andExpect(jsonPath("$.credentials").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(connectionJson).doesNotContain("customer-secret-token");
        String connectionId = objectMapper.readTree(connectionJson).get("id").asText();

        mvc.perform(put("/api/v1/agents/" + agentId + "/integrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"whatsapp","enabled":true,"connectionId":"%s","configuration":{}}
                                """.formatted(connectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.connectionStatus").value("connected"));

        var provisionedAgentJson = mvc.perform(post("/api/v1/agents/" + agentId + "/provision-number")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twilioPhoneNumber").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String twilioNumber = objectMapper.readTree(provisionedAgentJson).get("twilioPhoneNumber").asText();

        mvc.perform(post("/api/v1/agents/generate-from-brief")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brief\":\"Book clinic appointments and escalate medical emergencies\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingEnabled").value(true))
                .andExpect(jsonPath("$.supportedLanguages").isArray())
                .andExpect(jsonPath("$.systemPrompt").value(containsString("## Tool Rules")))
                .andExpect(jsonPath("$.variables").isArray());

        mvc.perform(get("/api/v1/tenant/onboarding-status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAgent").value(true))
                .andExpect(jsonPath("$.hasProvisionedNumber").value(true))
                .andExpect(jsonPath("$.hasActiveAgent").value(false))
                .andExpect(jsonPath("$.nextStep").value("activate_agent"));

        mvc.perform(post("/api/v1/agents/" + agentId + "/activate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        activateSeededTools(token, agentId);

        mvc.perform(get("/api/v1/billing/usage")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("trial"))
                .andExpect(jsonPath("$.remainingMinutes").value(60));

        String bookingBody = """
                {
                  "agentId": "%s",
                  "callerName": "Fatou",
                  "callerPhone": "+221771234567",
                  "serviceType": "Consultation",
                  "appointmentAt": "2030-01-15T10:00:00Z"
                }
                """.formatted(agentId);

        var bookingJson = mvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callerName").value("Fatou"))
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String bookingId = objectMapper.readTree(bookingJson).get("id").asText();
        mvc.perform(delete("/api/v1/bookings/" + bookingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));

        var twiml = mvc.perform(post("/webhooks/twilio/voice")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("To", twilioNumber)
                        .param("From", "+221771234567")
                        .param("CallSid", "CA123"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(twiml)
                .contains("<Connect>")
                .contains("<Stream url=\"ws://localhost:8080/ws/twilio/media/CA123\">")
                .contains("<Parameter name=\"callSid\" value=\"CA123\"/>")
                .contains("<Parameter name=\"tenantId\"")
                .contains("<Parameter name=\"agentId\"")
                .doesNotContain("<Say>");

        mvc.perform(post("/api/v1/calls/CA123/simulate-turn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\":\"My name is Fatou and I want to book a consultation tomorrow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(containsString("Would you like me to confirm it?")));

        mvc.perform(post("/api/v1/calls/CA123/simulate-turn")
                        .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transcript\":\"yes confirm it\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value(containsString("Before I save the booking")))
                .andExpect(jsonPath("$.response").value(containsString(
                        "F for Foxtrot, A for Alfa, T for Tango, O for Oscar, U for Uniform"
                )));

        mvc.perform(post("/api/v1/calls/CA123/simulate-turn")
                        .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"transcript\":\"yes, all of those details are correct\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Perfect, you're all booked!"));

        mvc.perform(get("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].callerName").value("Fatou"))
                .andExpect(jsonPath("$[1].serviceType").value("Consultation"))
                .andExpect(jsonPath("$[1].externalEventId").doesNotExist())
                .andExpect(jsonPath("$[1].calendarSyncStatus").value("not_configured"));

        mvc.perform(get("/api/v1/calls")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.twilioCallSid == 'CA123')].outcome").value("booking_made"));

        mvc.perform(post("/webhooks/twilio/voice")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("To", twilioNumber)
                        .param("From", "+221771234568")
                        .param("CallSid", "CA124"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/calls/CA124/simulate-turn")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\":\"I want to speak to a human\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("I am transferring you to a team member."));

        mvc.perform(get("/api/v1/calls")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.twilioCallSid == 'CA124')].outcome").value("active"));

        mvc.perform(get("/api/v1/analytics/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCalls").value(2))
                .andExpect(jsonPath("$.transferredCalls").value(0))
                .andExpect(jsonPath("$.bookingCalls").value(1));

        mvc.perform(get("/api/v1/agents/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentId").value(agentId))
                .andExpect(jsonPath("$[0].totalCalls").value(2))
                .andExpect(jsonPath("$[0].bookingCalls").value(1))
                .andExpect(jsonPath("$[0].bookingRate").value(50.0));

        mvc.perform(post("/webhooks/twilio/status")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("CallSid", "CA124")
                        .param("CallStatus", "completed")
                        .param("CallDuration", "73")
                        .param("RecordingUrl", "https://api.twilio.com/recordings/RE123"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/calls")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.twilioCallSid == 'CA124')].outcome").value("completed"))
                .andExpect(jsonPath("$[?(@.twilioCallSid == 'CA124')].durationSeconds").value(73))
                .andExpect(jsonPath("$[?(@.twilioCallSid == 'CA124')].recordingUrl").value("https://api.twilio.com/recordings/RE123"));
    }

    private String registerVerifyResetPasswordAndReturnToken() throws Exception {
        String registerBody = """
                {
                  "businessName": "Demo Clinic",
                  "email": "owner@example.com",
                  "countryCode": "SN",
                  "password": "password123"
                }
                """;

        var authJson = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("verification_required"))
                .andExpect(jsonPath("$.devVerificationCode").isString())
                .andExpect(jsonPath("$.tenant.businessName").value("Demo Clinic"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("email_not_verified"));

        var resendJson = mvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devCode").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String verificationCode = objectMapper.readTree(resendJson).get("devCode").asText();
        var verifiedJson = mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"code\":\"" + verificationCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"code\":\"" + verificationCode + "\"}"))
                .andExpect(status().isOk());

        String verifiedRefreshToken = objectMapper.readTree(verifiedJson).get("refreshToken").asText();

        var rotatedJson = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + verifiedRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + verifiedRefreshToken + "\"}"))
                .andExpect(status().isBadRequest());

        String activeRefreshToken = objectMapper.readTree(rotatedJson).get("refreshToken").asText();

        var forgotJson = mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devCode").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String resetCode = objectMapper.readTree(forgotJson).get("devCode").asText();
        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"code\":\"" + resetCode + "\",\"newPassword\":\"newPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + activeRefreshToken + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());

        var loginJson = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"newPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String logoutRefreshToken = objectMapper.readTree(loginJson).get("refreshToken").asText();
        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + logoutRefreshToken + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + logoutRefreshToken + "\"}"))
                .andExpect(status().isBadRequest());

        loginJson = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"newPassword123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode verified = objectMapper.readTree(verifiedJson);
        if (!verified.hasNonNull("accessToken")) {
            throw new IllegalStateException("Email verification did not issue an access token");
        }
        return objectMapper.readTree(loginJson).get("accessToken").asText();
    }

    private void activateSeededTools(String token, String agentId) throws Exception {
        var toolsJson = mvc.perform(get("/api/v1/agents/" + agentId + "/tools")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16))
                .andReturn()
                .getResponse()
                .getContentAsString();

        for (JsonNode tool : objectMapper.readTree(toolsJson)) {
            var body = objectMapper.writeValueAsString(java.util.Map.of(
                    "toolName", tool.get("toolName").asText(),
                    "toolDescription", tool.get("toolDescription").asText(),
                    "parametersSchema", objectMapper.convertValue(tool.get("parametersSchema"), java.util.Map.class),
                    "fulfillmentType", tool.get("fulfillmentType").asText(),
                    "webhookMethod", tool.path("webhookMethod").asText("POST"),
                    "authType", tool.path("authType").asText("none"),
                    "calendarType", tool.path("calendarType").asText(""),
                    "active", true,
                    "displayOrder", tool.get("displayOrder").asInt()
            ));
            mvc.perform(put("/api/v1/agents/" + agentId + "/tools/" + tool.get("id").asText())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.authConfigured").value(false))
                    .andExpect(jsonPath("$.active").value(true));
        }
    }
}
