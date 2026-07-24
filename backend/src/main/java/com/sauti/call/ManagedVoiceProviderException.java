package com.sauti.call;

public class ManagedVoiceProviderException extends RuntimeException {
    private final String provider;
    private final int providerStatus;

    public ManagedVoiceProviderException(String provider, int providerStatus) {
        this(provider, providerStatus, "");
    }

    public ManagedVoiceProviderException(String provider, int providerStatus, String validationSummary) {
        super(message(provider, providerStatus, validationSummary));
        this.provider = provider;
        this.providerStatus = providerStatus;
    }

    public String provider() {
        return provider;
    }

    public int providerStatus() {
        return providerStatus;
    }

    private static String message(String provider, int providerStatus, String validationSummary) {
        if (providerStatus == 401 || providerStatus == 403) {
            return provider + " rejected managed voice setup with status " + providerStatus
                    + ". Check the API key and its agent/tool permissions.";
        }
        if (providerStatus == 422) {
            var detail = validationSummary == null ? "" : validationSummary.trim();
            return provider + " rejected the generated agent/tool configuration with status 422."
                    + (detail.isBlank() ? "" : " " + detail);
        }
        return provider + " rejected managed voice setup with status " + providerStatus + ".";
    }
}
