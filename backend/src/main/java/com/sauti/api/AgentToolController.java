package com.sauti.api;

import com.sauti.auth.AuthenticatedUser;
import com.sauti.tool.AgentToolDtos.AgentToolRequest;
import com.sauti.tool.AgentToolDtos.AgentToolResponse;
import com.sauti.tool.AgentToolService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/{agentId}/tools")
public class AgentToolController {
    private final AgentToolService agentToolService;

    public AgentToolController(AgentToolService agentToolService) {
        this.agentToolService = agentToolService;
    }

    @GetMapping
    List<AgentToolResponse> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID agentId) {
        return agentToolService.list(user.tenantId(), agentId).stream().map(AgentToolResponse::from).toList();
    }

    @GetMapping("/{toolId}")
    AgentToolResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID toolId) {
        return AgentToolResponse.from(agentToolService.get(user.tenantId(), toolId));
    }

    @PostMapping
    AgentToolResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID agentId,
            @Valid @RequestBody AgentToolRequest request
    ) {
        return AgentToolResponse.from(agentToolService.create(user.tenantId(), agentId, request));
    }

    @PutMapping("/{toolId}")
    AgentToolResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID toolId,
            @Valid @RequestBody AgentToolRequest request
    ) {
        return AgentToolResponse.from(agentToolService.update(user.tenantId(), toolId, request));
    }

    @DeleteMapping("/{toolId}")
    AgentToolResponse delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID toolId) {
        return AgentToolResponse.from(agentToolService.deactivate(user.tenantId(), toolId));
    }
}
