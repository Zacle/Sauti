package com.sauti.tenant;

import com.sauti.agent.AgentRepository;
import com.sauti.auth.UserRepository;
import com.sauti.tenant.TenantDtos.OnboardingStatusResponse;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantFlowService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;

    public TenantFlowService(TenantRepository tenantRepository, UserRepository userRepository, AgentRepository agentRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional(readOnly = true)
    public OnboardingStatusResponse onboardingStatus(UUID tenantId, UUID userId) {
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        var agents = agentRepository.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId());
        boolean hasAgent = !agents.isEmpty();
        boolean hasActiveAgent = agents.stream().anyMatch(agent -> agent.isActive());
        boolean hasProvisionedNumber = agents.stream().anyMatch(agent -> agent.getTwilioPhoneNumber() != null && !agent.getTwilioPhoneNumber().isBlank());
        boolean hasEnabledChannel = hasProvisionedNumber || agents.stream().anyMatch(agent -> agent.isWebVoiceEnabled());
        String nextStep = nextStep(user.isEmailVerified(), hasAgent, hasEnabledChannel, hasActiveAgent);
        return new OnboardingStatusResponse(true, user.isEmailVerified(), hasAgent, hasActiveAgent, hasProvisionedNumber, nextStep);
    }

    private String nextStep(boolean emailVerified, boolean hasAgent, boolean hasEnabledChannel, boolean hasActiveAgent) {
        if (!emailVerified) {
            return "verify_email";
        }
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
