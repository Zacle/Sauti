package com.sauti.agent;

import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "agent_variables",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_agent_variables_agent_key",
                columnNames = {"agent_id", "var_key"}
        )
)
public class AgentVariable extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "var_key", nullable = false, length = 100)
    private String key;

    @Column(name = "var_value", nullable = false, columnDefinition = "TEXT")
    private String value = "";

    @Column(name = "display_label", nullable = false, length = 200)
    private String displayLabel;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean required;

    protected AgentVariable() {
    }

    public AgentVariable(Agent agent, String key, String displayLabel, String description, boolean required) {
        this.id = UUID.randomUUID();
        this.agent = agent;
        this.key = key;
        this.displayLabel = displayLabel;
        this.description = description;
        this.required = required;
    }

    public void updateValue(String value) {
        this.value = value == null ? "" : value.trim();
    }

    public UUID getId() { return id; }
    public Agent getAgent() { return agent; }
    public String getKey() { return key; }
    public String getValue() { return value; }
    public String getDisplayLabel() { return displayLabel; }
    public String getDescription() { return description; }
    public boolean isRequired() { return required; }
    public boolean isFilled() { return value != null && !value.isBlank(); }
}
