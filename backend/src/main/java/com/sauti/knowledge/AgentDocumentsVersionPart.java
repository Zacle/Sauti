package com.sauti.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentConfigurationPart;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Which documents an agent had in each version. Documents are files, so a version only names them:
 * restoring never deletes a document added since, and a deleted one cannot be brought back. The
 * restore note says when the documents differ from the version.
 */
@Component
@Order(30)
class AgentDocumentsVersionPart implements AgentConfigurationPart {
    private final KnowledgeDocumentRepository documents;
    private final ObjectMapper json;

    AgentDocumentsVersionPart(KnowledgeDocumentRepository documents, ObjectMapper json) {
        this.documents = documents;
        this.json = json;
    }

    @Override
    public String key() {
        return "documents";
    }

    @Override
    public JsonNode capture(UUID tenantId, UUID agentId) {
        var captured = json.createArrayNode();
        for (var document : documents.findAllByTenantIdAndAgentIdOrderByCreatedAtDesc(tenantId, agentId)) {
            captured.addObject().put("id", document.getId().toString()).put("fileName", document.getFileName());
        }
        return captured;
    }

    @Override
    public List<String> restore(UUID tenantId, UUID agentId, JsonNode captured) {
        var then = new HashSet<String>();
        captured.forEach(node -> then.add(node.path("id").asText()));
        var now = new HashSet<String>();
        documents.findAllByTenantIdAndAgentIdOrderByCreatedAtDesc(tenantId, agentId)
                .forEach(document -> now.add(document.getId().toString()));
        return then.equals(now) ? List.of() : List.of("Documents were kept as they are now.");
    }
}
