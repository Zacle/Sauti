package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.notification.WorkspaceNotificationDtos.NotificationListResponse;
import com.sauti.notification.WorkspaceNotificationDtos.NotificationResponse;
import com.sauti.notification.WorkspaceNotificationService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class WorkspaceNotificationController {
    private final WorkspaceNotificationService service;

    public WorkspaceNotificationController(WorkspaceNotificationService service) {
        this.service = service;
    }

    @GetMapping
    NotificationListResponse list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.tenantId());
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.markRead(user.tenantId(), id);
    }

    @PostMapping("/read-all")
    void markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        service.markAllRead(user.tenantId());
    }
}
