package com.sauti.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.sauti.agent.AgentVariableDtos.AgentVariableResponse;
import com.sauti.agent.AgentVariableDtos.AgentVariableValue;
import com.sauti.phone.InternationalPhoneNumberService;
import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentVariableService {
    private static final Set<String> SERVICE_CATALOG_KEYS = Set.of(
            "services", "services_and_prices", "treatments", "bookable_services",
            "classes", "memberships", "packages", "products_and_plans",
            "service_categories", "service_types", "session_types", "subjects_and_levels",
            "supported_products", "veterinary_services", "coverage_types", "dining_options",
            "delivery_modes", "practice_areas", "staff", "dentists", "accepted_insurance",
            "maintenance_categories", "authorized_offers", "trial_offer"
    );
    private static final Set<String> INTERNAL_OPERATION_KEYS = Set.of(
            "greeting_style", "tone", "transfer_rules", "transfer_number",
            "after_hours_behavior", "escalation_triggers", "transfer_retry_policy",
            "prohibited_statements", "account_executive_routing", "agent_routing_rules",
            "delivery_escalation_rules", "dispatch_rules", "hazard_escalation_rules",
            "incident_priority_rules", "landlord_routing_rules", "lead_handoff_policy",
            "licensed_agent_routing", "priority_rules", "property_emergency_rules",
            "security_incident_rules", "tenant_emergency_rules", "veterinary_emergency_rules",
            "vehicle_safety_rules", "dental_urgency_policy", "emergency_instruction",
            "safeguarding_rules", "support_playbooks", "technical_playbooks",
            "out_of_scope_policy", "support_boundaries", "checkout_boundaries",
            "sales_claim_boundaries", "account_verification_fields", "cart_lookup_fields",
            "demo_qualification_fields", "environment_fields", "lead_fields",
            "lead_qualification_fields", "message_fields", "order_lookup_fields",
            "qualification_fields", "quote_intake_fields", "ticket_fields"
    );
    private static final Pattern CATALOG_SEPARATOR = Pattern.compile("\\r?\\n|;|,(?!\\d{3}(?!\\d))");
    private static final Pattern VARIABLE_KEY = Pattern.compile("[a-z][a-z0-9_]{0,99}");
    private static final Set<String> SYSTEM_MANAGED_VARIABLES = Set.of(
            "agent_name", "timezone", "business_timezone", "calendar_system",
            "calendar_provider", "routing_policy", "required_booking_fields",
            "notification_channels"
    );
    /** Filled from the agent's own settings at call time, so a prompt may use them without a variable. */
    private static final Set<String> AGENT_SETTING_PLACEHOLDERS = Set.of("business_hours", "after_hours_behavior");

    private final AgentVariableRepository variableRepository;
    private final AgentRepository agentRepository;
    private final InternationalPhoneNumberService phoneNumbers;
    private final ApplicationEventPublisher events;
    private final BusinessFacts businessFacts;

    @Autowired
    public AgentVariableService(AgentVariableRepository variableRepository, AgentRepository agentRepository,
                                InternationalPhoneNumberService phoneNumbers, ApplicationEventPublisher events,
                                BusinessFacts businessFacts) {
        this.variableRepository = variableRepository;
        this.agentRepository = agentRepository;
        this.phoneNumbers = phoneNumbers;
        this.events = events;
        this.businessFacts = businessFacts;
    }

    AgentVariableService(AgentVariableRepository variableRepository, AgentRepository agentRepository,
                         InternationalPhoneNumberService phoneNumbers, BusinessFacts businessFacts) {
        this(variableRepository, agentRepository, phoneNumbers, event -> { }, businessFacts);
    }

    AgentVariableService(AgentVariableRepository variableRepository, AgentRepository agentRepository,
                         InternationalPhoneNumberService phoneNumbers) {
        this(variableRepository, agentRepository, phoneNumbers, event -> { }, BusinessFacts.NONE);
    }

    @Transactional(readOnly = true)
    public List<AgentVariableResponse> list(UUID tenantId, UUID agentId) {
        var agent = requireOwnedAgent(tenantId, agentId);
        var workspace = workspaceFacts(agent);
        return variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agentId)
                .stream()
                .filter(variable -> !SYSTEM_MANAGED_VARIABLES.contains(variable.getKey()))
                .map(variable -> AgentVariableResponse.from(variable, workspace.get(variable.getKey())))
                .toList();
    }

    /** Workspace business-profile facts for this agent's workspace; empty when unavailable. */
    private Map<String, String> workspaceFacts(Agent agent) {
        if (agent.getTenant() == null || agent.getTenant().getId() == null) return Map.of();
        var facts = businessFacts.factsFor(agent.getTenant().getId());
        return facts == null ? Map.of() : facts;
    }

    /** Filled by the agent itself, or by the workspace profile when the agent leaves it empty. */
    private static boolean covered(AgentVariable variable, Map<String, String> workspace) {
        return variable.isFilled() || workspace.containsKey(variable.getKey());
    }

    @Transactional
    public List<AgentVariableResponse> updateAll(
            UUID tenantId,
            UUID agentId,
            List<AgentVariableValue> values
    ) {
        var agent = requireOwnedAgent(tenantId, agentId);
        var variables = variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agentId);
        var byKey = new LinkedHashMap<String, AgentVariable>();
        variables.forEach(variable -> byKey.put(variable.getKey(), variable));
        for (var value : values) {
            requireOwnerManaged(value.key());
            var variable = byKey.get(value.key());
            if (variable == null) {
                throw new IllegalArgumentException("Unknown agent variable: " + value.key());
            }
            var normalizedValue = normalizeValue(agent, value.key(), value.value());
            variable.updateValue(normalizedValue);
            syncStructuredSetting(agent, value.key(), normalizedValue);
        }
        changed(agent, "Changed business details");
        return variables.stream().map(AgentVariableResponse::from).toList();
    }

    /** Sets values for variables the agent has; keys it doesn't have, or that are automatic, are skipped. */
    @Transactional
    public void updateKnown(UUID tenantId, UUID agentId, Map<String, String> values) {
        if (values.isEmpty()) return;
        var agent = requireOwnedAgent(tenantId, agentId);
        var byKey = new LinkedHashMap<String, AgentVariable>();
        variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agentId)
                .forEach(variable -> byKey.put(variable.getKey(), variable));
        values.forEach((key, value) -> {
            var variable = byKey.get(key);
            if (variable == null || SYSTEM_MANAGED_VARIABLES.contains(key)) return;
            var normalizedValue = normalizeValue(agent, key, value);
            variable.updateValue(normalizedValue);
            syncStructuredSetting(agent, key, normalizedValue);
        });
        changed(agent);
    }

    /**
     * Placeholders that will say less than intended on a call: a required value still missing, an
     * optional one left empty (its sentence is dropped), or a placeholder with no variable at all.
     */
    @Transactional(readOnly = true)
    public List<AgentSetupDtos.PreviewWarning> placeholderWarnings(Agent agent) {
        var byKey = new LinkedHashMap<String, AgentVariable>();
        variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId())
                .forEach(variable -> byKey.put(variable.getKey(), variable));
        var workspace = workspaceFacts(agent);
        var warnings = new java.util.ArrayList<AgentSetupDtos.PreviewWarning>();
        var used = PromptTemplate.placeholders(agent.getSystemPrompt(), agent.getGreetingMessage());
        for (var key : used) {
            if (SYSTEM_MANAGED_VARIABLES.contains(key) || AGENT_SETTING_PLACEHOLDERS.contains(key)) continue;
            if (workspace.containsKey(key) && (byKey.get(key) == null || !byKey.get(key).isFilled())) continue;
            var variable = byKey.get(key);
            if (variable == null) {
                warnings.add(new AgentSetupDtos.PreviewWarning("unknown_placeholder", key, humanize(key),
                        "{{" + key + "}} has no business detail behind it, so the sentence using it is left out."));
            } else if (!variable.isFilled() && variable.isRequired()) {
                warnings.add(missingRequired(variable));
            } else if (!variable.isFilled()) {
                warnings.add(new AgentSetupDtos.PreviewWarning("empty_optional", key, variable.getDisplayLabel(),
                        variable.getDisplayLabel() + " is empty, so the sentence using it is left out."));
            }
        }
        byKey.values().stream()
                .filter(variable -> !used.contains(variable.getKey()))
                .filter(variable -> !SYSTEM_MANAGED_VARIABLES.contains(variable.getKey()))
                .filter(variable -> variable.isRequired() && !covered(variable, workspace))
                .forEach(variable -> warnings.add(missingRequired(variable)));
        return List.copyOf(warnings);
    }

    private static AgentSetupDtos.PreviewWarning missingRequired(AgentVariable variable) {
        return new AgentSetupDtos.PreviewWarning("missing_required", variable.getKey(), variable.getDisplayLabel(),
                variable.getDisplayLabel() + " is required before this agent can go live.");
    }

    @Transactional
    public AgentVariableResponse updateOne(UUID tenantId, UUID agentId, String key, String value) {
        var agent = requireOwnedAgent(tenantId, agentId);
        requireOwnerManaged(key);
        var variable = variableRepository.findByAgentIdAndKey(agentId, key)
                .orElseThrow(() -> new EntityNotFoundException("Agent variable not found"));
        var normalizedValue = normalizeValue(agent, key, value);
        variable.updateValue(normalizedValue);
        syncStructuredSetting(agent, key, normalizedValue);
        changed(agent, "Changed " + variable.getDisplayLabel());
        return AgentVariableResponse.from(variable);
    }

    @Transactional
    public void updateIfPresent(UUID agentId, String key, String value) {
        variableRepository.findByAgentIdAndKey(agentId, key).ifPresent(variable -> {
            var normalizedValue = normalizeValue(variable.getAgent(), key, value);
            variable.updateValue(normalizedValue);
            syncStructuredSetting(variable.getAgent(), key, normalizedValue);
            changed(variable.getAgent());
        });
    }

    /**
     * Defines a variable, or redefines one that already exists. Saving an agent already creates a
     * variable for each placeholder in its prompt, so a client that then sends the full definition
     * (label, help text, required) refines it instead of failing.
     */
    @Transactional
    public AgentVariableResponse create(
            UUID tenantId,
            UUID agentId,
            String key,
            String label,
            String description,
            String value,
            boolean required
    ) {
        return create(tenantId, agentId, key, label, description, value, required, null);
    }

    /** As {@link #create}, recording the edit in the agent version history under {@code change}. */
    @Transactional
    public AgentVariableResponse create(
            UUID tenantId,
            UUID agentId,
            String key,
            String label,
            String description,
            String value,
            boolean required,
            String change
    ) {
        var agent = requireOwnedAgent(tenantId, agentId);
        var normalizedKey = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        if (!VARIABLE_KEY.matcher(normalizedKey).matches()) {
            throw new IllegalArgumentException(
                    "Variable keys must start with a letter and contain only lowercase letters, numbers, and underscores"
            );
        }
        if (SYSTEM_MANAGED_VARIABLES.contains(normalizedKey)) {
            throw new IllegalArgumentException(normalizedKey + " is an automatic variable and cannot be redefined");
        }
        var cleanDescription = description == null || description.isBlank() ? null : description.trim();
        var cleanLabel = label == null || label.isBlank() ? humanize(normalizedKey) : label.trim();
        var variable = variableRepository.findByAgentIdAndKey(agentId, normalizedKey)
                .map(existing -> {
                    existing.redefine(cleanLabel, cleanDescription, required);
                    return existing;
                })
                .orElseGet(() -> new AgentVariable(agent, normalizedKey, cleanLabel, cleanDescription, required));
        if (value != null && !value.isBlank()) {
            variable.updateValue(normalizeValue(agent, normalizedKey, value));
        }
        var saved = variableRepository.save(variable);
        changed(agent, change == null ? null : change + " " + saved.getDisplayLabel());
        return AgentVariableResponse.from(saved);
    }

    /** Applies a template's variable definitions; a template's wording replaces an automatic definition. */
    @Transactional
    public void seedDefinitions(Agent agent, JsonNode configuration) {
        var definitions = configuration.path("variables");
        if (!definitions.isArray()) {
            return;
        }
        for (var definition : definitions) {
            var key = definition.path("key").asText("").trim();
            if (key.isBlank() || SYSTEM_MANAGED_VARIABLES.contains(key)) {
                continue;
            }
            var label = definition.path("label").asText(key).trim();
            var description = definition.path("description").asText("").trim();
            var cleanLabel = label.isBlank() ? key : label;
            var cleanDescription = description.isBlank() ? null : description;
            var required = definition.path("required").asBoolean(false);
            var existing = variableRepository.findByAgentIdAndKey(agent.getId(), key);
            if (existing.isPresent()) {
                if (!existing.get().isFilled()) existing.get().redefine(cleanLabel, cleanDescription, required);
                continue;
            }
            variableRepository.save(new AgentVariable(agent, key, cleanLabel, cleanDescription, required));
        }
        changed(agent);
    }

    /**
     * Makes sure every placeholder the agent's prompt or greeting uses has a variable to fill it.
     * A new one is required, so the agent cannot go live while the prompt would say nothing there.
     * Returns the keys that were added.
     */
    @Transactional
    public List<String> ensureDefinitionsFor(Agent agent, String... texts) {
        var added = new java.util.ArrayList<String>();
        for (var key : PromptTemplate.placeholders(texts)) {
            if (SYSTEM_MANAGED_VARIABLES.contains(key) || AGENT_SETTING_PLACEHOLDERS.contains(key)) continue;
            if (variableRepository.findByAgentIdAndKey(agent.getId(), key).isPresent()) continue;
            variableRepository.save(new AgentVariable(agent, key, humanize(key), null, true));
            added.add(key);
        }
        if (!added.isEmpty()) changed(agent);
        return added;
    }

    /** Rejects a prompt or greeting containing brace expressions that would never be filled. */
    public static void requireWellFormedPlaceholders(String... texts) {
        var malformed = PromptTemplate.malformed(texts);
        if (!malformed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Placeholders use lowercase letters, numbers and underscores, like {{business_name}}. Fix: "
                            + String.join(", ", malformed)
            );
        }
    }

    private static String humanize(String key) {
        var words = key.replace('_', ' ').trim();
        return words.isEmpty() ? key : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private void changed(Agent agent) {
        changed(agent, null);
    }

    private void changed(Agent agent, String change) {
        if (agent.getId() == null || agent.getTenant() == null || agent.getTenant().getId() == null) return;
        events.publishEvent(new AgentConfigurationChanged(agent.getTenant().getId(), agent.getId(), change));
    }

    @Transactional(readOnly = true)
    public List<String> missingRequired(UUID agentId) {
        var workspace = agentRepository.findById(agentId).map(this::workspaceFacts).orElse(Map.of());
        return variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agentId)
                .stream()
                .filter(variable -> !SYSTEM_MANAGED_VARIABLES.contains(variable.getKey()))
                .filter(AgentVariable::isRequired)
                .filter(variable -> !covered(variable, workspace))
                .map(AgentVariable::getDisplayLabel)
                .toList();
    }

    @Transactional(readOnly = true)
    public String resolvePrompt(Agent agent, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "";
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("agent_name", agent.getName());
        values.put("timezone", agent.getTimezone());
        // Workspace profile first, so an agent's own filled value replaces it.
        values.putAll(workspaceFacts(agent));
        variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()).stream()
                .filter(AgentVariable::isFilled)
                .forEach(variable -> values.put(variable.getKey(), variable.getValue()));
        values.put("business_timezone", agent.getTimezone());
        values.put("calendar_system", agent.getCalendarProvider() == null || agent.getCalendarProvider().isBlank()
                ? "the enabled calendar integration"
                : agent.getCalendarProvider());
        // Agent fields drive runtime behavior and must override stale onboarding variables.
        if (agent.getCalendarProvider() != null && !agent.getCalendarProvider().isBlank()) {
            values.put("calendar_provider", agent.getCalendarProvider());
        }
        if (agent.getRoutingPolicy() != null && !agent.getRoutingPolicy().isBlank()) {
            values.put("routing_policy", agent.getRoutingPolicy());
        }
        values.put("required_booking_fields", String.join(", ", agent.getBookingRequiredFields()));
        values.put("notification_channels", String.join(", ", agent.getBookingNotificationChannels()));
        values.put("business_hours", OperatingHoursSchedule.describe(OperatingHoursSchedule.effective(agent)));
        values.put("after_hours_behavior", describeAfterHoursBehavior(agent.getAfterHoursBehavior()));
        return PromptTemplate.render(prompt, values::get);
    }

    @Transactional(readOnly = true)
    public String businessName(Agent agent) {
        return variableRepository.findByAgentIdAndKey(agent.getId(), "business_name")
                .filter(AgentVariable::isFilled)
                .map(AgentVariable::getValue)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .or(() -> java.util.Optional.ofNullable(workspaceFacts(agent).get("business_name")))
                .orElseGet(() -> AgentBusinessIdentity.fromPrompt(agent));
    }

    /**
     * Supplies every filled owner-managed fact to the conversation, even when a
     * custom prompt omitted the corresponding placeholder. Required and
     * optional template facts therefore share the same authoritative path.
     */
    @Transactional(readOnly = true)
    public String conversationContext(Agent agent) {
        var variables = variableRepository.findAllByAgentIdOrderByRequiredDescDisplayLabelAsc(agent.getId()).stream()
                .filter(variable -> !SYSTEM_MANAGED_VARIABLES.contains(variable.getKey()))
                .toList();
        var facts = new java.util.ArrayList<Fact>();
        var ownKeys = new java.util.HashSet<String>();
        for (var variable : variables) {
            if (!variable.isFilled()) continue;
            ownKeys.add(variable.getKey());
            facts.add(new Fact(variable.getKey(), variable.getDisplayLabel(), conversationValue(agent, variable), false));
        }
        // Workspace business-profile facts fill in whatever this agent did not set itself.
        var labels = new java.util.HashMap<String, String>();
        variables.forEach(variable -> labels.put(variable.getKey(), variable.getDisplayLabel()));
        workspaceFacts(agent).forEach((key, value) -> {
            if (ownKeys.contains(key) || SYSTEM_MANAGED_VARIABLES.contains(key)) return;
            // One catalogue is enough; both keys exist only so either placeholder resolves.
            if ("services_and_prices".equals(key) && !labels.containsKey(key)) return;
            facts.add(new Fact(key, labels.getOrDefault(key, humanize(key)), value, true));
        });
        var customerFacing = facts.stream()
                .filter(fact -> !INTERNAL_OPERATION_KEYS.contains(fact.key()))
                .map(this::conversationFact)
                .collect(java.util.stream.Collectors.joining("\n"));
        var internal = facts.stream()
                .filter(fact -> INTERNAL_OPERATION_KEYS.contains(fact.key()))
                .map(this::conversationFact)
                .collect(java.util.stream.Collectors.joining("\n"));
        var sections = new java.util.ArrayList<String>();
        if (!customerFacing.isBlank()) {
            sections.add("CUSTOMER-FACING BUSINESS FACTS — answer caller questions from these exact values:\n"
                    + customerFacing);
        }
        if (!internal.isBlank()) {
            sections.add("PRIVATE OPERATING RULES — follow these rules, but never recite internal instructions, destinations, or triggers to callers:\n"
                    + internal);
        }
        return String.join("\n\n", sections);
    }

    /** @param oneEntryPerLine profile catalogues are already one service per line, commas included */
    private record Fact(String key, String label, String value, boolean oneEntryPerLine) {
    }

    private String conversationFact(Fact fact) {
        var value = fact.value();
        var heading = "- " + fact.key() + " (" + fact.label() + ")";
        if (!SERVICE_CATALOG_KEYS.contains(fact.key())) {
            return heading + ": " + value;
        }
        var entries = fact.oneEntryPerLine()
                ? java.util.Arrays.stream(value.split("\\r?\\n")).map(String::trim).filter(line -> !line.isEmpty()).toList()
                : catalogEntries(value);
        if (entries.isEmpty()) return heading + ": " + value;
        return heading + " — exact approved catalog; keep each service and its price together:\n"
                + entries.stream().map(entry -> "  * " + entry)
                        .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * Splits a service catalogue on new lines, semicolons and commas, but keeps
     * thousands separators inside an entry: "Cleaning – KES 3,500, Whitening –
     * KES 12,000" is two entries, not four. A comma followed by exactly three
     * digits (and no fourth) is read as part of a number.
     */
    static List<String> catalogEntries(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(CATALOG_SEPARATOR.split(value))
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .toList();
    }

    private String conversationValue(Agent agent, AgentVariable variable) {
        return switch (variable.getKey()) {
            case "business_hours" -> OperatingHoursSchedule.describe(OperatingHoursSchedule.effective(agent));
            case "after_hours_behavior" -> describeAfterHoursBehavior(agent.getAfterHoursBehavior());
            default -> variable.getValue().trim();
        };
    }

    private String normalizeValue(Agent agent, String key, String value) {
        if (value == null || value.isBlank() || !isPhoneNumberKey(key)) {
            return value;
        }
        return phoneNumbers.normalize(value, agent.getTenant().getCountryCode());
    }

    private static boolean isPhoneNumberKey(String key) {
        return key != null && (key.contains("phone") || "transfer_number".equals(key));
    }

    private Agent requireOwnedAgent(UUID tenantId, UUID agentId) {
        return agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
    }

    private void requireOwnerManaged(String key) {
        if (SYSTEM_MANAGED_VARIABLES.contains(key)) {
            throw new IllegalArgumentException(key + " is managed by the agent or its integrations");
        }
    }

    private void syncStructuredSetting(Agent agent, String key, String value) {
        if ("business_hours".equals(key)) {
            OperatingHoursSchedule.validate(value);
            agent.configureAvailability(value, agent.getAfterHoursBehavior(), agent.getAfterHoursMessage());
        }
        if ("after_hours_behavior".equals(key)) {
            if (!Set.of("answer", "take_message", "closed").contains(value)) {
                throw new IllegalArgumentException("Choose a supported after-hours behavior");
            }
            agent.configureAvailability(agent.getOperatingHours(), value, agent.getAfterHoursMessage());
        }
        if ("calendar_provider".equals(key)) {
            var allowed = List.of("Google Calendar", "Calendly", "Custom webhook", "Set up later");
            if (!allowed.contains(value)) {
                throw new IllegalArgumentException("Choose a supported calendar destination");
            }
            agent.updateCalendarProvider(value);
        }
        if ("routing_policy".equals(key)) {
            var allowed = List.of("Fixed calendar", "Set up later");
            if (!allowed.contains(value)) {
                throw new IllegalArgumentException("Choose a supported meeting routing option");
            }
            agent.updateRoutingPolicy(value);
        }
    }

    private String describeAfterHoursBehavior(String behavior) {
        return switch (behavior == null ? "answer" : behavior) {
            case "take_message" -> "Collect the caller's name, contact details, and reason for calling for follow-up.";
            case "closed" -> "Explain that the business is closed, provide the configured message, and end the call politely.";
            default -> "Answer normally with the agent's enabled capabilities.";
        };
    }
}
