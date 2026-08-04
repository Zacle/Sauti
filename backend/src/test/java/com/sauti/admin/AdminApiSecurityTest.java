package com.sauti.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sauti.auth.JwtService;
import com.sauti.auth.User;
import com.sauti.auth.UserRepository;
import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import com.sauti.call.Call;
import com.sauti.call.CallRepository;
import com.sauti.tenant.Tenant;
import com.sauti.tenant.TenantRepository;
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
    @Autowired JwtService jwt;
    @Autowired AgentRepository agents;
    @Autowired CallRepository calls;

    @Test
    void separatesPlatformAdminApisFromTenantOwners() throws Exception {
        var admin = user("Admin workspace", "platform-admin@sauti.test");
        var owner = user("Customer workspace", "customer-owner@sauti.test");

        mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + jwt.issueAccessToken(owner)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/overview").header("Authorization", "Bearer " + jwt.issueAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces").isNumber());
    }

    @Test
    void searchesWorkspaceAndTenantSeparatedCustomerDetails() throws Exception {
        var admin = user("Admin workspace", "platform-admin@sauti.test");
        var owner = user("Acme Dental", "dental-owner@sauti.test");
        var agent = agents.save(new Agent(owner.getTenant(), "Amina", "Hello", "Help callers"));
        calls.save(new Call(owner.getTenant(), agent, "admin-customer-query-call", "+254799998877", "inbound"));
        var authorization = "Bearer " + jwt.issueAccessToken(admin);

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
                .andExpect(jsonPath("$.customers[0].tenantId").value(owner.getTenant().getId().toString()))
                .andExpect(jsonPath("$.customers[0].phone").value("+254799998877"));

        mvc.perform(get("/api/v1/admin/customers/{tenantId}", owner.getTenant().getId())
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

    private User user(String business, String email) {
        var tenant = tenants.save(new Tenant(business, email, "KE"));
        var user = new User(tenant, email, passwords.encode("password123"));
        user.verifyEmail();
        return users.save(user);
    }
}
