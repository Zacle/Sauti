package com.sauti.api;

import com.sauti.agent.AgentDtos.AgentRequest;
import com.sauti.agent.AgentDtos.AgentResponse;
import com.sauti.agent.AgentDtos.AgentTimezoneRequest;
import com.sauti.agent.AgentService;
import com.sauti.agent.AgentStatsService;
import com.sauti.agent.AgentReadinessService;
import com.sauti.agent.AgentReadinessDtos.AgentReadinessResponse;
import com.sauti.agent.AgentDtos.AgentStatsResponse;
import com.sauti.agent.AgentDraftGenerationDtos.GenerateAgentDraftRequest;
import com.sauti.agent.AgentDraftGenerationDtos.GeneratedAgentDraftResponse;
import com.sauti.agent.AgentDraftGenerationService;
import com.sauti.agent.AgentTemplateDtos.CreateAgentFromTemplateRequest;
import com.sauti.agent.AgentTemplateService;
import com.sauti.agent.AgentVariableDtos.AgentVariableResponse;
import com.sauti.agent.AgentVariableDtos.BulkAgentVariableRequest;
import com.sauti.agent.AgentVariableDtos.CreateAgentVariableRequest;
import com.sauti.agent.AgentVariableDtos.PatchAgentVariableRequest;
import com.sauti.agent.AgentVariableService;
import com.sauti.auth.AuthenticatedUser;
import com.sauti.knowledge.KnowledgeDocumentDtos.KnowledgeDocumentResponse;
import com.sauti.knowledge.KnowledgeDocumentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    private final AgentService agentService;
    private final AgentTemplateService agentTemplateService;
    private final AgentStatsService agentStatsService;
    private final AgentDraftGenerationService draftGenerationService;
    private final AgentVariableService agentVariableService;
    private final AgentReadinessService agentReadinessService;
    private final KnowledgeDocumentService knowledgeDocumentService;

    public AgentController(
            AgentService agentService,
            AgentTemplateService agentTemplateService,
            AgentStatsService agentStatsService,
            AgentDraftGenerationService draftGenerationService,
            AgentVariableService agentVariableService,
            AgentReadinessService agentReadinessService,
            KnowledgeDocumentService knowledgeDocumentService
    ) {
        this.agentService = agentService;
        this.agentTemplateService = agentTemplateService;
        this.agentStatsService = agentStatsService;
        this.draftGenerationService = draftGenerationService;
        this.agentVariableService = agentVariableService;
        this.agentReadinessService = agentReadinessService;
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @PostMapping("/generate-from-brief")
    GeneratedAgentDraftResponse generateFromBrief(
            @Valid @RequestBody GenerateAgentDraftRequest request
    ) {
        return draftGenerationService.generate(request.brief());
    }

    @GetMapping("/stats")
    List<AgentStatsResponse> stats(@AuthenticationPrincipal AuthenticatedUser user) {
        return agentStatsService.list(user.tenantId());
    }

    @GetMapping("/readiness")
    List<AgentReadinessResponse> readiness(@AuthenticationPrincipal AuthenticatedUser user) {
        return agentReadinessService.list(user.tenantId());
    }

    @GetMapping
    List<AgentResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return agentService.list(user.tenantId()).stream().map(AgentResponse::from).toList();
    }

    @PostMapping
    AgentResponse create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody AgentRequest request) {
        return AgentResponse.from(agentService.create(user.tenantId(), request));
    }

    @PostMapping("/from-template/{templateId}")
    AgentResponse createFromTemplate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID templateId,
            @RequestBody(required = false) @Valid CreateAgentFromTemplateRequest request
    ) {
        return AgentResponse.from(agentTemplateService.createAgent(user.tenantId(), templateId, request));
    }

    @GetMapping("/{id}")
    AgentResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return AgentResponse.from(agentService.get(user.tenantId(), id));
    }

    @GetMapping("/{id}/readiness")
    AgentReadinessResponse readiness(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return agentReadinessService.get(user.tenantId(), id);
    }

    @PutMapping("/{id}")
    AgentResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id, @Valid @RequestBody AgentRequest request) {
        return AgentResponse.from(agentService.update(user.tenantId(), id, request));
    }

    @PatchMapping("/{id}/timezone")
    AgentResponse updateTimezone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody AgentTimezoneRequest request
    ) {
        return AgentResponse.from(agentService.updateTimezone(user.tenantId(), id, request.timezone()));
    }

    @PostMapping("/{id}/activate")
    AgentResponse activate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return AgentResponse.from(agentService.activate(user.tenantId(), id));
    }

    @PostMapping("/{id}/deactivate")
    AgentResponse deactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return AgentResponse.from(agentService.deactivate(user.tenantId(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        agentService.delete(user.tenantId(), id);
    }

    @PostMapping("/{id}/provision-number")
    AgentResponse provisionNumber(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody(required = false) ProvisionNumberRequest request
    ) {
        return AgentResponse.from(agentService.provisionNumber(
                user.tenantId(),
                id,
                request == null ? null : request.phoneNumber(),
                request != null && request.replaceExisting()
        ));
    }

    @PostMapping("/{id}/phone-number/refresh")
    AgentResponse refreshPhoneNumber(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return AgentResponse.from(agentService.refreshPhoneNumber(user.tenantId(), id));
    }

    @GetMapping("/{id}/available-numbers")
    List<com.sauti.agent.TelephonyProvider.AvailablePhoneNumber> availableNumbers(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestParam(required = false) String countryCode,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return agentService.searchAvailableNumbers(user.tenantId(), id, countryCode, limit);
    }

    @GetMapping("/{id}/knowledge-documents")
    List<KnowledgeDocumentResponse> knowledgeDocuments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return knowledgeDocumentService.list(user.tenantId(), id)
                .stream()
                .map(KnowledgeDocumentResponse::from)
                .toList();
    }

    @PostMapping(value = "/{id}/knowledge-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    KnowledgeDocumentResponse uploadKnowledgeDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file
    ) {
        return KnowledgeDocumentResponse.from(knowledgeDocumentService.upload(user.tenantId(), id, file));
    }

    @DeleteMapping("/{id}/knowledge-documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteKnowledgeDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @PathVariable UUID documentId
    ) {
        knowledgeDocumentService.delete(user.tenantId(), id, documentId);
    }

    @GetMapping("/{id}/variables")
    List<AgentVariableResponse> variables(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        return agentVariableService.list(user.tenantId(), id);
    }

    @PostMapping("/{id}/variables")
    AgentVariableResponse createVariable(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody CreateAgentVariableRequest request
    ) {
        return agentVariableService.create(
                user.tenantId(),
                id,
                request.key(),
                request.label(),
                request.description(),
                request.value(),
                request.required()
        );
    }

    @PutMapping("/{id}/variables")
    List<AgentVariableResponse> updateVariables(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody BulkAgentVariableRequest request
    ) {
        return agentVariableService.updateAll(user.tenantId(), id, request.variables());
    }

    @PatchMapping("/{id}/variables/{key}")
    AgentVariableResponse updateVariable(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @PathVariable String key,
            @Valid @RequestBody PatchAgentVariableRequest request
    ) {
        return agentVariableService.updateOne(user.tenantId(), id, key, request.value());
    }
}

record ProvisionNumberRequest(String phoneNumber, boolean replaceExisting) {
}
