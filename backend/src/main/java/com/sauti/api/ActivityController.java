package com.sauti.api;

import com.sauti.audit.application.ActivityLog;
import com.sauti.audit.application.ActivityLog.ActivityView;
import com.sauti.auth.AuthenticatedUser;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The activity log. Workspace activity is for Admins and Owners; account activity is your own. */
@RestController
public class ActivityController {
    private final ActivityLog activity;

    public ActivityController(ActivityLog activity) {
        this.activity = activity;
    }

    /** Served as /api/v1/workspaces/{workspaceId}/activity. */
    @GetMapping("/api/v1/activity")
    List<ActivityView> workspaceActivity(@AuthenticationPrincipal AuthenticatedUser user,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before) {
        return activity.workspaceActivity(user.tenantId(), before);
    }

    @GetMapping("/api/v1/me/activity")
    List<ActivityView> accountActivity(@AuthenticationPrincipal AuthenticatedUser user) {
        return activity.accountActivity(user.userId());
    }
}
