package com.sauti.api;

import com.sauti.agent.AgentDtos.AgentResponse;
import com.sauti.agent.AgentSetupDtos.AgentSetupRequest;
import com.sauti.agent.AgentSetupDtos.DraftView;
import com.sauti.agent.AgentSetupDtos.InstructionsPreview;
import com.sauti.agent.AgentSetupDtos.VersionView;
import com.sauti.agent.AgentSetupService;
import com.sauti.agent.AgentVersionService;
import com.sauti.auth.AuthenticatedUser;
import com.sauti.call.AgentInstructionsPreviewService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saves a whole agent configuration in one request, keeps unpublished drafts and published
 * versions, and previews what the live assistant is told.
 */
@RestController
@RequestMapping("/api/v1/agents")
class AgentSetupController {
    private final AgentSetupService setup;
    private final AgentVersionService versions;
    private final AgentInstructionsPreviewService previews;

    AgentSetupController(AgentSetupService setup, AgentVersionService versions,
                         AgentInstructionsPreviewService previews) {
        this.setup = setup;
        this.versions = versions;
        this.previews = previews;
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    AgentResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AgentSetupRequest request) {
        return AgentResponse.from(setup.create(user.tenantId(), request, actor(user)));
    }

    /** Publishes: live calls use this configuration from now on, and it becomes the newest version. */
    @PutMapping("/{id}/setup")
    AgentResponse publish(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                          @Valid @RequestBody AgentSetupRequest request,
                          @RequestParam(required = false) String note) {
        return AgentResponse.from(setup.publish(user.tenantId(), id, request, actor(user), note));
    }

    @GetMapping("/{id}/draft")
    ResponseEntity<DraftView> draft(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return versions.draft(user.tenantId(), id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{id}/draft")
    DraftView saveDraft(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                        @Valid @RequestBody AgentSetupRequest request) {
        return versions.saveDraft(user.tenantId(), id, request, actor(user));
    }

    @DeleteMapping("/{id}/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discardDraft(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        versions.discardDraft(user.tenantId(), id);
    }

    @GetMapping("/{id}/versions")
    List<VersionView> versions(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return versions.history(user.tenantId(), id);
    }

    @PostMapping("/{id}/versions/{version}/restore")
    AgentResponse restore(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                          @PathVariable int version) {
        return AgentResponse.from(setup.restore(user.tenantId(), id, version, actor(user)));
    }

    @GetMapping("/{id}/instructions-preview")
    InstructionsPreview preview(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                @RequestParam(required = false) String language) {
        return previews.preview(user.tenantId(), id, language);
    }

    /** Who did it, as shown in the version history: the person's email, or the API key. */
    private static String actor(AuthenticatedUser user) {
        if (user.apiKeyId() != null) return "API key";
        return user.email();
    }
}
