package com.sauti.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sauti.testsupport.TestAccounts;
import com.sauti.auth.domain.User;
import com.sauti.auth.domain.UserRepository;
import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
import com.sauti.demo.DemoRequest;
import com.sauti.demo.DemoRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "sauti.admin.emails=platform-admin@sauti.test")
@AutoConfigureMockMvc
@Transactional
class AdminApiSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired TestAccounts accounts;
    @Autowired AgentRepository agents;
    @Autowired CallRepository calls;
    @Autowired DemoRequestRepository demoRequests;

    @Test
    void separatesPlatformAdminApisFromTenantOwners() throws Exception {
        var admin = user("Admin workspace", "platform-admin@sauti.test");
        var owner = user("Customer workspace", "customer-owner@sauti.test");

        mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces").isNumber());

        mvc.perform(get("/api/v1/admin/billing/readiness")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/billing/readiness")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("whop"))
                .andExpect(jsonPath("$.variants.length()").value(6));

        mvc.perform(get("/api/v1/admin/launch-readiness")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/launch-readiness")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.automatedChecks.length()").value(8));
    }

    @Test
    void searchesWorkspaceAndTenantSeparatedCustomerDetails() throws Exception {
        var admin = user("Admin workspace", "platform-admin@sauti.test");
        var owner = user("Acme Dental", "dental-owner@sauti.test");
        var agent = agents.save(new Agent(tenants.findById(owner.workspaceId()).orElseThrow(), "Amina", "Hello", "Help callers"));
        calls.save(new Call(tenants.findById(owner.workspaceId()).orElseThrow(), agent, "admin-customer-query-call", "+254799998877", "inbound"));
        var authorization = "Bearer " + admin.token();

        mvc.perform(get("/api/v1/admin/workspaces").param("query", "Acme")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.workspaces[0].businessName").value("Acme Dental"))
                .andExpect(jsonPath("$.workspaces[0].customers").value(1));

        mvc.perform(get("/api/v1/admin/customers").param("query", "99998877")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.customers[0].tenantId").value(owner.workspaceId().toString()))
                .andExpect(jsonPath("$.customers[0].phone").value("+254799998877"));

        mvc.perform(get("/api/v1/admin/customers/{tenantId}", owner.workspaceId())
                        .param("phone", "+254799998877")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Acme Dental"))
                .andExpect(jsonPath("$.recentCalls[0].agentName").value("Amina"));

        mvc.perform(get("/api/v1/admin/analytics").param("days", "7")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.activity.length()").value(7));
    }

    @Test
    void recordsAssignmentAndNotesAsAnImmutableAdminAuditEvent() throws Exception {
        var admin = user("Admin workspace", "platform-admin@sauti.test");
        var owner = user("Customer workspace", "ordinary-owner@sauti.test");
        var request = demoRequests.save(new DemoRequest("Acme", "Amina", "lead@example.com", "KE", null,
                "Healthcare", "under-100", "voice", "Answer calls", null));
        var payload = "{\"assignedTo\":\"operator@sauti.uk\",\"internalNotes\":\"Qualified for pilot\"}";

        mvc.perform(patch("/api/v1/admin/demo-requests/{id}", request.getId())
                        .contentType("application/json").content(payload)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/v1/admin/demo-requests/{id}", request.getId())
                        .contentType("application/json").content(payload)
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value("operator@sauti.uk"))
                .andExpect(jsonPath("$.internalNotes").value("Qualified for pilot"));

        mvc.perform(get("/api/v1/admin/audit")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].actorEmail").value("platform-admin@sauti.test"))
                .andExpect(jsonPath("$.events[0].action").value("demo.request.operations_updated"))
                .andExpect(jsonPath("$.events[0].resourceId").value(request.getId().toString()));
    }

    @Test
    void platformAdminControlsPilotBudgetAndPaidCapabilities() throws Exception {
        var admin = user("Admin workspace", "platform-admin@sauti.test");
        var pilot = user("Pilot workspace", "pilot-owner@sauti.test");
        var authorization = "Bearer " + admin.token();

        mvc.perform(patch("/api/v1/admin/workspaces/{id}/pilot-policy", pilot.workspaceId())
                        .contentType("application/json")
                        .content("""
                                {"status":"approved","currency":"USD","monthlyBudget":25.00,
                                 "phoneNumbersApproved":true,"liveCallingApproved":true,
                                 "smsApproved":false,"whatsappApproved":false,
                                 "notes":"Voice-only controlled pilot"}
                                """)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pilotPolicy.status").value("approved"))
                .andExpect(jsonPath("$.pilotPolicy.monthlyBudget").value(25.0))
                .andExpect(jsonPath("$.pilotPolicy.phoneNumbersApproved").value(true))
                .andExpect(jsonPath("$.pilotPolicy.smsApproved").value(false))
                .andExpect(jsonPath("$.pilotPolicy.approvedBy").value("platform-admin@sauti.test"));

        mvc.perform(get("/api/v1/admin/audit").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].action").value("pilot.provisioning_policy.updated"));
    }

    @Test
    void platformAdminReviewsEvidenceBackedPilotReadiness() throws Exception {
        var admin = user("Admin workspace", "platform-admin@sauti.test");
        var pilot = user("Pilot workspace", "readiness-owner@sauti.test");
        var authorization = "Bearer " + admin.token();

        mvc.perform(get("/api/v1/admin/workspaces/{id}/readiness", pilot.workspaceId())
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingChecks").value(3))
                .andExpect(jsonPath("$.checks[0].key").value("agent_setup"))
                .andExpect(jsonPath("$.checks[1].status").value("not_required"))
                .andExpect(jsonPath("$.checks[2].status").value("not_required"));

        mvc.perform(patch("/api/v1/admin/workspaces/{id}/readiness", pilot.workspaceId())
                        .contentType("application/json")
                        .content("""
                                {"supportContactName":"Zachary","supportContactEmail":"support@example.com",
                                 "supportContactPhone":"","launchNotes":"Pilot owner briefed",
                                 "launchApproved":false}
                                """)
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportContactName").value("Zachary"))
                .andExpect(jsonPath("$.blockingChecks").value(2))
                .andExpect(jsonPath("$.readyForLaunch").value(false));

        mvc.perform(get("/api/v1/admin/audit").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].action").value("pilot.readiness.updated"));
    }

    private TestAccounts.Member user(String business, String email) {
        return accounts.owner(business, email, "KE");
    }
}
