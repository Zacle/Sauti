package com.sauti.api;

import com.sauti.agent.AgentDtos.AgentResponse;
import com.sauti.agent.AgentTemplateDtos.AgentTemplateRequest;
import com.sauti.agent.AgentTemplateDtos.AgentTemplateResponse;
import com.sauti.agent.AgentTemplateDtos.CreateAgentFromTemplateRequest;
import com.sauti.agent.AgentTemplateService;
import com.sauti.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/agent-templates")
public class AgentTemplateController {
    private final AgentTemplateService templateService;

    public AgentTemplateController(AgentTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    List<AgentTemplateResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        var templates = user == null
                ? templateService.listSystemTemplates()
                : templateService.list(user.tenantId());
        return templates.stream().map(AgentTemplateResponse::from).toList();
    }

    @GetMapping("/{id}")
    AgentTemplateResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return AgentTemplateResponse.from(user == null
                ? templateService.getSystemTemplate(id)
                : templateService.get(user.tenantId(), id));
    }

    @PostMapping
    AgentTemplateResponse create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AgentTemplateRequest request
    ) {
        return AgentTemplateResponse.from(templateService.create(user.tenantId(), request));
    }

    @PutMapping("/{id}")
    AgentTemplateResponse update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody AgentTemplateRequest request
    ) {
        return AgentTemplateResponse.from(templateService.update(user.tenantId(), id, request));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        templateService.delete(user.tenantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/agents")
    AgentResponse createAgent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid CreateAgentFromTemplateRequest request
    ) {
        return AgentResponse.from(templateService.createAgent(user.tenantId(), id, request));
    }
}
