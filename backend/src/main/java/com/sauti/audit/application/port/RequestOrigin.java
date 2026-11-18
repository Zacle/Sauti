package com.sauti.audit.application.port;

/** Where the current request came from, as a one-way hash. Never the raw IP address. */
public interface RequestOrigin {
    /** Null when there is no current request, for example in a background job. */
    String ipHash();
}
