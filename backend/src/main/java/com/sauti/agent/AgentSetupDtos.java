package com.sauti.agent;

import com.sauti.agent.AgentDtos.AgentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The whole agent configuration saved in one request, so it is never left half-created. */
public final class AgentSetupDtos {
    private AgentSetupDtos() {
    }

    /**
     * @param agent      every agent setting
     * @param templateId a stored template whose variable definitions apply, or null
     * @param variables  variable definitions with their values (from a template, an AI brief or the owner)
     * @param values     values for variables the agent already has, by key; unknown keys are ignored
     */
    public record AgentSetupRequest(
            @NotNull @Valid AgentRequest agent,
            UUID templateId,
            @Size(max = 100) List<@Valid VariableDefinition> variables,
            Map<@NotBlank @Size(max = 100) String, @Size(max = 4000) String> values
    ) {
        public List<VariableDefinition> variablesOrEmpty() {
            return variables == null ? List.of() : variables;
        }

        public Map<String, String> valuesOrEmpty() {
            return values == null ? Map.of() : values;
        }
    }

    public record VariableDefinition(
            @NotBlank @Size(max = 100) String key,
            @Size(max = 200) String label,
            @Size(max = 1000) String description,
            @Size(max = 4000) String value,
            boolean required
    ) {
    }

    /** Unpublished edits waiting on the server. */
    public record DraftView(AgentSetupRequest setup, String savedBy, java.time.OffsetDateTime savedAt) {
    }

    /**
     * One entry in an agent's history; current marks the one live now.
     *
     * @param kind  publish (saved through setup or a restore) or edit (recorded after a direct edit)
     * @param parts what restoring it puts back besides settings and details, such as "tools"
     */
    public record VersionView(int version, String kind, String note, String publishedBy,
                              java.time.OffsetDateTime publishedAt, boolean current, List<String> parts) {
    }

    /** What the live assistant will be told, with anything that would make it say less than intended. */
    public record InstructionsPreview(
            String language,
            String greeting,
            String instructions,
            List<String> tools,
            List<PreviewWarning> warnings
    ) {
    }

    /** @param kind missing_required, empty_optional or unknown_placeholder */
    public record PreviewWarning(String kind, String key, String label, String message) {
    }
}
