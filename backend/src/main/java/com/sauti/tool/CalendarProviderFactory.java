package com.sauti.tool;

import com.sauti.calendar.CalendarProvider;
import com.sauti.calendar.GoogleCalendarApiClient;
import com.sauti.calendar.GoogleCalendarProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CalendarProviderFactory {
    private final CalendarProvider defaultCalendarProvider;
    private final CalendarCredentialRepository calendarCredentialRepository;
    private final GoogleCalendarApiClient googleCalendarApiClient;
    private final AgentToolRepository agentToolRepository;

    public CalendarProviderFactory(
            CalendarProvider defaultCalendarProvider,
            CalendarCredentialRepository calendarCredentialRepository,
            GoogleCalendarApiClient googleCalendarApiClient,
            AgentToolRepository agentToolRepository
    ) {
        this.defaultCalendarProvider = defaultCalendarProvider;
        this.calendarCredentialRepository = calendarCredentialRepository;
        this.googleCalendarApiClient = googleCalendarApiClient;
        this.agentToolRepository = agentToolRepository;
    }

    public CalendarProvider forTool(AgentTool toolConfig) {
        return forTool(toolConfig, toolConfig.getAgent().getTenant().getId());
    }

    /**
     * Resolves a call-scoped provider without dereferencing the tool's lazy agent association.
     * Realtime tools execute on asynchronous threads where that JPA association is detached.
     */
    public CalendarProvider forTool(AgentTool toolConfig, java.util.UUID tenantId) {
        var calendarType = toolConfig.getCalendarType();
        if (calendarType == null || calendarType.isBlank() || "noop_calendar".equals(calendarType) || "local".equals(calendarType)) {
            return defaultCalendarProvider;
        }
        if ("google".equalsIgnoreCase(calendarType) && toolConfig.getCalendarCredentialId() != null) {
            var credential = calendarCredentialRepository.findByIdAndTenant_Id(
                    toolConfig.getCalendarCredentialId(),
                    tenantId
            ).orElseThrow(() -> new IllegalArgumentException("Calendar credential not found"));
            return new GoogleCalendarProvider(credential, googleCalendarApiClient);
        }
        throw new IllegalStateException("Calendar provider is not connected: " + calendarType);
    }

    @Transactional(readOnly = true)
    public CalendarProvider forAgent(java.util.UUID agentId) {
        return connectedForAgent(agentId).orElse(defaultCalendarProvider);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<CalendarProvider> connectedForAgent(java.util.UUID agentId) {
        return agentToolRepository.findByAgent_IdOrderByDisplayOrderAsc(agentId).stream()
                .filter(tool -> "google".equalsIgnoreCase(tool.getCalendarType())
                        && tool.getCalendarCredentialId() != null)
                .findFirst()
                .map(this::forTool);
    }
}
