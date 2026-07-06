package com.sauti.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentDtos.AgentRequest;
import com.sauti.agent.AgentTemplateDtos.AgentTemplateRequest;
import com.sauti.agent.AgentTemplateDtos.CreateAgentFromTemplateRequest;
import com.sauti.tenant.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTemplateService {
    private static final List<String> SUPPORTED_LANGUAGES = List.of("fr", "ar", "sw", "en");

    private final AgentTemplateRepository templateRepository;
    private final TenantRepository tenantRepository;
    private final AgentService agentService;
    private final AgentVariableService agentVariableService;
    private final ObjectMapper objectMapper;

    public AgentTemplateService(
            AgentTemplateRepository templateRepository,
            TenantRepository tenantRepository,
            AgentService agentService,
            AgentVariableService agentVariableService,
            ObjectMapper objectMapper
    ) {
        this.templateRepository = templateRepository;
        this.tenantRepository = tenantRepository;
        this.agentService = agentService;
        this.agentVariableService = agentVariableService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentTemplate> list(UUID tenantId) {
        return templateRepository.findAllAccessible(tenantId);
    }

    @Transactional(readOnly = true)
    public List<AgentTemplate> listSystemTemplates() {
        return templateRepository.findAllByTenantIsNullAndPublishedTrueOrderByCategoryAscNameAsc();
    }

    @Transactional(readOnly = true)
    public AgentTemplate get(UUID tenantId, UUID templateId) {
        return templateRepository.findAccessibleById(templateId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent template not found"));
    }

    @Transactional(readOnly = true)
    public AgentTemplate getSystemTemplate(UUID templateId) {
        return templateRepository.findByIdAndTenantIsNullAndPublishedTrue(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Agent template not found"));
    }

    @Transactional
    public AgentTemplate create(UUID tenantId, AgentTemplateRequest request) {
        validate(request);
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        return templateRepository.save(new AgentTemplate(tenant, request));
    }

    @Transactional
    public AgentTemplate update(UUID tenantId, UUID templateId, AgentTemplateRequest request) {
        validate(request);
        var template = ownedTemplate(tenantId, templateId);
        template.update(request);
        return template;
    }

    @Transactional
    public void delete(UUID tenantId, UUID templateId) {
        templateRepository.delete(ownedTemplate(tenantId, templateId));
    }

    @Transactional
    public Agent createAgent(UUID tenantId, UUID templateId, CreateAgentFromTemplateRequest request) {
        var template = get(tenantId, templateId);
        var configuration = parseConfiguration(template.getConfigurationJson());
        boolean bookingEnabled = configuration.path("bookingEnabled").asBoolean(false);
        List<String> escalationPhrases = configuration.has("escalationPhrases")
                ? objectMapper.convertValue(
                        configuration.path("escalationPhrases"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
                )
                : List.of("speak to a person", "talk to a human", "human agent");
        String ttsVoiceId = textOrNull(configuration, "ttsVoiceId");
        String operatingHours = textOrNull(configuration, "operatingHours");
        String afterHoursBehavior = textOrNull(configuration, "afterHoursBehavior");
        String afterHoursMessage = textOrNull(configuration, "afterHoursMessage");
        String requestedName = request == null ? null : request.name();
        String requestedTimezone = request == null ? null : request.timezone();
        String transferNumber = request == null ? null : request.humanTransferNumber();
        var agentRequest = new AgentRequest(
                requestedName == null || requestedName.isBlank() ? template.getName() : requestedName.trim(),
                template.getDescription(),
                template.getGreetingMessage(),
                template.getSystemPrompt(),
                template.getDefaultLanguage(),
                template.getSupportedLanguages(),
                ttsVoiceId,
                transferNumber,
                escalationPhrases,
                bookingEnabled,
                requestedTimezone == null || requestedTimezone.isBlank() ? "Africa/Nairobi" : requestedTimezone,
                "",
                operatingHours,
                afterHoursBehavior,
                afterHoursMessage,
                300,
                true,
                false,
                "standard",
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                "#", 5, 8, java.util.Map.of(),
                false, java.util.List.of(), true,
                false, null
        );
        var agent = agentService.create(tenantId, agentRequest);
        agentVariableService.seedDefinitions(agent, configuration);
        return agent;
    }

    private String textOrNull(JsonNode configuration, String field) {
        String value = configuration.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private AgentTemplate ownedTemplate(UUID tenantId, UUID templateId) {
        return templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant agent template not found"));
    }

    private void validate(AgentTemplateRequest request) {
        if (!SUPPORTED_LANGUAGES.contains(request.defaultLanguage())) {
            throw new IllegalArgumentException("Unsupported default language");
        }
        if (request.supportedLanguages().stream().anyMatch(language -> !SUPPORTED_LANGUAGES.contains(language))) {
            throw new IllegalArgumentException("Supported languages are fr, ar, sw, and en");
        }
        if (!request.supportedLanguages().contains(request.defaultLanguage())) {
            throw new IllegalArgumentException("Default language must be included in supported languages");
        }
        parseConfiguration(request.configurationJson());
    }

    private JsonNode parseConfiguration(String configurationJson) {
        String value = configurationJson == null || configurationJson.isBlank() ? "{}" : configurationJson;
        try {
            var parsed = objectMapper.readTree(value);
            if (!parsed.isObject()) {
                throw new IllegalArgumentException("configurationJson must be a JSON object");
            }
            return parsed;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("configurationJson must contain valid JSON");
        }
    }
}
