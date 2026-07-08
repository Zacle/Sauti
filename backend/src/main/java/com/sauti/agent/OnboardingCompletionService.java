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
    private static final Set<String> LANGUAGES = Set.of("en", "fr", "ar");

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

                BUSINESS CONTEXT
                %s

                PRIMARY USE CASE
                %s

                APPROVED SERVICES
                {{bookable_services}}

                BUSINESS WEBSITE
                {{business_website}}

                %s

                %s

                CONVERSATION RULES
                - Be concise, natural, and helpful.
                - Ask one question at a time.
                - Confirm important details before taking action.
                - If a request is outside your scope, explain the limitation and offer human follow-up.
                - Never invent business information, availability, prices, or policies.
                """.formatted(
                request.businessType(),
                request.primaryUseCase(),
                bookingRules,
                categoryGuidance(request.businessType(), request.primaryUseCase())
        );
    }

    private String greetingDirection(String useCase) {
        boolean booking = "Appointment booking".equals(useCase);
        return """
                Generate the opening at call time. Do not use a fixed script.
                Sound like a warm, capable receptionist for the configured business.
                Adapt naturally to the caller's language, the channel, and whether this is a test or live visitor.
                Introduce yourself as {{agent_name}} once in the opening.
                %s
                Ask one simple opening question and then wait.
                """.formatted(booking
                ? "If appointment booking is relevant, invite the caller to say what they would like to book."
                : "Invite the caller to say what they need.");
    }

    private String categoryGuidance(String businessType, String useCase) {
        return switch (businessType) {
            case "Clinics & healthcare", "Healthcare" -> """
                    HEALTHCARE WORKFLOW
                    - Help with appointment requests, opening hours, location, accepted services, and callback routing.
                    - For a consultation request, collect only service/reason at a high level, full name, preferred date/time, and contact detail.
                    - Do not ask for date of birth, symptoms, diagnosis, insurance, medication, or medical history unless a human-approved workflow explicitly requires it.
                    - If the caller describes urgent symptoms or an emergency, advise them to contact local emergency services or the clinic directly and offer human escalation.
                    - Keep language calm and careful. Do not provide medical advice.
                    """;
            case "Salons & beauty" -> """
                    SALON AND BEAUTY WORKFLOW
                    - Help callers choose or book services such as consultations, treatments, styling, follow-ups, or callbacks.
                    - Ask for the requested service, preferred stylist or staff member if relevant, date/time preference, name, and contact detail.
                    - Mention prices, deposits, cancellation rules, and service duration only when configured in business facts.
                    - If the caller is unsure, ask what result they want rather than listing every service.
                    """;
            case "Real estate" -> """
                    REAL ESTATE WORKFLOW
                    - Qualify property enquiries naturally: buying/renting goal, area, property type, budget range, timeframe, and viewing preference.
                    - Do not promise property availability, pricing, financing, or legal terms unless configured or confirmed by a tool.
                    - For viewings, collect name, contact detail, preferred date/time, and property reference if available.
                    - Escalate urgent, legal, offer, or negotiation questions to a human agent.
                    """;
            case "Professional services" -> """
                    PROFESSIONAL SERVICES WORKFLOW
                    - Identify the caller's issue, desired outcome, urgency, and whether they are an existing client.
                    - Collect only the minimum intake details needed for a consultation or callback.
                    - Do not give legal, financial, tax, or regulated advice. Offer to schedule a consultation or route to a human.
                    - Confirm confidentiality-sensitive details briefly and avoid repeating unnecessary personal information.
                    """;
            case "Education" -> """
                    EDUCATION WORKFLOW
                    - Help with admissions, programme information, appointments, callbacks, department routing, and general school questions.
                    - Ask whether the caller is a student, parent, applicant, or partner only when it helps route the request.
                    - Collect programme/department, preferred contact method, name, and timing for follow-up.
                    - Do not invent fees, deadlines, admission decisions, or policy details.
                    """;
            case "Local services", "Local business", "Service team", "Multi-location" -> """
                    LOCAL SERVICES WORKFLOW
                    - Help callers request service, check general availability, ask location/hours questions, or leave a callback request.
                    - Collect service needed, location or service area when relevant, urgency, preferred date/time, name, and contact detail.
                    - Do not quote prices, arrival windows, or guarantees unless configured in business facts or confirmed by a tool.
                    - If the request is urgent or unsafe, offer human escalation.
                    """;
            default -> """
                    GENERAL WORKFLOW
                    - Understand the caller's goal, ask one relevant follow-up, and help with the configured use case.
                    - Collect only the minimum information needed to complete or route the request.
                    - Escalate unclear, urgent, sensitive, or out-of-scope requests to a human.
                    """;
        };
    }

    private void validate(CompleteOnboardingRequest request) {
        requireAllowed("business type", request.businessType(), BUSINESS_TYPES);
        requireAllowed("primary use case", request.primaryUseCase(), USE_CASES);
        requireAllowed("calendar provider", request.calendarProvider(), CALENDAR_PROVIDERS);
        requireAllowed("routing policy", request.routingPolicy(), ROUTING_POLICIES);
        requireAllowed("default language", request.defaultLanguage(), LANGUAGES);
        if (request.supportedLanguages().stream().anyMatch(language -> !LANGUAGES.contains(language))) {
            throw new IllegalArgumentException("Supported languages are fr, ar, and en");
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
