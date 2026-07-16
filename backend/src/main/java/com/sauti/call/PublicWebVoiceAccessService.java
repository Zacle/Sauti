package com.sauti.call;

import com.sauti.agent.Agent;
import com.sauti.agent.AgentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns public Web Voice lookup and authorization rules outside the HTTP adapter. */
@Service
public class PublicWebVoiceAccessService {
    private final AgentRepository agentRepository;
    private final CallRepository callRepository;

    public PublicWebVoiceAccessService(AgentRepository agentRepository, CallRepository callRepository) {
        this.agentRepository = agentRepository;
        this.callRepository = callRepository;
    }

    @Transactional(readOnly = true)
    public Agent requirePublicAgent(String publicId) {
        return agentRepository.findByWebVoicePublicId(publicId)
                .filter(Agent::isActive)
                .filter(Agent::isWebVoiceEnabled)
                .orElseThrow(() -> new EntityNotFoundException("Web Voice agent not found"));
    }

    @Transactional(readOnly = true)
    public Call requireActiveCall(String sessionId, String publicAgentId) {
        return requireCall(sessionId, publicAgentId, true);
    }

    @Transactional(readOnly = true)
    public Call requireCall(String sessionId, String publicAgentId) {
        return requireCall(sessionId, publicAgentId, false);
    }

    private Call requireCall(String sessionId, String publicAgentId, boolean activeRequired) {
        return callRepository.findByTwilioCallSid(sessionId)
                .filter(candidate -> "web".equals(candidate.getDirection()))
                .filter(candidate -> !activeRequired || candidate.isActive())
                .filter(candidate -> publicAgentId.equals(candidate.getAgent().getWebVoicePublicId()))
                .orElseThrow(() -> new EntityNotFoundException("Web Voice session is unavailable"));
    }
}
