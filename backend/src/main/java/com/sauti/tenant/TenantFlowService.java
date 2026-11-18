package com.sauti.tenant;

import com.sauti.agent.AgentRepository;
import com.sauti.tenant.TenantDtos.OnboardingStatusResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantFlowService {
    private final TenantRepository tenantRepository;
    private final AgentRepository agentRepository;

    public TenantFlowService(TenantRepository tenantRepository, AgentRepository agentRepository) {
        this.tenantRepository = tenantRepository;
        this.agentRepository = agentRepository;
    }

    /** Only verified people can sign in, so a caller of this endpoint has always verified their email. */
    @Transactional(readOnly = true)
    public OnboardingStatusResponse onboardingStatus(UUID tenantId) {
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        var agents = agentRepository.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId());
        boolean hasAgent = !agents.isEmpty();
        boolean hasActiveAgent = agents.stream().anyMatch(agent -> agent.isActive());
        boolean hasProvisionedNumber = agents.stream().anyMatch(agent -> agent.getTwilioPhoneNumber() != null && !agent.getTwilioPhoneNumber().isBlank());
        boolean hasEnabledChannel = hasProvisionedNumber || agents.stream().anyMatch(agent -> agent.isWebVoiceEnabled());
        String nextStep = nextStep(hasAgent, hasEnabledChannel, hasActiveAgent);
        return new OnboardingStatusResponse(true, true, hasAgent, hasActiveAgent, hasProvisionedNumber, nextStep);
    }

    private String nextStep(boolean hasAgent, boolean hasEnabledChannel, boolean hasActiveAgent) {
        if (!hasAgent) {
            return "create_agent";
        }
        if (!hasEnabledChannel) {
            return "enable_channel";
        }
        if (!hasActiveAgent) {
            return "activate_agent";
        }
        return "ready_for_calls";
    }
}
