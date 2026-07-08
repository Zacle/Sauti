package com.sauti.agent;

import com.sauti.agent.AgentDtos.AgentRequest;
import com.sauti.agent.OnboardingDtos.CompleteOnboardingRequest;
import com.sauti.tool.DefaultToolSeeder;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingCompletionService {
    private static final Set<String> BUSINESS_TYPES = Set.of(
            "Local business", "Healthcare", "Service team", "Multi-location",
            "Clinics & healthcare", "Salons & beauty", "Real estate",
            "Professional services", "Education", "Local services"
    );
    private static final Set<String> USE_CASES = Set.of(
            "Appointment booking", "Customer support", "Lead qualification", "Call routing", "Reminders"
    );
    private static final Set<String> CALENDAR_PROVIDERS = Set.of(
            "Google Calendar", "Calendly", "Custom webhook", "Set up later"
    );
    private static final Set<String> ROUTING_POLICIES = Set.of("Fixed calendar", "Set up later");
    private static final Set<String> LANGUAGES = Set.of("sw", "en", "fr", "ar");

    private final AgentService agentService;
    private final AgentRepository agentRepository;
    private final AgentVariableService agentVariableService;
    private final DefaultToolSeeder defaultToolSeeder;

    public OnboardingCompletionService(
            AgentService agentService,
            AgentRepository agentRepository,
            AgentVariableService agentVariableService,
            DefaultToolSeeder defaultToolSeeder
    ) {
        this.agentService = agentService;
        this.agentRepository = agentRepository;
        this.agentVariableService = agentVariableService;
        this.defaultToolSeeder = defaultToolSeeder;
    }

    @Transactional
    public Agent complete(UUID tenantId, CompleteOnboardingRequest request) {
        validate(request);
        var services = request.bookableServices().stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        boolean bookingEnabled = "Appointment booking".equals(request.primaryUseCase());
        String website = normalizeWebsite(request.businessWebsite());
        String voiceProfile = request.voiceProfile() == null || request.voiceProfile().isBlank()
                ? "Provider default"
                : request.voiceProfile().trim();
        String prompt = prompt(request, bookingEnabled);
        boolean healthcare = isHealthcare(request.businessType());
        var draft = new AgentRequest(
                request.agentName().trim(),
                request.primaryUseCase() + " agent for " + request.businessType().toLowerCase(Locale.ROOT),
                greetingDirection(request.primaryUseCase()),
                prompt,
                request.defaultLanguage(),
                List.copyOf(new LinkedHashSet<>(request.supportedLanguages())),
                blankToNull(request.ttsVoiceId()),
                null,
                List.of("speak to a person", "talk to a human", "human agent", "representative"),
                bookingEnabled,
                request.timezone(),
                "",
                "workspace",
                "answer",
                null,
                300,
                true,
                false,
                healthcare ? "advanced" : "standard",
                0.70,
                300,
                600,
                10,
                1,
                true,
                true,
                false,
                300,
                healthcare ? "medical" : null,
                services.isEmpty() ? null : String.join(",", services),
                healthcare ? List.of("medical diagnosis") : List.of(),
                List.of("summary", "successful", "sentiment", "intent"),
                "#",
                5,
                8,
                java.util.Map.of(),
                false,
                java.util.List.of(),
                true,
                false,
                null
        );

        var agent = agentService.create(tenantId, draft);
        agent.configureOnboarding(
                request.businessType(),
                request.primaryUseCase(),
                website,
                services,
                request.calendarProvider(),
                request.routingPolicy(),
                voiceProfile
        );
        agentRepository.save(agent);
        defaultToolSeeder.configureOnboardingDraft(agent);
        seedVariables(tenantId, agent, website, services, request);
        return agent;
    }

    private void seedVariables(
            UUID tenantId,
            Agent agent,
            String website,
            List<String> services,
            CompleteOnboardingRequest request
    ) {
        agentVariableService.create(
                tenantId, agent.getId(), "business_website", "Business website",
                "Website the agent can reference when directing callers.", website, false
        );
        agentVariableService.create(
                tenantId, agent.getId(), "bookable_services", "Bookable services",
                "Services the agent is allowed to discuss and book.", String.join(", ", services),
                agent.isBookingEnabled()
        );
        agentVariableService.create(
                tenantId, agent.getId(), "calendar_provider", "Calendar destination",
                "Calendar selected during onboarding. Credentials are connected separately.",
                request.calendarProvider(), false
        );
        agentVariableService.create(
                tenantId, agent.getId(), "routing_policy", "Meeting routing",
                "How confirmed bookings should be assigned.", request.routingPolicy(), false
        );
    }

    private String prompt(CompleteOnboardingRequest request, boolean bookingEnabled) {
        String bookingRules = bookingEnabled
                ? """
                  BOOKING RULES
                  - Only offer services listed in {{bookable_services}}.
                  - Check availability before offering a time.
                  - Confirm the caller's name, phone number, service, date, and time before booking.
                  - Collect booking details in a phone-friendly order: service, full name, date, time preference, then contact detail.
                  - Do not ask for date of birth, medical history, insurance, symptoms, or other sensitive details unless the caller volunteers them or the business explicitly requires them.
                  - If the caller asks what services are offered, answer only from {{bookable_services}}. If that list is empty, say you do not have the exact service list and offer human follow-up.
                  - The selected calendar destination is {{calendar_provider}} using {{routing_policy}} routing.
                  """
                : """
                  SCOPE RULES
                  - Help only with the configured use case and services.
                  - Do not claim that an appointment has been booked.
                  """;
        return """
                You are {{agent_name}}, a professional AI voice agent.

                BUSINESS TYPE
                %s

                PRIMARY USE CASE
                %s

                APPROVED SERVICES
                {{bookable_services}}

                BUSINESS WEBSITE
                {{business_website}}

                %s

                CONVERSATION RULES
                - Be concise, natural, and helpful.
                - Ask one question at a time.
                - Confirm important details before taking action.
                - If a request is outside your scope, explain the limitation and offer human follow-up.
                - Never invent business information, availability, prices, or policies.
                """.formatted(request.businessType(), request.primaryUseCase(), bookingRules);
    }

    private String greetingDirection(String useCase) {
        boolean booking = "Appointment booking".equals(useCase);
        return """
                Generate the opening at call time. Do not use a fixed script.
                Sound like a warm, capable receptionist for the configured business.
                Adapt naturally to the caller's language, the channel, and whether this is a test or live visitor.
                Mention {{agent_name}} only when it sounds natural.
                %s
                Ask one simple opening question and then wait.
                """.formatted(booking
                ? "If appointment booking is relevant, invite the caller to say what they would like to book."
                : "Invite the caller to say what they need.");
    }

    private void validate(CompleteOnboardingRequest request) {
        requireAllowed("business type", request.businessType(), BUSINESS_TYPES);
        requireAllowed("primary use case", request.primaryUseCase(), USE_CASES);
        requireAllowed("calendar provider", request.calendarProvider(), CALENDAR_PROVIDERS);
        requireAllowed("routing policy", request.routingPolicy(), ROUTING_POLICIES);
        requireAllowed("default language", request.defaultLanguage(), LANGUAGES);
        if (request.supportedLanguages().stream().anyMatch(language -> !LANGUAGES.contains(language))) {
            throw new IllegalArgumentException("Supported languages are fr, ar, sw, and en");
        }
        if (!request.supportedLanguages().contains(request.defaultLanguage())) {
            throw new IllegalArgumentException("Default language must be included in supported languages");
        }
    }

    private void requireAllowed(String label, String value, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException("Unsupported " + label + ": " + value);
        }
    }

    private String normalizeWebsite(String website) {
        if (website == null || website.isBlank()) return "";
        try {
            var uri = URI.create(website.trim());
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Business website must be an http or https URL");
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Business website must be a valid http or https URL");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isHealthcare(String businessType) {
        return "Healthcare".equals(businessType) || "Clinics & healthcare".equals(businessType);
    }
}
