package com.sauti.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sauti.agent.Agent;
import com.sauti.call.Call;
import com.sauti.llm.LlmToolCall;
import com.sauti.llm.LlmToolDefinition;
import com.sauti.tenant.Tenant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KnowledgeSearchToolTest {
    private final Agent agent = new Agent(new Tenant("Clinic", "owner@example.com", "KE"), "Amina", "Hello", "Prompt");
    private final Call call = new Call(agent.getTenant(), agent, "sid-knowledge", "+254700000001", "inbound");

    @Test
    void agentsWithDocumentsAreOfferedDocumentSearchOnLiveCalls() {
        var loader = new AgentToolLoader(mock(AgentToolRepository.class), new ToolActionPolicy(), documents(true));

        assertThat(loader.loadForAgent(agent.getId()).stream().map(LlmToolDefinition::name))
                .contains(KnowledgeSearchTool.NAME);
    }

    @Test
    void agentsWithoutDocumentsAreNotOfferedIt() {
        var loader = new AgentToolLoader(mock(AgentToolRepository.class), new ToolActionPolicy(), documents(false));

        assertThat(loader.loadForAgent(agent.getId()).stream().map(LlmToolDefinition::name))
                .doesNotContain(KnowledgeSearchTool.NAME);
    }

    @Test
    void returnsMatchingExcerptsAsReferenceMaterial() {
        var tool = new KnowledgeSearchTool(documents(true));

        var result = tool.execute(call, new LlmToolCall("1", KnowledgeSearchTool.NAME, Map.of("query", "cancellation fee")));

        assertThat(result.success()).isTrue();
        assertThat(result.result()).containsEntry("found", true)
                .containsEntry("excerpts", List.of("Cancellations within 24 hours cost KES 500."));
    }

    @Test
    void saysWhenNothingMatchesAndRejectsAnEmptyQuery() {
        var tool = new KnowledgeSearchTool(KnowledgeSearch.NONE);

        assertThat(tool.execute(call, new LlmToolCall("1", KnowledgeSearchTool.NAME, Map.of("query", "parking")))
                .result()).containsEntry("found", false);
        assertThat(tool.execute(call, new LlmToolCall("2", KnowledgeSearchTool.NAME, Map.of())).success()).isFalse();
    }

    private static KnowledgeSearch documents(boolean present) {
        return new KnowledgeSearch() {
            @Override public boolean hasDocuments(UUID agentId) { return present; }
            @Override public List<String> search(UUID tenantId, UUID agentId, String query) {
                return present ? List.of("Cancellations within 24 hours cost KES 500.") : List.of();
            }
        };
    }
}
