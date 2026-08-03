package com.sauti.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sauti.auth.OperatorApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "sauti.operator.api-key=test-operator-key")
@AutoConfigureMockMvc
@Transactional
class PilotInvitationApiSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired DemoRequestRepository requests;

    @Test
    void operatorEndpointRejectsMissingKeyAndIssuesInviteWithConfiguredKey() throws Exception {
        var request = requests.save(new DemoRequest(
                "Acme Clinic", "Amina", "pilot-owner@example.com", "KE", "+254700000000",
                "Healthcare", "under-100", "voice", "Answer calls", null));

        mvc.perform(post("/api/v1/operator/demo-requests/{id}/invitation", request.getId()))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/operator/demo-requests/{id}/invitation", request.getId())
                        .header(OperatorApiKeyFilter.HEADER, "test-operator-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pilot-owner@example.com"));
    }
}
