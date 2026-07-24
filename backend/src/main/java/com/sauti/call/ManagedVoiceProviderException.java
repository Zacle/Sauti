package com.sauti.call;

public class ManagedVoiceProviderException extends RuntimeException {
    private final String provider;
    private final int providerStatus;

    public ManagedVoiceProviderException(String provider, int providerStatus) {
        super(provider + " rejected managed voice setup with status " + providerStatus
                + ". Check the API key and its agent/tool permissions.");
        this.provider = provider;
        this.providerStatus = providerStatus;
    }

    public String provider() {
        return provider;
    }

    public int providerStatus() {
        return providerStatus;
    }
}
