package com.sauti.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AgentVariableDtos {
    private AgentVariableDtos() {
    }

    public record AgentVariableResponse(
            String key,
            String label,
            String description,
            String value,
            boolean required,
            boolean filled
    ) {
        static AgentVariableResponse from(AgentVariable variable) {
            return new AgentVariableResponse(
                    variable.getKey(),
                    variable.getDisplayLabel(),
                    variable.getDescription(),
                    variable.getValue(),
                    variable.isRequired(),
                    variable.isFilled()
            );
        }
    }

    public record AgentVariableValue(
            @NotBlank @Size(max = 100) String key,
            @Size(max = 4000) String value
    ) {
    }

    public record BulkAgentVariableRequest(
            @NotEmpty List<@Valid AgentVariableValue> variables
    ) {
    }

    public record PatchAgentVariableRequest(
            @Size(max = 4000) String value
    ) {
    }

    public record CreateAgentVariableRequest(
            @NotBlank @Size(max = 100) String key,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 500) String description,
            @Size(max = 4000) String value,
            boolean required
    ) {
    }
}
