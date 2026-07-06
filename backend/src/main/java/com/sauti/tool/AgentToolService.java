package com.sauti.tool;

import com.sauti.agent.AgentRepository;
import com.sauti.tool.AgentToolDtos.AgentToolRequest;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentToolService {
    private static final Set<String> FULFILLMENT_TYPES = Set.of(
            "sauti_calendar", "sauti_sms", "sauti_integration", "twilio_transfer", "webhook", "noop"
    );
    private static final Set<String> AUTH_TYPES = Set.of("none", "bearer", "api_key", "hmac_sha256");
    private static final Set<String> METHODS = Set.of("GET", "POST");

    private final AgentRepository agentRepository;
    private final AgentToolRepository agentToolRepository;
    private final CredentialEncryption credentialEncryption;
    private final WebhookDestinationValidator webhookDestinationValidator;

    public AgentToolService(
            AgentRepository agentRepository,
            AgentToolRepository agentToolRepository,
            CredentialEncryption credentialEncryption,
            WebhookDestinationValidator webhookDestinationValidator
    ) {
        this.agentRepository = agentRepository;
        this.agentToolRepository = agentToolRepository;
        this.credentialEncryption = credentialEncryption;
        this.webhookDestinationValidator = webhookDestinationValidator;
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
        apply(tool, request);
        return agentToolRepository.save(tool);
    }

    @Transactional
    public AgentTool update(UUID tenantId, UUID toolId, AgentToolRequest request) {
        validate(request);
        var tool = get(tenantId, toolId);
        apply(tool, request);
        return tool;
    }

    @Transactional
    public AgentTool deactivate(UUID tenantId, UUID toolId) {
        var tool = get(tenantId, toolId);
        tool.deactivate();
        return tool;
    }

    private void apply(AgentTool tool, AgentToolRequest request) {
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
    }

    private com.sauti.agent.Agent ensureAgent(UUID tenantId, UUID agentId) {
        return agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
    }

    private void validate(AgentToolRequest request) {
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
        if ("webhook".equals(request.fulfillmentType())) {
            webhookDestinationValidator.validateHttpsPublicUrl(request.webhookUrl());
            if ("api_key".equals(authType(request.authType())) && (request.authHeaderName() == null || request.authHeaderName().isBlank())) {
                throw new IllegalArgumentException("authHeaderName is required for api_key auth");
            }
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
