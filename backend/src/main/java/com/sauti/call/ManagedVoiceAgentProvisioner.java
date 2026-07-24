package com.sauti.call;

public interface ManagedVoiceAgentProvisioner {
    String provider();

    boolean isConfigured();

    ManagedVoiceAgentReference synchronize(
            ManagedVoiceAgentBlueprint blueprint,
            ManagedVoiceAgentReference existing
    );
}
