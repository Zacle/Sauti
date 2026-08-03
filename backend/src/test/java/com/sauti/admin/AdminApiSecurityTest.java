package com.sauti.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sauti.auth.JwtService;
import com.sauti.auth.User;
import com.sauti.auth.UserRepository;
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

    private User user(String business, String email) {
        var tenant = tenants.save(new Tenant(business, email, "KE"));
        var user = new User(tenant, email, passwords.encode("password123"));
        user.verifyEmail();
        return users.save(user);
    }
}
