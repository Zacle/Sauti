package com.sauti.tool;

import com.sauti.agent.Agent;
import com.sauti.shared.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "agent_tools")
public class AgentTool extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false, length = 64)
    private String toolName;

    @Column(nullable = false)
    private String toolDescription;

    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private Map<String, Object> parametersSchema = Map.of();

    @Column(nullable = false, length = 32)
    private String fulfillmentType;

    @Column(nullable = false, length = 32)
    private String actionEffect = ToolActionEffect.READ_ONLY.value();

    @Column(nullable = false, length = 32)
    private String confirmationPolicy = ToolConfirmationPolicy.NONE.value();

    private String webhookUrl;
    private String webhookMethod = "POST";
    private String authType = "none";
    private String authCredential;
    private String authHeaderName;
    private String calendarType;
    private UUID calendarCredentialId;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private int displayOrder;

    protected AgentTool() {
    }

    public AgentTool(
            Agent agent,
            String toolName,
            String toolDescription,
            Map<String, Object> parametersSchema,
            String fulfillmentType,
            boolean isActive,
            int displayOrder
    ) {
        this.id = UUID.randomUUID();
        this.agent = agent;
        this.toolName = toolName;
        this.toolDescription = toolDescription;
        this.parametersSchema = parametersSchema == null ? Map.of() : Map.copyOf(parametersSchema);
        this.fulfillmentType = fulfillmentType;
        this.isActive = isActive;
        this.displayOrder = displayOrder;
    }

    public void update(
            String toolName,
            String toolDescription,
            Map<String, Object> parametersSchema,
            String fulfillmentType,
            String webhookUrl,
            String webhookMethod,
            String authType,
            String authCredential,
            String authHeaderName,
            String calendarType,
            UUID calendarCredentialId,
            boolean active,
            int displayOrder
    ) {
        this.toolName = toolName;
        this.toolDescription = toolDescription;
        this.parametersSchema = parametersSchema == null ? Map.of() : Map.copyOf(parametersSchema);
        this.fulfillmentType = fulfillmentType;
        this.webhookUrl = webhookUrl;
        this.webhookMethod = webhookMethod == null || webhookMethod.isBlank() ? "POST" : webhookMethod;
        this.authType = authType == null || authType.isBlank() ? "none" : authType;
        if (authCredential != null) {
            this.authCredential = authCredential;
        }
        this.authHeaderName = authHeaderName;
        this.calendarType = calendarType;
        this.calendarCredentialId = calendarCredentialId;
        this.isActive = active;
        this.displayOrder = displayOrder;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void configureActionPolicy(ToolActionEffect effect, ToolConfirmationPolicy confirmation) {
        this.actionEffect = (effect == null ? ToolActionEffect.READ_ONLY : effect).value();
        this.confirmationPolicy = (confirmation == null ? ToolConfirmationPolicy.NONE : confirmation).value();
    }

    public void configureForDraft(boolean active, String calendarType) {
        this.isActive = active;
        if (calendarType != null) {
            this.calendarType = calendarType;
        }
    }

    public void connectCalendar(String calendarType, UUID calendarCredentialId) {
        this.calendarType = calendarType;
        this.calendarCredentialId = calendarCredentialId;
        this.isActive = true;
    }

    public void disconnectCalendar() {
        this.calendarType = "noop_calendar";
        this.calendarCredentialId = null;
        this.isActive = false;
    }

    public UUID getId() {
        return id;
    }

    public Agent getAgent() {
        return agent;
    }

    public String getToolName() {
        return toolName;
    }

    public String getToolDescription() {
        return toolDescription;
    }

    public Map<String, Object> getParametersSchema() {
        return parametersSchema;
    }

    public String getFulfillmentType() {
        return fulfillmentType;
    }

    public String getActionEffect() {
        return actionEffect;
    }

    public ToolActionEffect actionEffect() {
        return ToolActionEffect.from(actionEffect);
    }

    public String getConfirmationPolicy() {
        return confirmationPolicy;
    }

    public ToolConfirmationPolicy confirmationPolicy() {
        return ToolConfirmationPolicy.from(confirmationPolicy);
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getWebhookMethod() {
        return webhookMethod;
    }

    public String getAuthType() {
        return authType;
    }

    public String getAuthCredential() {
        return authCredential;
    }

    public String getAuthHeaderName() {
        return authHeaderName;
    }

    public String getCalendarType() {
        return calendarType;
    }

    public UUID getCalendarCredentialId() {
        return calendarCredentialId;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
