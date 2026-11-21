package com.sauti.call;

import java.util.Optional;

/** Asks the telephony provider whether a phone call is still connected. */
public interface CallLiveness {
    /**
     * @return true while the call is connected, false once it has ended (or the provider no longer
     *         knows it), empty when the provider could not be asked
     */
    Optional<Boolean> isAlive(String providerCallId);
}
