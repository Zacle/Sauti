package com.sauti.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sauti.agent.AgentTemplateDtos.AgentTemplateRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SystemAgentTemplateSeeder implements ApplicationRunner {
    private static final Pattern TEMPLATE_SECTION = Pattern.compile(
            "(?ms)^## Template\\s+\\d+\\s+[—-]\\s+(.+?)\\R(.*?)(?=^---\\s*$|\\z)"
    );
    private static final Pattern PROMPT = Pattern.compile(
            "(?ms)### System Prompt\\s*\\R```\\s*\\R(.*?)\\R```"
    );
    private static final Set<String> REQUIRED_VARIABLES = Set.of(
            "business_name", "business_description", "business_address", "business_hours",
            "business_timezone", "business_phone", "supported_languages", "fallback_language",
            "after_hours_behavior", "required_booking_fields", "notification_channels"
    );
    private static final Set<String> CORE_VARIABLES = Set.of(
            "business_name", "business_description", "business_address", "business_hours",
            "business_timezone", "business_phone", "business_website", "booking_link",
            "supported_languages", "fallback_language", "greeting_style", "tone",
            "transfer_rules", "transfer_number", "after_hours_behavior", "escalation_triggers",
            "transfer_retry_policy", "calendar_system", "appointment_lead_time",
            "appointment_buffer", "cancellation_policy", "confirmation_channels",
            "notification_channels", "faq", "prohibited_statements", "required_booking_fields"
    );
    static final String LIVE_VOICE_BEHAVIOR = """

            ## Live Voice Behavior
            - Sound like a capable phone receptionist, not a script, form, or chatbot.
            - Keep replies short: usually one sentence, two only when needed, then pause.
            - Ask one question at a time and wait for the caller's answer.
            - Use natural acknowledgements sparingly: "Sure", "Of course", "Got it", "I understand". Vary them.
            - Do not repeat the caller's exact words unless confirming a critical detail.
            - Confirm names, phone numbers, dates, times, email addresses, and booking details when they matter.
            - If a name, phone number, or email sounds unclear, ask the caller to repeat it slowly instead of guessing.
            - For appointments or reservations, do not ask for unnecessary sensitive details. Collect only what is needed to help the caller.
            - Never say a booking, message, transfer, or callback is confirmed unless an available tool result confirms it.
            - If tool results or business facts are missing, say briefly that you do not have the exact information and offer a practical next step.
            - If the caller switches language, follow the caller naturally without announcing the switch.
            - End warmly and briefly when the caller is clearly done.
            """;

    private final AgentTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public SystemAgentTemplateSeeder(AgentTemplateRepository templateRepository, ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        var resource = new ClassPathResource("templates/agent-templates.md");
        String markdown;
        try (var input = resource.getInputStream()) {
            markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        var requests = parse(markdown);
        var synchronizedNames = new LinkedHashSet<String>();
        for (var request : requests) {
            synchronizedNames.add(request.name());
            var existing = templateRepository.findByTenantIsNullAndName(request.name());
            if (existing.isEmpty()) {
                templateRepository.save(new AgentTemplate(null, request));
            } else if (!existing.get().matches(request)) {
                existing.get().update(request);
            }
        }
        templateRepository.findAllByTenantIsNull().stream()
                .filter(template -> !synchronizedNames.contains(template.getName()))
                .forEach(templateRepository::delete);
    }

    List<AgentTemplateRequest> parse(String markdown) {
        var requests = new ArrayList<AgentTemplateRequest>();
        var matcher = TEMPLATE_SECTION.matcher(markdown);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String body = matcher.group(2);
            String industry = metadata(body, "Industry");
            String idealFor = metadata(body, "Ideal For");
            var languages = languageCodes(metadata(body, "Language Support"));
            var capabilities = bulletSection(body, "Key Capabilities", "Variables");
            var verticalVariables = variables(body);
            var variables = new ArrayList<Map<String, Object>>(coreDefinitions());
            verticalVariables.stream()
                    .filter(candidate -> variables.stream().noneMatch(existing -> existing.get("key").equals(candidate.get("key"))))
                    .forEach(variables::add);
            var bookingRequiredFields = csv(metadata(body, "Booking Required Fields"));
            var bookingNotificationChannels = csv(metadata(body, "Owner Notifications"));
            var promptMatcher = PROMPT.matcher(body);
            if (!promptMatcher.find()) {
                throw new IllegalArgumentException("Missing system prompt for template " + name);
            }
            String configuration = configurationJson(
                    industry, capabilities, variables, bookingRequiredFields, bookingNotificationChannels
            );
            requests.add(new AgentTemplateRequest(
                    name,
                    idealFor.isBlank() ? "Ready-to-use voice agent for " + industry + "." : "Designed for " + idealFor + ".",
                    industry,
                    openingDirection(capabilities),
                    withLiveVoiceBehavior(withCoreBehavior(promptMatcher.group(1).trim())),
                    languages.get(0),
                    languages,
                    configuration,
                    true
            ));
        }
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("No agent templates were found in agent-templates.md");
        }
        return requests;
    }

    private String metadata(String body, String key) {
        var matcher = Pattern.compile("(?m)^\\*\\*" + Pattern.quote(key) + ":\\*\\*\\s*(.+)$").matcher(body);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private List<String> bulletSection(String body, String start, String end) {
        var matcher = Pattern.compile(
                "(?ms)^### " + Pattern.quote(start) + "\\s*\\R(.*?)(?=^### " + Pattern.quote(end) + ")"
        ).matcher(body);
        if (!matcher.find()) {
            return List.of();
        }
        return matcher.group(1).lines()
                .map(String::trim)
                .filter(line -> line.startsWith("- "))
                .map(line -> line.substring(2).trim())
                .toList();
    }

    private List<Map<String, Object>> variables(String body) {
        var matcher = Pattern.compile(
                "(?m)^\\|\\s*`\\{\\{([a-zA-Z0-9_]+)}}`\\s*\\|\\s*([^|]+?)\\s*\\|\\s*([^|]+?)\\s*\\|\\s*$"
        ).matcher(body);
        var values = new ArrayList<Map<String, Object>>();
        var seen = new LinkedHashSet<String>();
        while (matcher.find()) {
            var key = matcher.group(1).trim();
            if ("agent_name".equals(key) || "timezone".equals(key) || !seen.add(key)) {
                continue;
            }
            var definition = new LinkedHashMap<String, Object>();
            definition.put("key", key);
            definition.put("label", displayLabel(key));
            definition.put("description", matcher.group(2).trim());
            definition.put("example", matcher.group(3).trim());
            definition.put("required", REQUIRED_VARIABLES.contains(key));
            values.add(definition);
        }
        return List.copyOf(values);
    }

    private String displayLabel(String key) {
        var words = key.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private List<String> languageCodes(String languageSupport) {
        var languages = new ArrayList<String>();
        for (String label : languageSupport.split(",")) {
            String code = switch (label.trim().toLowerCase(Locale.ROOT)) {
                case "english" -> "en";
                case "french" -> "fr";
                case "arabic" -> "ar";
                case "swahili" -> "sw";
                case "spanish" -> "es";
                case "german" -> "de";
                case "portuguese" -> "pt";
                case "italian" -> "it";
                default -> null;
            };
            if (code != null && !languages.contains(code)) {
                languages.add(code);
            }
        }
        return languages.isEmpty() ? List.of("en") : List.copyOf(languages);
    }

    private String configurationJson(
            String industry,
            List<String> capabilities,
            List<Map<String, Object>> variables,
            List<String> configuredBookingFields,
            List<String> configuredNotificationChannels
    ) {
        var configuration = new LinkedHashMap<String, Object>();
        boolean bookingEnabled = capabilities.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("book") || value.contains("schedul") || value.contains("reservation"));
        configuration.put("bookingEnabled", bookingEnabled);
        configuration.put("group", groupFor(industry));
        configuration.put("icon", iconFor(industry));
        configuration.put("escalationPhrases", escalationPhrasesFor(industry));
        configuration.put("capabilities", capabilities);
        configuration.put("variables", variables);
        configuration.put("schemaVersion", 2);
        configuration.put("coreFields", variables.stream()
                .filter(variable -> CORE_VARIABLES.contains(variable.get("key").toString()))
                .toList());
        configuration.put("verticalFields", variables.stream()
                .filter(variable -> !CORE_VARIABLES.contains(variable.get("key").toString()))
                .toList());
        configuration.put("bookingRequiredFields", configuredBookingFields.isEmpty()
                ? bookingRequiredFields(variables, bookingEnabled)
                : configuredBookingFields);
        configuration.put("bookingNotificationChannels", configuredNotificationChannels.isEmpty()
                ? List.of("dashboard", "email")
                : configuredNotificationChannels);
        configuration.put("source", "docs/agent-templates.md");
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize system template configuration", exception);
        }
    }

    private List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> coreDefinitions() {
        return List.of(
                definition("business_name", "Business name", "Customer-facing legal or trading name", "Acme Clinic", true),
                definition("business_description", "Business description", "Short description or tagline", "Friendly neighbourhood clinic", true),
                definition("business_address", "Business address", "Primary address or location list", "12 Main Street", true),
                definition("business_hours", "Business hours", "Weekly hours plus holiday exceptions", "Mon-Fri 09:00-17:00", true),
                definition("business_timezone", "Business timezone", "IANA timezone for all scheduling", "Africa/Cairo", true),
                definition("business_phone", "Business phone", "Number callers may use for callbacks", "+20 111 000 0000", true),
                definition("business_website", "Business website", "Public website", "https://example.com", false),
                definition("booking_link", "Booking link", "Public self-service booking link when available", "https://example.com/book", false),
                definition("supported_languages", "Supported languages", "Languages the business wants this agent to support", "English, French, Arabic", true),
                definition("fallback_language", "Fallback language", "Language to use when caller language is uncertain", "English", true),
                definition("greeting_style", "Greeting style", "How the opening should sound; not a fixed script", "Warm and concise", false),
                definition("tone", "Tone", "Formal, casual, warm, efficient, or another style", "Warm and efficient", false),
                definition("transfer_rules", "Transfer rules", "When and where to transfer", "Human request or urgent issue", false),
                definition("transfer_number", "Transfer number", "Human destination number or extension", "+20 111 000 0001", false),
                definition("after_hours_behavior", "After-hours behavior", "Voicemail, callback capture, or emergency line", "Capture a callback request", true),
                definition("escalation_triggers", "Escalation triggers", "Human request, anger, risk, or out-of-scope conditions", "Explicit human request; angry caller", false),
                definition("transfer_retry_policy", "Transfer retry policy", "Hold time and fallback if nobody answers", "Ring 20 seconds then capture callback", false),
                definition("calendar_system", "Calendar system", "Google Calendar, Calendly, or custom API", "Google Calendar", false),
                definition("appointment_lead_time", "Appointment lead time", "Minimum time before a new booking", "2 hours", false),
                definition("appointment_buffer", "Appointment buffer", "Gap required between appointments", "15 minutes", false),
                definition("cancellation_policy", "Cancellation policy", "Policy text the agent may explain", "Cancel at least 24 hours ahead", false),
                definition("confirmation_channels", "Customer confirmations", "SMS, email, both, or none", "SMS and email", false),
                definition("notification_channels", "Owner notifications", "How the owner is alerted to bookings", "Dashboard and email", true),
                definition("faq", "Frequently asked questions", "Approved question and answer pairs", "Parking: available behind the building", false),
                definition("prohibited_statements", "Prohibited statements", "Claims or advice the agent must never provide", "Never diagnose or guarantee outcomes", false),
                definition("required_booking_fields", "Required booking fields", "Information required before creating a booking", "Name, phone, service, date and time", true)
        );
    }

    private Map<String, Object> definition(
            String key, String label, String description, String example, boolean required
    ) {
        var definition = new LinkedHashMap<String, Object>();
        definition.put("key", key);
        definition.put("label", label);
        definition.put("description", description);
        definition.put("example", example);
        definition.put("required", required);
        return definition;
    }

    private List<String> bookingRequiredFields(List<Map<String, Object>> variables, boolean bookingEnabled) {
        if (!bookingEnabled) return List.of();
        var fields = new ArrayList<>(List.of("caller_name", "caller_phone", "service_type", "appointment_at"));
        variables.stream()
                .map(variable -> variable.get("key").toString())
                .filter(key -> !CORE_VARIABLES.contains(key))
                .filter(key -> key.startsWith("booking_") || key.startsWith("customer_") || key.startsWith("patient_"))
                .forEach(fields::add);
        return fields.stream().distinct().toList();
    }

    private String openingDirection(List<String> capabilities) {
        boolean booking = capabilities.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains("book") || value.contains("schedul") || value.contains("reservation"));
        return """
                Open naturally in the caller's language.
                Sound warm, concise, and professional for this business type.
                Introduce yourself as {{agent_name}} once in the opening.
                %s
                Ask one simple opening question and then wait.
                """.formatted(booking
                ? "If appointment booking is relevant, invite the caller to say what they would like to book."
                : "Invite the caller to say what they need.");
    }

    private String withLiveVoiceBehavior(String systemPrompt) {
        if (systemPrompt.contains("## Live Voice Behavior")) {
            return systemPrompt;
        }
        return systemPrompt + LIVE_VOICE_BEHAVIOR;
    }

    private String withCoreBehavior(String systemPrompt) {
        if (systemPrompt.contains("## Configured Business Profile")) return systemPrompt;
        return systemPrompt + """

                ## Configured Business Profile
                - Business: {{business_name}} â€” {{business_description}}
                - Locations and access: {{business_address}}
                - Hours and exceptions: {{business_hours}} ({{business_timezone}})
                - Public callback number: {{business_phone}}
                - Website and booking link: {{business_website}} {{booking_link}}
                - Supported and fallback languages: {{supported_languages}}; fallback {{fallback_language}}
                - Voice style: {{greeting_style}}; tone {{tone}}

                ## Routing and After-hours Rules
                Follow {{transfer_rules}} using {{transfer_number}}. If transfer does not connect, apply {{transfer_retry_policy}}.
                Outside configured hours, apply {{after_hours_behavior}}. Escalate only for {{escalation_triggers}}.

                ## Booking and Confirmation Rules
                Use {{calendar_system}}. Apply lead time {{appointment_lead_time}}, buffer {{appointment_buffer}}, and cancellation policy {{cancellation_policy}}.
                Send customer confirmations only through {{confirmation_channels}} and alert the owner through {{notification_channels}}.
                Collect the configured fields represented by {{required_booking_fields}} before booking; runtime tool configuration is authoritative.

                ## Approved Knowledge and Compliance
                Use only these approved FAQs: {{faq}}.
                Never make these statements: {{prohibited_statements}}.
                Empty optional values mean no fact or policy is configured; do not invent a replacement.
                """;
    }

    private String groupFor(String industry) {
        return switch (industry) {
            case "Legal Services", "Real Estate Agency", "Finance & Insurance" -> "Sales";
            case "Automotive Repair" -> "Support";
            default -> "Appointments";
        };
    }

    private String iconFor(String industry) {
        return switch (industry) {
            case "Medical Clinic / Doctor's Office" -> "stethoscope";
            case "Dental Practice" -> "smile";
            case "Hair Salon & Beauty" -> "scissors";
            case "Legal Services" -> "scale";
            case "Real Estate Agency" -> "building";
            case "Health & Fitness" -> "dumbbell";
            case "Automotive Repair" -> "wrench";
            case "Food & Beverage" -> "utensils";
            case "Spa & Wellness" -> "sparkles";
            case "Finance & Insurance" -> "landmark";
            default -> "bot";
        };
    }

    private List<String> escalationPhrasesFor(String industry) {
        var phrases = new ArrayList<>(List.of(
                "speak to a person",
                "talk to a human",
                "human agent",
                "representative"
        ));
        if ("Medical Clinic / Doctor's Office".equals(industry) || "Dental Practice".equals(industry)) {
            phrases.add("this is urgent");
            phrases.add("medical emergency");
        } else if ("Legal Services".equals(industry)) {
            phrases.add("urgent legal matter");
        }
        return List.copyOf(phrases);
    }
}
