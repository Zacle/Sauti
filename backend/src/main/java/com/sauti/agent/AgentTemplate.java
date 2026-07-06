package com.sauti.agent;

import com.sauti.shared.Auditable;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "agent_templates")
public class AgentTemplate extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 80)
    private String category;

    @Column(nullable = false)
    private String greetingMessage;

    @Column(nullable = false)
    private String systemPrompt;

    @Column(nullable = false, length = 10)
    private String defaultLanguage;

    @Column(nullable = false)
    private String supportedLanguages;

    @Column(nullable = false)
    private String configurationJson = "{}";

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private boolean published;

    protected AgentTemplate() {
    }

    public AgentTemplate(Tenant tenant, AgentTemplateDtos.AgentTemplateRequest request) {
        this.id = UUID.randomUUID();
        this.tenant = tenant;
        apply(request);
    }

    public void update(AgentTemplateDtos.AgentTemplateRequest request) {
        apply(request);
        version++;
    }

    public boolean matches(AgentTemplateDtos.AgentTemplateRequest request) {
        String requestedConfiguration = request.configurationJson() == null || request.configurationJson().isBlank()
                ? "{}"
                : request.configurationJson();
        return name.equals(request.name().trim())
                && description.equals(request.description().trim())
                && category.equals(request.category().trim())
                && greetingMessage.equals(request.greetingMessage().trim())
                && systemPrompt.equals(request.systemPrompt().trim())
                && defaultLanguage.equals(request.defaultLanguage())
                && getSupportedLanguages().equals(request.supportedLanguages())
                && configurationJson.equals(requestedConfiguration)
                && published == request.published();
    }

    private void apply(AgentTemplateDtos.AgentTemplateRequest request) {
        this.name = request.name().trim();
        this.description = request.description().trim();
        this.category = request.category().trim();
        this.greetingMessage = request.greetingMessage().trim();
        this.systemPrompt = request.systemPrompt().trim();
        this.defaultLanguage = request.defaultLanguage();
        this.supportedLanguages = String.join(",", request.supportedLanguages());
        this.configurationJson = request.configurationJson() == null || request.configurationJson().isBlank()
                ? "{}"
                : request.configurationJson();
        this.published = request.published();
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getGreetingMessage() {
        return greetingMessage;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public List<String> getSupportedLanguages() {
        return Arrays.stream(supportedLanguages.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public String getConfigurationJson() {
        return configurationJson;
    }

    public int getVersion() {
        return version;
    }

    public boolean isPublished() {
        return published;
    }

    public boolean isSystemTemplate() {
        return tenant == null;
    }
}
