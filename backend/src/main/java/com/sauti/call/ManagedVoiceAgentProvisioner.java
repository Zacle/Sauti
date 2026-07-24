package com.sauti.call;

public interface ManagedVoiceAgentProvisioner {
    String provider();

    boolean isConfigured();

    default String configurationVersion() {
        return "1";
    }

    ManagedVoiceAgentReference synchronize(
            ManagedVoiceAgentBlueprint blueprint,
            ManagedVoiceAgentReference existing
    );
}
