package com.sauti.call;

import com.sauti.tool.AgentToolLoader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ManagedVoiceRuntimeSupport {
    private final AgentToolLoader agentToolLoader;
    private final String publicBaseUrl;

    public ManagedVoiceRuntimeSupport(
            AgentToolLoader agentToolLoader,
            @Value("${sauti.managed-voice.public-base-url:${sauti.telephony.public-base-url:http://localhost:8080}}")
            String publicBaseUrl
    ) {
        this.agentToolLoader = agentToolLoader;
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    public List<String> toolNames(Call call) {
        return agentToolLoader.loadForAgent(call.getAgent().getId()).stream()
                .map(tool -> tool.name())
                .filter(name -> name != null && !name.isBlank() && !"end_call".equals(name))
                .distinct()
                .toList();
    }

    /**
     * The token is scoped to one Sauti call and expires shortly after it. It
     * authorizes only tools already enabled for that agent.
     */
    public String toolUrl(String provider, Call call, String callbackToken) {
        return publicBaseUrl + "/api/v1/public/managed-voice/" + url(provider)
                + "/" + url(call.getTwilioCallSid()) + "/tool?token=" + url(callbackToken);
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        var normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
