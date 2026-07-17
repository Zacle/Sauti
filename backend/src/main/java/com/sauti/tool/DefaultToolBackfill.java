package com.sauti.tool;

import com.sauti.agent.AgentRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DefaultToolBackfill {
    private final AgentRepository agents;
    private final DefaultToolSeeder seeder;

    public DefaultToolBackfill(AgentRepository agents, DefaultToolSeeder seeder) {
        this.agents = agents;
        this.seeder = seeder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void addMissingDefaultTools() {
        agents.findAll().forEach(seeder::seedDefaults);
    }
}
