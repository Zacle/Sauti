package com.sauti.tool;

import java.util.List;
import java.util.UUID;

/**
 * Searches the documents uploaded to an agent. The knowledge module provides it, so the tool layer
 * offers document search on live calls without depending on how documents are stored or embedded.
 */
public interface KnowledgeSearch {
    KnowledgeSearch NONE = new KnowledgeSearch() {
        @Override public boolean hasDocuments(UUID agentId) { return false; }
        @Override public List<String> search(UUID tenantId, UUID agentId, String query) { return List.of(); }
    };

    boolean hasDocuments(UUID agentId);

    /** The most relevant excerpts, best first, within a size budget suitable for a voice turn. */
    List<String> search(UUID tenantId, UUID agentId, String query);
}
