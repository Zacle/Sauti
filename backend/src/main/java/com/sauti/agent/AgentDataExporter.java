package com.sauti.agent;

import com.sauti.tenant.application.port.WorkspaceDataExporter;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Agents in a workspace data export. */
@Component
class AgentDataExporter implements WorkspaceDataExporter {
    private final AgentRepository agents;

    AgentDataExporter(AgentRepository agents) {
        this.agents = agents;
    }

    @Override
    public String section() {
        return "agents";
    }

    @Override
    @Transactional(readOnly = true)
    public Object export(UUID workspaceId) {
        return agents.findAllByTenantIdOrderByCreatedAtDesc(workspaceId).stream().map(agent -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("id", agent.getId());
            row.put("name", agent.getName());
            row.put("description", agent.getDescription());
            row.put("active", agent.isActive());
            row.put("phoneNumber", agent.getTwilioPhoneNumber());
            row.put("defaultLanguage", agent.getDefaultLanguage());
            row.put("supportedLanguages", agent.getSupportedLanguages());
            row.put("greetingMessage", agent.getGreetingMessage());
            row.put("systemPrompt", agent.getSystemPrompt());
            row.put("voice", agent.getTtsVoiceId());
            row.put("timezone", agent.getTimezone());
            row.put("operatingHours", agent.getOperatingHours());
            row.put("bookingEnabled", agent.isBookingEnabled());
            row.put("bookableServices", agent.getBookableServices());
            row.put("humanTransferNumber", agent.getHumanTransferNumber());
            row.put("knowledgeBase", agent.getKnowledgeBase());
            return row;
        }).toList();
    }
}
