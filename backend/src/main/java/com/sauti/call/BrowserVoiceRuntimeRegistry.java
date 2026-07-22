package com.sauti.call;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class BrowserVoiceRuntimeRegistry {
    private final Map<String, BrowserVoiceRuntimeProvider> providers;

    public BrowserVoiceRuntimeRegistry(List<BrowserVoiceRuntimeProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> normalize(provider.provider()),
                Function.identity()
        ));
    }

    public boolean supports(String provider) {
        return providers.containsKey(normalize(provider));
    }

    public void requireConfigured(String provider) {
        var runtime = require(provider);
        if (!runtime.isConfigured()) {
            throw new IllegalStateException(runtime.provider() + " test calls are not configured");
        }
    }

    public BrowserVoiceRuntimeSession prepare(String provider, Call call, String greeting, String callbackToken) {
        requireConfigured(provider);
        return require(provider).prepare(call, greeting, callbackToken);
    }

    private BrowserVoiceRuntimeProvider require(String provider) {
        var runtime = providers.get(normalize(provider));
        if (runtime == null) throw new IllegalArgumentException("Unsupported test voice runtime: " + provider);
        return runtime;
    }

    private static String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
