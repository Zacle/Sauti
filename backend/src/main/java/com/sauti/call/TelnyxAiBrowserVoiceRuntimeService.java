package com.sauti.call;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelnyxAiBrowserVoiceRuntimeService implements BrowserVoiceRuntimeProvider {
    private final ManagedVoiceRuntimeSupport support;
    private final ManagedVoiceAgentProvisioningService provisioningService;
    private final String environment;
    private final String region;

    public TelnyxAiBrowserVoiceRuntimeService(
            ManagedVoiceRuntimeSupport support,
            ManagedVoiceAgentProvisioningService provisioningService,
            @Value("${sauti.telnyx.ai-environment:production}") String environment,
            @Value("${sauti.telnyx.ai-region:}") String region
    ) {
        this.support = support;
        this.provisioningService = provisioningService;
        this.environment = "development".equalsIgnoreCase(trim(environment))
                ? "development"
                : "production";
        this.region = trim(region);
    }

    @Override
    public String provider() {
        return "telnyx";
    }

    @Override
    public boolean isConfigured() {
        return provisioningService.isConfigured(provider());
    }

    @Override
    public BrowserVoiceRuntimeSession prepare(Call call, String greeting, String callbackToken) {
        if (!isConfigured()) {
            throw new IllegalStateException("Telnyx browser calls require TELNYX_API_KEY.");
        }
        var managedAgent = provisioningService.resolve(provider(), call, greeting);
        var configuration = new LinkedHashMap<String, Object>();
        configuration.put("agentId", managedAgent.externalAgentId());
        configuration.put(
                "versionId",
                managedAgent.externalVersionId().isBlank() ? "main" : managedAgent.externalVersionId()
        );
        configuration.put("environment", environment);
        configuration.put("greeting", greeting == null ? "" : greeting);
        configuration.put("toolNames", support.toolNames(call));
        configuration.put("callSid", call.getTwilioCallSid());
        if (!region.isBlank()) configuration.put("region", region);
        return new BrowserVoiceRuntimeSession(provider(), "", "", Map.copyOf(configuration));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
