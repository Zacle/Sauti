package com.sauti.call;

public record ManagedVoiceAgentReference(
        String externalAgentId,
        String externalVersionId,
        String externalResourcesJson
) {
    public ManagedVoiceAgentReference {
        externalAgentId = externalAgentId == null ? "" : externalAgentId.trim();
        externalVersionId = externalVersionId == null ? "" : externalVersionId.trim();
        externalResourcesJson = externalResourcesJson == null || externalResourcesJson.isBlank()
                ? "{}"
                : externalResourcesJson;
        if (externalAgentId.isBlank()) {
            throw new IllegalArgumentException("A managed provider must return an external agent id");
        }
    }
}
