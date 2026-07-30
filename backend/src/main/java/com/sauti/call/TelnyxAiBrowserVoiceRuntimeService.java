package com.sauti.call;

import com.sauti.agent.Agent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelnyxAiBrowserVoiceRuntimeService {
    private final ManagedVoiceAgentProvisioningService provisioningService;
    private final String environment;
    private final String region;

    public TelnyxAiBrowserVoiceRuntimeService(
            ManagedVoiceAgentProvisioningService provisioningService,
            @Value("${sauti.telnyx.ai-environment:production}") String environment,
            @Value("${sauti.telnyx.ai-region:}") String region
    ) {
        this.provisioningService = provisioningService;
        this.environment = "development".equalsIgnoreCase(trim(environment))
                ? "development"
                : "production";
        this.region = trim(region);
    }

    public String provider() {
        return "telnyx";
    }

    public boolean isConfigured() {
        return provisioningService.isConfigured();
    }

    public BrowserVoiceRuntimeSession prepare(Call call, String greeting, String callbackToken) {
        if (!isConfigured()) {
            throw new IllegalStateException("Telnyx browser calls require TELNYX_API_KEY.");
        }
        return session(provisioningService.existing(call), call.getTwilioCallSid());
    }

    public BrowserVoiceRuntimeSession prepare(Agent agent, String greeting) {
        return prepare(agent, greeting, agent.getDefaultLanguage());
    }

    public BrowserVoiceRuntimeSession prepare(Agent agent, String greeting, String language) {
        if (!isConfigured()) {
            throw new IllegalStateException("Telnyx browser calls require TELNYX_API_KEY.");
        }
        var managedAgent = provisioningService.synchronize(agent, greeting, language);
        return session(managedAgent, "");
    }

    private BrowserVoiceRuntimeSession session(
            ManagedVoiceAgentReference managedAgent,
            String callSid
    ) {
        var configuration = new LinkedHashMap<String, Object>();
        configuration.put("agentId", managedAgent.externalAgentId());
        configuration.put(
                "versionId",
                managedAgent.externalVersionId().isBlank() ? "main" : managedAgent.externalVersionId()
        );
        configuration.put("environment", environment);
        if (callSid != null && !callSid.isBlank()) configuration.put("callSid", callSid);
        if (!region.isBlank()) configuration.put("region", region);
        return new BrowserVoiceRuntimeSession(provider(), "", "", Map.copyOf(configuration));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
