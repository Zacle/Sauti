package com.sauti.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentRepository;
import com.sauti.tool.AgentToolRepository;
import com.sauti.tool.CalendarCredentialRepository;
import com.sauti.tool.CredentialEncryption;
import com.sauti.tool.WebhookDestinationValidator;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationService.class);
    private final ObjectMapper objectMapper;
    private final CredentialEncryption encryption;
    private final IntegrationCatalog catalog;
    private final IntegrationConnectionRepository connections;
    private final AgentIntegrationRepository bindings;
    private final IntegrationDeliveryRepository deliveries;
    private final AgentRepository agents;
    private final AgentToolRepository agentTools;
    private final CalendarCredentialRepository calendarCredentials;
    private final WebhookDestinationValidator webhookValidator;

    public IntegrationService(ObjectMapper objectMapper, CredentialEncryption encryption,
                              IntegrationCatalog catalog, IntegrationConnectionRepository connections,
                              AgentIntegrationRepository bindings, IntegrationDeliveryRepository deliveries,
                              AgentRepository agents, AgentToolRepository agentTools,
                              CalendarCredentialRepository calendarCredentials,
                              WebhookDestinationValidator webhookValidator) {
        this.objectMapper = objectMapper;
        this.encryption = encryption;
        this.catalog = catalog;
        this.connections = connections;
        this.bindings = bindings;
        this.deliveries = deliveries;
        this.agents = agents;
        this.agentTools = agentTools;
        this.calendarCredentials = calendarCredentials;
        this.webhookValidator = webhookValidator;
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> listConnections(UUID tenantId) {
        return connections.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public String connectionProvider(UUID tenantId, UUID id) {
        return requireConnection(tenantId, id).getProvider();
    }

    @Transactional
    public ConnectionResponse create(UUID tenantId, ConnectionRequest request) {
        var entry = catalog.require(normalize(request.provider()));
        if (!entry.requiresConnection()) {
            throw new IllegalArgumentException(entry.name() + " does not require a customer connection");
        }
        var credentials = safe(request.credentials());
        validate(entry, safe(request.configuration()), credentials);
        var connection = new IntegrationConnection(
                tenantId, entry.provider(),
                blank(request.displayName()) ? entry.name() : request.displayName().trim(),
                encrypt(credentials), json(safe(request.configuration())));
        return response(connections.save(connection));
    }

    @Transactional
    public ConnectionResponse update(UUID tenantId, UUID id, ConnectionRequest request) {
        var connection = requireConnection(tenantId, id);
        var entry = catalog.require(connection.getProvider());
        var configuration = request.configuration() == null
                ? map(connection.getConfiguration()) : safe(request.configuration());
        var credentials = request.credentials() == null
                ? decrypt(connection) : safe(request.credentials());
        validate(entry, configuration, credentials);
        connection.update(request.displayName(),
                request.credentials() == null ? null : encrypt(credentials), json(configuration));
        return response(connections.save(connection));
    }

    @Transactional
    public void disconnect(UUID tenantId, UUID id) {
        var connection = requireConnection(tenantId, id);
        bindings.findAllByTenantIdAndConnectionId(tenantId, id).stream()
                .forEach(binding -> {
                    binding.disconnect();
                    if ("google_calendar".equals(connection.getProvider())) {
                        synchronizeGoogleCalendarTools(tenantId, binding.getAgentId(), false);
                    } else {
                        var affectedTools = toolNamesFor(connection.getProvider());
                        agentTools.findByAgent_IdOrderByDisplayOrderAsc(binding.getAgentId()).stream()
                                .filter(tool -> affectedTools.contains(tool.getToolName()))
                                .forEach(tool -> tool.configureForDraft(false, null));
                    }
                    if ("whatsapp".equals(connection.getProvider())) {
                        agents.findByIdAndTenantId(binding.getAgentId(), tenantId).ifPresent(agent -> {
                            agent.configureWhatsApp(false, null);
                            agents.save(agent);
                        });
                    }
                });
        connections.delete(connection);
    }

    @Transactional
    public ConnectionResponse test(UUID tenantId, UUID id) {
        return test(tenantId, id, () -> { });
    }

    @Transactional
    public ConnectionResponse test(UUID tenantId, UUID id, Runnable liveProbe) {
        var connection = requireConnection(tenantId, id);
        try {
            validate(catalog.require(connection.getProvider()), map(connection.getConfiguration()), decrypt(connection));
            liveProbe.run();
            connection.testSucceeded();
        } catch (RuntimeException exception) {
            connection.testFailed(exception.getMessage());
        }
        return response(connections.save(connection));
    }

    @Transactional(readOnly = true)
    public List<BindingResponse> listBindings(UUID tenantId, UUID agentId) {
        requireAgent(tenantId, agentId);
        var existing = bindings.findAllByTenantIdAndAgentIdOrderByProvider(tenantId, agentId);
        return catalog.all().stream().map(entry -> {
            var binding = existing.stream().filter(item -> item.getProvider().equals(entry.provider()))
                    .findFirst().orElse(null);
            return bindingResponse(entry, binding);
        }).toList();
    }

    @Transactional
    public BindingResponse configure(UUID tenantId, UUID agentId, BindingRequest request) {
        requireAgent(tenantId, agentId);
        var entry = catalog.require(normalize(request.provider()));
        IntegrationConnection connection = null;
        if (request.connectionId() != null) {
            connection = requireConnection(tenantId, request.connectionId());
            if (!connection.getProvider().equals(entry.provider())) {
                throw new IllegalArgumentException("Connection provider does not match integration provider");
            }
        }
        if (request.enabled() && entry.requiresConnection() && connection == null) {
            throw new IllegalArgumentException("A connection is required before enabling " + entry.name());
        }
        var bindingConfiguration = safe(request.configuration());
        if (request.enabled() && "google_sheets".equals(entry.provider())) {
            for (var field : List.of("spreadsheetId", "range")) {
                if (blank(String.valueOf(bindingConfiguration.get(field)))) {
                    throw new IllegalArgumentException(field + " is required");
                }
            }
        }
        var binding = bindings.findByTenantIdAndAgentIdAndProvider(tenantId, agentId, entry.provider())
                .orElseGet(() -> new AgentIntegration(tenantId, agentId, entry.provider()));
        binding.configure(request.enabled(), request.connectionId(), json(bindingConfiguration));
        var saved = bindings.save(binding);
        if ("whatsapp".equals(entry.provider())) {
            var phoneNumberId = connection == null ? "" :
                    String.valueOf(map(connection.getConfiguration()).getOrDefault("phoneNumberId", ""));
            agents.findByIdAndTenantId(agentId, tenantId).ifPresent(agent -> {
                agent.configureWhatsApp(request.enabled(), request.enabled() ? phoneNumberId : null);
                agents.save(agent);
            });
        }
        if ("google_calendar".equals(entry.provider())) {
            synchronizeGoogleCalendarTools(tenantId, agentId, request.enabled());
        } else {
            var toolNames = toolNamesFor(entry.provider());
            agentTools.findByAgent_IdOrderByDisplayOrderAsc(agentId).stream()
                    .filter(tool -> toolNames.contains(tool.getToolName()))
                    .forEach(tool -> tool.configureForDraft(request.enabled(), null));
        }
        return bindingResponse(entry, saved);
    }

    /** Repairs bindings created before workspace connections and tool links were synchronized. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileGoogleCalendarBindings() {
        bindings.findAllByProviderAndEnabledTrue("google_calendar").forEach(binding -> {
            try {
                var connection = binding.getConnectionId() == null ? null
                        : connections.findByIdAndTenantId(binding.getConnectionId(), binding.getTenantId()).orElse(null);
                if (connection != null && "connected".equals(connection.getStatus())) {
                    synchronizeGoogleCalendarTools(binding.getTenantId(), binding.getAgentId(), true);
                    return;
                }
                binding.disconnect();
                synchronizeGoogleCalendarTools(binding.getTenantId(), binding.getAgentId(), false);
            } catch (RuntimeException exception) {
                binding.disconnect();
                try {
                    synchronizeGoogleCalendarTools(binding.getTenantId(), binding.getAgentId(), false);
                } catch (RuntimeException cleanupException) {
                    LOGGER.debug("Unable to clear stale Google Calendar tools for agentId={}",
                            binding.getAgentId(), cleanupException);
                }
                LOGGER.warn("Disabled inconsistent Google Calendar binding for agentId={}: {}",
                        binding.getAgentId(), exception.getMessage());
            }
        });
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> listDeliveries(UUID tenantId) {
        return deliveries.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(DeliveryResponse::from).toList();
    }

    @Transactional
    public void connectOAuth(UUID tenantId, UUID agentId, String provider,
                             Map<String, Object> credentials, Map<String, Object> configuration) {
        var entry = catalog.require(provider);
        var existing = connections.findFirstByTenantIdAndProviderOrderByCreatedAtDesc(tenantId, provider)
                .orElse(null);
        var mergedCredentials = new LinkedHashMap<String, Object>();
        if (existing != null) mergedCredentials.putAll(decrypt(existing));
        credentials.forEach((key, value) -> {
            if (!"refreshToken".equals(key) || (value != null && !String.valueOf(value).isBlank())) {
                mergedCredentials.put(key, value);
            }
        });
        var storedConfiguration = existing != null && (configuration == null || configuration.isEmpty())
                ? existing.getConfiguration()
                : json(configuration == null ? Map.of() : configuration);
        var connection = existing == null
                ? new IntegrationConnection(tenantId, provider, entry.name(),
                        encrypt(mergedCredentials), storedConfiguration)
                : existing;
        connection.update(entry.name(), encrypt(mergedCredentials), storedConfiguration);
        connection = connections.save(connection);
        var binding = bindings.findByTenantIdAndAgentIdAndProvider(tenantId, agentId, provider)
                .orElseGet(() -> new AgentIntegration(tenantId, agentId, provider));
        var existingBindingConfiguration = binding.getConfiguration() == null ? "{}" : binding.getConfiguration();
        var bindingValues = map(existingBindingConfiguration);
        var sheetsConfigured = !blank(String.valueOf(bindingValues.get("spreadsheetId")))
                && !blank(String.valueOf(bindingValues.get("range")));
        binding.configure(!"google_sheets".equals(provider) || sheetsConfigured,
                connection.getId(), existingBindingConfiguration);
        bindings.save(binding);
    }

    @Transactional
    public void updateOAuthCredentials(UUID tenantId, UUID connectionId, Map<String, Object> credentials) {
        var connection = requireConnection(tenantId, connectionId);
        var merged = new LinkedHashMap<>(decrypt(connection));
        credentials.forEach((key, value) -> {
            if (!"refreshToken".equals(key) || (value != null && !String.valueOf(value).isBlank())) {
                merged.put(key, value);
            }
        });
        connection.update(null, encrypt(merged), null);
        connections.save(connection);
    }

    Map<String, Object> credentials(IntegrationConnection connection) { return decrypt(connection); }

    @Transactional(readOnly = true)
    public RuntimeConfiguration runtime(UUID tenantId, UUID agentId, String provider) {
        var binding = bindings.findByTenantIdAndAgentIdAndProvider(tenantId, agentId, provider)
                .filter(AgentIntegration::isEnabled)
                .orElseThrow(() -> new IllegalStateException(provider + " is not enabled for this agent"));
        var connection = binding.getConnectionId() == null ? null
                : requireConnection(tenantId, binding.getConnectionId());
        if (connection == null) throw new IllegalStateException(provider + " is not connected");
        var configuration = new LinkedHashMap<>(map(connection.getConfiguration()));
        configuration.putAll(map(binding.getConfiguration()));
        return new RuntimeConfiguration(connection.getId(), configuration, decrypt(connection));
    }

    private BindingResponse bindingResponse(IntegrationCatalog.Entry entry, AgentIntegration binding) {
        IntegrationConnection connection = binding == null || binding.getConnectionId() == null ? null
                : connections.findByIdAndTenantId(binding.getConnectionId(), binding.getTenantId()).orElse(null);
        var last = binding == null ? null
                : deliveries.findFirstByAgentIntegrationIdOrderByCreatedAtDesc(binding.getId()).orElse(null);
        return new BindingResponse(entry.provider(), entry.name(), binding != null && binding.isEnabled(),
                binding == null ? null : binding.getConnectionId(),
                connection == null ? (entry.requiresConnection() ? "not_connected" : "built_in") : connection.getStatus(),
                binding == null ? Map.of() : map(binding.getConfiguration()),
                last == null ? null : DeliveryResponse.from(last));
    }

    private ConnectionResponse response(IntegrationConnection connection) {
        return new ConnectionResponse(connection.getId(), connection.getProvider(), connection.getDisplayName(),
                connection.getStatus(), connection.getEncryptedCredentials() != null
                        && !connection.getEncryptedCredentials().isBlank(),
                map(connection.getConfiguration()), connection.getExternalAccountId(),
                connection.getLastTestedAt(), connection.getLastError(), connection.getCreatedAt());
    }

    private List<String> toolNamesFor(String provider) {
        return switch (provider) {
            case "whatsapp" -> List.of("send_whatsapp_message");
            case "google_calendar" -> List.of("check_availability", "book_slot", "reschedule_booking", "cancel_booking");
            case "google_sheets" -> List.of("lookup_google_sheet_row", "update_google_sheet_row");
            case "mpesa" -> List.of("request_mpesa_payment", "check_mpesa_payment");
            case "custom_webhook" -> List.of("call_custom_webhook");
            default -> List.of();
        };
    }

    private void synchronizeGoogleCalendarTools(UUID tenantId, UUID agentId, boolean enabled) {
        var agent = agents.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
        var tools = agentTools.findByAgent_IdOrderByDisplayOrderAsc(agentId).stream()
                .filter(tool -> toolNamesFor("google_calendar").contains(tool.getToolName()))
                .toList();
        if (!enabled) {
            tools.forEach(com.sauti.tool.AgentTool::disconnectCalendar);
            if ("Google Calendar".equals(agent.getCalendarProvider())) {
                agent.updateCalendarProvider("Set up later");
                agents.save(agent);
            }
            return;
        }
        var credential = calendarCredentials
                .findAllByTenant_IdAndProviderOrderByCreatedAtDesc(tenantId, "google")
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Reconnect Google Calendar before enabling this agent"));
        tools.forEach(tool -> tool.connectCalendar("google", credential.getId()));
        agent.updateCalendarProvider("Google Calendar");
        agents.save(agent);
    }

    private void validate(IntegrationCatalog.Entry entry, Map<String, Object> configuration,
                          Map<String, Object> credentials) {
        for (var field : entry.configurationFields()) {
            if (entry.provider().equals("custom_webhook") && field.equals("authType")) continue;
            if (entry.provider().equals("google_sheets")) continue;
            if (!configuration.containsKey(field) || blank(String.valueOf(configuration.get(field)))) {
                throw new IllegalArgumentException(field + " is required");
            }
        }
        for (var field : entry.credentialFields()) {
            if (entry.provider().equals("custom_webhook")) continue;
            if (!credentials.containsKey(field) || blank(String.valueOf(credentials.get(field)))) {
                throw new IllegalArgumentException(field + " is required");
            }
        }
        if (entry.provider().equals("custom_webhook")) {
            webhookValidator.validateHttpsPublicUrl(String.valueOf(configuration.get("webhookUrl")));
        }
        if (entry.provider().equals("slack")) {
            var url = String.valueOf(credentials.get("webhookUrl"));
            webhookValidator.validateHttpsPublicUrl(url);
            if (!url.startsWith("https://hooks.slack.com/")) {
                throw new IllegalArgumentException("Slack incoming webhook must use hooks.slack.com");
            }
        }
        if (entry.provider().equals("mpesa")) {
            var environment = String.valueOf(configuration.get("environment"));
            if (!List.of("sandbox", "production").contains(environment)) {
                throw new IllegalArgumentException("environment must be sandbox or production");
            }
        }
    }

    private IntegrationConnection requireConnection(UUID tenantId, UUID id) {
        return connections.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Integration connection not found"));
    }

    private void requireAgent(UUID tenantId, UUID agentId) {
        agents.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
    }

    private String encrypt(Map<String, Object> credentials) {
        return credentials.isEmpty() ? null : encryption.encrypt(json(credentials));
    }

    private Map<String, Object> decrypt(IntegrationConnection connection) {
        if (connection.getEncryptedCredentials() == null || connection.getEncryptedCredentials().isBlank()) {
            return Map.of();
        }
        return map(encryption.decrypt(connection.getEncryptedCredentials()));
    }

    private String json(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("Integration configuration is invalid", exception); }
    }

    private Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {}); }
        catch (Exception exception) { throw new IllegalStateException("Stored integration configuration is invalid", exception); }
    }

    private static Map<String, Object> safe(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }
    private static String normalize(String value) {
        if (blank(value)) throw new IllegalArgumentException("provider is required");
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
    private static boolean blank(String value) { return value == null || value.isBlank() || "null".equals(value); }

    public record ConnectionRequest(String provider, String displayName,
                                    Map<String, Object> configuration, Map<String, Object> credentials) {}
    public record ConnectionResponse(UUID id, String provider, String displayName, String status,
                                     boolean credentialConfigured, Map<String, Object> configuration,
                                     String externalAccountId, OffsetDateTime lastTestedAt,
                                     String lastError, OffsetDateTime createdAt) {}
    public record BindingRequest(String provider, boolean enabled, UUID connectionId,
                                 Map<String, Object> configuration) {}
    public record BindingResponse(String provider, String name, boolean enabled, UUID connectionId,
                                  String connectionStatus, Map<String, Object> configuration,
                                  DeliveryResponse lastDelivery) {}
    public record DeliveryResponse(UUID id, UUID callId, String provider, String status, int attempts,
                                   Integer responseCode, String lastError, OffsetDateTime deliveredAt,
                                   OffsetDateTime createdAt) {
        static DeliveryResponse from(IntegrationDelivery delivery) {
            return new DeliveryResponse(delivery.getId(), delivery.getCallId(), delivery.getProvider(),
                    delivery.getStatus(), delivery.getAttempts(), delivery.getResponseCode(),
                    delivery.getLastError(), delivery.getDeliveredAt(), delivery.getCreatedAt());
        }
    }
    public record RuntimeConfiguration(UUID connectionId, Map<String, Object> configuration,
                                       Map<String, Object> credentials) {}
}
