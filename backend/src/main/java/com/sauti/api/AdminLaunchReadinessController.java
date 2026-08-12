package com.sauti.api;

import com.sauti.admin.PlatformLaunchReadinessService;
import com.sauti.admin.PlatformLaunchReadinessService.Readiness;
import com.sauti.admin.PlatformLaunchReadinessService.UpdateReview;
import com.sauti.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/launch-readiness")
public class AdminLaunchReadinessController {
    private final PlatformLaunchReadinessService readiness;

    public AdminLaunchReadinessController(PlatformLaunchReadinessService readiness) {
        this.readiness = readiness;
    }

    @GetMapping
    Readiness get() { return readiness.get(); }

    @PatchMapping
    Readiness update(@AuthenticationPrincipal AuthenticatedUser user,
                     @RequestBody UpdateReview request) {
        return readiness.update(request, user.email());
    }
}
