package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.business.application.BusinessProfileDtos.BusinessProfileRequest;
import com.sauti.business.application.BusinessProfileDtos.BusinessProfileView;
import com.sauti.business.application.BusinessProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The workspace's business facts, shared by all of its agents. Editing needs the configure permission. */
@RestController
@RequestMapping("/api/v1/business-profile")
class BusinessProfileController {
    private final BusinessProfileService profiles;

    BusinessProfileController(BusinessProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    BusinessProfileView get(@AuthenticationPrincipal AuthenticatedUser user) {
        return profiles.get(user.tenantId());
    }

    @PutMapping
    BusinessProfileView update(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody BusinessProfileRequest request) {
        return profiles.update(user.tenantId(), request);
    }
}
