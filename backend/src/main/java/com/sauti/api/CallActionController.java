package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.call.CallQueryService;
import com.sauti.tool.ToolActionLog;
import com.sauti.tool.ToolActionLog.ActionView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the agent did during a call: bookings, messages, transfers, and what waited for confirmation. */
@RestController
@RequestMapping("/api/v1/calls")
class CallActionController {
    private final CallQueryService calls;
    private final ToolActionLog actions;

    CallActionController(CallQueryService calls, ToolActionLog actions) {
        this.calls = calls;
        this.actions = actions;
    }

    @GetMapping("/{id}/actions")
    List<ActionView> actions(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        calls.get(user.tenantId(), id);
        return actions.forCall(user.tenantId(), id);
    }
}
