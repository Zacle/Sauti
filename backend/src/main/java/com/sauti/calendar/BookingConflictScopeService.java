package com.sauti.calendar;

import com.sauti.agent.AgentRepository;
import com.sauti.tool.AgentToolRepository;
import com.sauti.tool.CalendarCredentialRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separates booking ownership from capacity. Agents retain exclusive ownership
 * of their bookings, while agents using the same Google Calendar credential
 * share one availability/conflict scope.
 */
@Service
public class BookingConflictScopeService {
    private final AgentToolRepository agentToolRepository;
    private final CalendarCredentialRepository credentialRepository;
    private final AgentRepository agentRepository;

    public BookingConflictScopeService(
            AgentToolRepository agentToolRepository,
            CalendarCredentialRepository credentialRepository,
            AgentRepository agentRepository
    ) {
        this.agentToolRepository = agentToolRepository;
        this.credentialRepository = credentialRepository;
        this.agentRepository = agentRepository;
    }

    @Transactional(readOnly = true)
    public ConflictScope resolve(UUID tenantId, UUID agentId) {
        return resolve(tenantId, agentId, false);
    }

    @Transactional
    public ConflictScope resolveAndLock(UUID tenantId, UUID agentId) {
        return resolve(tenantId, agentId, true);
    }

    private ConflictScope resolve(UUID tenantId, UUID agentId, boolean lock) {
        var tool = agentToolRepository
                .findByAgent_IdAndToolNameAndIsActiveTrue(agentId, "check_availability")
                .filter(candidate -> "google".equalsIgnoreCase(candidate.getCalendarType()))
                .filter(candidate -> candidate.getCalendarCredentialId() != null)
                .orElse(null);
        if (tool == null) return localScope(tenantId, agentId, lock);

        var credentialId = tool.getCalendarCredentialId();
        var credential = lock
                ? credentialRepository.findByIdAndTenantIdForUpdate(credentialId, tenantId)
                : credentialRepository.findByIdAndTenant_Id(credentialId, tenantId);
        if (credential.isEmpty()) return localScope(tenantId, agentId, lock);

        var agentIds = new LinkedHashSet<>(agentToolRepository.findActiveAgentIdsSharingCalendar(
                tenantId,
                credentialId,
                "check_availability"
        ));
        agentIds.add(agentId);
        return new ConflictScope(credentialId, List.copyOf(agentIds));
    }

    private ConflictScope localScope(UUID tenantId, UUID agentId, boolean lock) {
        if (lock) {
            agentRepository.findByIdAndTenantIdForUpdate(agentId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Agent not found"));
        }
        return new ConflictScope(null, List.of(agentId));
    }

    public record ConflictScope(UUID calendarCredentialId, List<UUID> agentIds) {
    }
}
