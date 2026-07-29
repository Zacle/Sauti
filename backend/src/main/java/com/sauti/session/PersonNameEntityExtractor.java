package com.sauti.session;

import com.sauti.call.Call;

/**
 * Semantic boundary for turning a caller's multilingual name answer into the
 * person-name entity stored in authoritative conversation state.
 */
@FunctionalInterface
public interface PersonNameEntityExtractor {
    String extract(Call call, String candidate);
}
