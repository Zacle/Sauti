package com.sauti.tool;

import com.sauti.agent.AgentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Adds newly introduced platform tools to existing agents after a deployment.
 * DefaultToolSeeder is idempotent and preserves existing customized rows.
 */
@Component
public class DefaultToolBackfill implements ApplicationRunner {
    private final AgentRepository agentRepository;
    private final DefaultToolSeeder defaultToolSeeder;

    public DefaultToolBackfill(
            AgentRepository agentRepository,
            DefaultToolSeeder defaultToolSeeder
    ) {
        this.agentRepository = agentRepository;
        this.defaultToolSeeder = defaultToolSeeder;
    }

    @Override
    public void run(ApplicationArguments args) {
        agentRepository.findAll().forEach(defaultToolSeeder::seedDefaults);
    }
}
