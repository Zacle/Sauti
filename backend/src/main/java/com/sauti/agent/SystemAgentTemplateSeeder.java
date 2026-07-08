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
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("fr", "ar", "en");
    private static final Set<String> REQUIRED_VARIABLES = Set.of(
            "clinic_name", "salon_name", "firm_name", "agency_name", "gym_name",
            "shop_name", "restaurant_name", "spa_name", "company_name",
            "clinic_hours", "salon_hours", "office_hours", "gym_hours",
            "shop_hours", "spa_hours", "hours"
    );

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
            var variables = variables(body);
            var promptMatcher = PROMPT.matcher(body);
            if (!promptMatcher.find()) {
                throw new IllegalArgumentException("Missing system prompt for template " + name);
            }
            String configuration = configurationJson(industry, capabilities, variables);
            requests.add(new AgentTemplateRequest(
                    name,
                    idealFor.isBlank() ? "Ready-to-use voice agent for " + industry + "." : "Designed for " + idealFor + ".",
                    industry,
                    openingDirection(capabilities),
                    promptMatcher.group(1).trim(),
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
                default -> null;
            };
            if (code != null && ALLOWED_LANGUAGES.contains(code) && !languages.contains(code)) {
                languages.add(code);
            }
        }
        return languages.isEmpty() ? List.of("en") : List.copyOf(languages);
    }

    private String configurationJson(
            String industry,
            List<String> capabilities,
            List<Map<String, Object>> variables
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
        configuration.put("source", "docs/agent-templates.md");
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize system template configuration", exception);
        }
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
