package com.sauti.agent;

import com.sauti.agent.AgentReadinessDtos.AgentReadinessResponse;
import com.sauti.tool.AgentToolRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentReadinessService {
    private final AgentRepository agentRepository;
    private final AgentVariableService agentVariableService;
    private final AgentToolRepository agentToolRepository;

    public AgentReadinessService(
            AgentRepository agentRepository,
            AgentVariableService agentVariableService,
            AgentToolRepository agentToolRepository
    ) {
        this.agentRepository = agentRepository;
        this.agentVariableService = agentVariableService;
        this.agentToolRepository = agentToolRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentReadinessResponse> list(UUID tenantId) {
        return agentRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::readiness)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentReadinessResponse get(UUID tenantId, UUID agentId) {
        var agent = agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Agent not found"));
        return readiness(agent);
    }

    private AgentReadinessResponse readiness(Agent agent) {
        var missingVariables = agentVariableService.missingRequired(agent.getId());
        boolean businessDetailsComplete = missingVariables.isEmpty();
        boolean calendarRequired = agent.isBookingEnabled();
        boolean calendarConfigured = !calendarRequired || hasProductionCalendar(agent);
        boolean phoneConfigured = agent.getTwilioPhoneNumber() != null
                && !agent.getTwilioPhoneNumber().isBlank()
                && (agent.getPhoneNumberStatus() == null
                    || "active".equalsIgnoreCase(agent.getPhoneNumberStatus()));
        boolean webVoiceConfigured = agent.isWebVoiceEnabled();
        boolean whatsappConfigured = agent.isWhatsappEnabled()
                && agent.getWhatsappPhoneNumberId() != null
                && !agent.getWhatsappPhoneNumberId().isBlank();
        boolean channelConfigured = phoneConfigured || webVoiceConfigured || whatsappConfigured;
        boolean readyToActivate = businessDetailsComplete && calendarConfigured && channelConfigured;
        String nextStep = !businessDetailsComplete ? "complete_business_details"
                : !calendarConfigured ? "connect_calendar"
                : !channelConfigured ? "enable_channel"
                : !agent.isActive() ? "activate_agent"
                : "ready";
        return new AgentReadinessResponse(
                agent.getId(),
                businessDetailsComplete,
                calendarRequired,
                calendarConfigured,
                phoneConfigured,
                webVoiceConfigured,
                whatsappConfigured,
                channelConfigured,
                agent.isActive(),
                readyToActivate,
                nextStep,
                missingVariables
        );
    }

    private boolean hasProductionCalendar(Agent agent) {
        if (agent.getCalendarProvider() == null || agent.getCalendarProvider().isBlank()) {
            return true;
        }
        if ("Set up later".equals(agent.getCalendarProvider())) {
            return false;
        }
        return agentToolRepository.findByAgent_IdOrderByDisplayOrderAsc(agent.getId())
                .stream()
                .anyMatch(tool -> tool.getCalendarCredentialId() != null
                        || ("webhook".equals(tool.getFulfillmentType())
                            && tool.getWebhookUrl() != null
                            && !tool.getWebhookUrl().isBlank()));
    }
}
