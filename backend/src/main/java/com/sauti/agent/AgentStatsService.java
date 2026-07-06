package com.sauti.agent;

import com.sauti.agent.AgentDtos.AgentStatsResponse;
import com.sauti.call.CallRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentStatsService {
    private final CallRepository callRepository;

    public AgentStatsService(CallRepository callRepository) {
        this.callRepository = callRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentStatsResponse> list(UUID tenantId) {
        return callRepository.agentOutcomeStats(tenantId).stream().map(stat -> {
            long total = stat.getTotalCalls() == null ? 0 : stat.getTotalCalls();
            long bookings = stat.getBookingCalls() == null ? 0 : stat.getBookingCalls();
            double rate = total == 0 ? 0 : Math.round((bookings * 1000.0) / total) / 10.0;
            return new AgentStatsResponse(stat.getAgentId(), total, bookings, rate);
        }).toList();
    }
}
