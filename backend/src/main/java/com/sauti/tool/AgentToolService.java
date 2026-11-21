package com.sauti.tool;

import com.sauti.agent.AgentRepository;
import com.sauti.tool.AgentToolDtos.AgentToolRequest;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.sauti.agent.AgentConfigurationChanged;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentToolService {
    private static final Set<String> FULFILLMENT_TYPES = Set.of(
            "sauti_calendar", "sauti_sms", "sauti_integration", "call_transfer", "webhook", "noop"
    );
    private static final Set<String> AUTH_TYPES = Set.of("none", "bearer", "api_key", "hmac_sha256");
    private static final Set<String> METHODS = Set.of("GET", "POST");

    private final AgentRepository agentRepository;
    private final AgentToolRepository agentToolRepository;
    private final CredentialEncryption credentialEncryption;
    private final WebhookDestinationValidator webhookDestinationValidator;
    private final ApplicationEventPublisher events;

    @Autowired
    public AgentToolService(
            AgentRepository agentRepository,
            AgentToolRepository agentToolRepository,
            CredentialEncryption credentialEncryption,
            WebhookDestinationValidator webhookDestinationValidator,
            ApplicationEventPublisher events
    ) {
        this.agentRepository = agentRepository;
        this.agentToolRepository = agentToolRepository;
        this.credentialEncryption = credentialEncryption;
        this.webhookDestinationValidator = webhookDestinationValidator;
        this.events = events;
    }

    AgentToolService(
            AgentRepository agentRepository,
            AgentToolRepository agentToolRepository,
            CredentialEncryption credentialEncryption,
            WebhookDestinationValidator webhookDestinationValidator
    ) {
        this(agentRepository, agentToolRepository, credentialEncryption, webhookDestinationValidator, event -> { });
    }

    @Transactional(readOnly = true)
    public List<AgentTool> list(UUID tenantId, UUID agentId) {
        ensureAgent(tenantId, agentId);
        return agentToolRepository.findByAgent_IdOrderByDisplayOrderAsc(agentId);
    }

    @Transactional(readOnly = true)
    public AgentTool get(UUID tenantId, UUID toolId) {
        return agentToolRepository.findByIdAndAgent_Tenant_Id(toolId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent tool not found"));
    }

    @Transactional
    public AgentTool create(UUID tenantId, UUID agentId, AgentToolRequest request) {
        var agent = ensureAgent(tenantId, agentId);
        validate(request);
        if (agentToolRepository.existsByAgent_IdAndToolName(agentId, request.toolName())) {
            throw new IllegalArgumentException("Tool already exists for agent: " + request.toolName());
        }
        var tool = new AgentTool(
                agent,
                request.toolName(),
                request.toolDescription(),
                request.parametersSchema(),
                request.fulfillmentType(),
                request.active(),
                request.displayOrder()
        );
        apply(tool, request, true);
        var saved = agentToolRepository.save(tool);
        events.publishEvent(new AgentConfigurationChanged(tenantId, agentId, "Added tool " + saved.getToolName()));
        return saved;
    }

    @Transactional
    public AgentTool update(UUID tenantId, UUID toolId, AgentToolRequest request) {
        validate(request);
        var tool = get(tenantId, toolId);
        apply(tool, request, false);
        events.publishEvent(new AgentConfigurationChanged(tenantId, tool.getAgent().getId(),
                "Changed tool " + tool.getToolName()));
        return tool;
    }

    @Transactional
    public AgentTool deactivate(UUID tenantId, UUID toolId) {
        var tool = get(tenantId, toolId);
        tool.deactivate();
        events.publishEvent(new AgentConfigurationChanged(tenantId, tool.getAgent().getId(),
                "Turned off tool " + tool.getToolName()));
        return tool;
    }

    private void apply(AgentTool tool, AgentToolRequest request, boolean creating) {
        tool.update(
                request.toolName(),
                request.toolDescription(),
                request.parametersSchema(),
                request.fulfillmentType(),
                request.webhookUrl(),
                method(request.webhookMethod()),
                authType(request.authType()),
                request.authCredential() == null || request.authCredential().isBlank() ? null : credentialEncryption.encrypt(request.authCredential()),
                request.authHeaderName(),
                request.calendarType(),
                request.calendarCredentialId(),
                request.active(),
                request.displayOrder()
        );
        var effect = request.actionEffect() == null || request.actionEffect().isBlank()
                ? (creating ? defaultEffect(request) : tool.actionEffect())
                : ToolActionEffect.from(request.actionEffect());
        var confirmation = request.confirmationPolicy() == null || request.confirmationPolicy().isBlank()
                ? (creating ? defaultConfirmation(effect) : tool.confirmationPolicy())
                : ToolConfirmationPolicy.from(request.confirmationPolicy());
        validatePolicy(request.fulfillmentType(), request.toolName(), effect, confirmation);
        tool.configureActionPolicy(effect, confirmation);
    }

    private com.sauti.agent.Agent ensureAgent(UUID tenantId, UUID agentId) {
        return agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
    }

    private void validate(AgentToolRequest request) {
        if (ConversationStateTool.NAME.equals(request.toolName())) {
            throw new IllegalArgumentException("Reserved platform tool name: " + request.toolName());
        }
        if (!FULFILLMENT_TYPES.contains(request.fulfillmentType())) {
            throw new IllegalArgumentException("Unsupported fulfillment type: " + request.fulfillmentType());
        }
        if (!AUTH_TYPES.contains(authType(request.authType()))) {
            throw new IllegalArgumentException("Unsupported auth type: " + request.authType());
        }
        if (!METHODS.contains(method(request.webhookMethod()))) {
            throw new IllegalArgumentException("Unsupported webhook method: " + request.webhookMethod());
        }
        validateJsonSchema(request.parametersSchema());
        if (request.actionEffect() != null && !request.actionEffect().isBlank()) {
            ToolActionEffect.from(request.actionEffect());
        }
        if (request.confirmationPolicy() != null && !request.confirmationPolicy().isBlank()) {
            ToolConfirmationPolicy.from(request.confirmationPolicy());
        }
        if ("webhook".equals(request.fulfillmentType())) {
            webhookDestinationValidator.validateHttpsPublicUrl(request.webhookUrl());
            if ("api_key".equals(authType(request.authType())) && (request.authHeaderName() == null || request.authHeaderName().isBlank())) {
                throw new IllegalArgumentException("authHeaderName is required for api_key auth");
            }
        }
    }

    private ToolActionEffect defaultEffect(AgentToolRequest request) {
        return switch (request.fulfillmentType()) {
            case "sauti_sms" -> ToolActionEffect.EXTERNAL_COMMUNICATION;
            case "call_transfer" -> ToolActionEffect.TRANSFER;
            case "sauti_integration" -> ToolActionEffect.DATA_WRITE;
            case "webhook" -> "GET".equals(method(request.webhookMethod()))
                    ? ToolActionEffect.READ_ONLY : ToolActionEffect.DATA_WRITE;
            default -> ToolActionEffect.READ_ONLY;
        };
    }

    private ToolConfirmationPolicy defaultConfirmation(ToolActionEffect effect) {
        return effect.isSideEffecting() ? ToolConfirmationPolicy.EXPLICIT : ToolConfirmationPolicy.NONE;
    }

    /**
     * Refuses any policy weaker than {@link ToolSafetyFloor}: a built-in action marked as something
     * milder, a side effect without confirmation, or a verified review on a tool that has none.
     */
    private void validatePolicy(String fulfillmentType, String toolName, ToolActionEffect effect,
                                ToolConfirmationPolicy confirmation) {
        if (effect == ToolActionEffect.READ_ONLY && confirmation != ToolConfirmationPolicy.NONE) {
            throw new IllegalArgumentException("Read-only tools cannot require action confirmation");
        }
        if (ToolSafetyFloor.effect(fulfillmentType, toolName, effect) != effect) {
            throw new IllegalArgumentException(toolName + " must be declared as "
                    + ToolSafetyFloor.minimumEffect(fulfillmentType, toolName).value());
        }
        if (effect.isSideEffecting() && confirmation == ToolConfirmationPolicy.NONE) {
            throw new IllegalArgumentException(
                    "Tools that change something (" + effect.value() + ") require confirmation from the caller");
        }
        if (confirmation == ToolConfirmationPolicy.VERIFIED_REVIEW
                && !ToolSafetyFloor.REVIEW_FULFILLMENT.equals(fulfillmentType)) {
            throw new IllegalArgumentException("Verified review is only available for calendar bookings; use explicit");
        }
    }

    private void validateJsonSchema(Map<String, Object> schema) {
        if (!"object".equals(schema.get("type"))) {
            throw new IllegalArgumentException("parametersSchema.type must be object");
        }
        if (!(schema.get("properties") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("parametersSchema.properties is required");
        }
    }

    private String authType(String authType) {
        return authType == null || authType.isBlank() ? "none" : authType;
    }

    private String method(String method) {
        return method == null || method.isBlank() ? "POST" : method.toUpperCase();
    }
}
