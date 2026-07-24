package com.sauti.call;

import com.sauti.agent.AgentBusinessIdentity;
import com.sauti.llm.ConversationOrchestrator;
import com.sauti.tool.AgentToolLoader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ManagedVoiceAgentBlueprintFactory {
    private final ConversationOrchestrator conversationOrchestrator;
    private final AgentToolLoader agentToolLoader;

    public ManagedVoiceAgentBlueprintFactory(
            ConversationOrchestrator conversationOrchestrator,
            AgentToolLoader agentToolLoader
    ) {
        this.conversationOrchestrator = conversationOrchestrator;
        this.agentToolLoader = agentToolLoader;
    }

    public ManagedVoiceAgentBlueprint create(Call call, String greeting) {
        var agent = call.getAgent();
        var language = call.getLanguageDetected() == null || call.getLanguageDetected().isBlank()
                ? agent.getDefaultLanguage()
                : call.getLanguageDetected();
        var configuredKeywords = agent.getSttBoostedKeywords() == null
                ? Stream.<String>empty()
                : Arrays.stream(agent.getSttBoostedKeywords().split(","));
        var keywords = Stream.concat(
                        Stream.of(AgentBusinessIdentity.fromPrompt(agent)),
                        configuredKeywords
                )
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(100)
                .toList();
        return new ManagedVoiceAgentBlueprint(
                "Sauti " + agent.getName(),
                greeting == null ? "" : greeting.trim(),
                conversationOrchestrator.realtimeInstructions(call, language),
                language,
                List.copyOf(agent.getSupportedLanguages()),
                agentToolLoader.loadForAgent(agent.getId()),
                agent.getMaxCallDurationSeconds(),
                agent.getBargeInSensitivity(),
                agent.getSttEndpointingMs(),
                keywords
        );
    }
}
