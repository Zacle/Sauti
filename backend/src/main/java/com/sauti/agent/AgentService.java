package com.sauti.agent;

import com.sauti.agent.AgentDtos.AgentRequest;
import com.sauti.calendar.BookingRepository;
import com.sauti.billing.BillingLedgerService;
import com.sauti.provisioning.PilotProvisioningPolicyService;
import com.sauti.call.CallRepository;
import com.sauti.outbound.ScheduledCallRepository;
import com.sauti.tenant.TenantRepository;
import com.sauti.tool.DefaultToolSeeder;
import com.sauti.tool.AgentToolRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {
    private final AgentRepository agentRepository;
    private final TenantRepository tenantRepository;
    private final TelephonyProvider telephonyProvider;
    private final DefaultToolSeeder defaultToolSeeder;
    private final AgentVariableService agentVariableService;
    private final AgentToolRepository agentToolRepository;
    private final CallRepository callRepository;
    private final BookingRepository bookingRepository;
    private final ScheduledCallRepository scheduledCallRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ApplicationEventPublisher events;
    private final BillingLedgerService billingLedger;
    private final PilotProvisioningPolicyService provisioningPolicies;

    public AgentService(
            AgentRepository agentRepository,
            TenantRepository tenantRepository,
            TelephonyProvider telephonyProvider,
            DefaultToolSeeder defaultToolSeeder,
            AgentVariableService agentVariableService,
            AgentToolRepository agentToolRepository,
            CallRepository callRepository,
            BookingRepository bookingRepository,
            ScheduledCallRepository scheduledCallRepository,
            KnowledgeBaseService knowledgeBaseService,
            ApplicationEventPublisher events,
            BillingLedgerService billingLedger,
            PilotProvisioningPolicyService provisioningPolicies
    ) {
        this.agentRepository = agentRepository;
        this.tenantRepository = tenantRepository;
        this.telephonyProvider = telephonyProvider;
        this.defaultToolSeeder = defaultToolSeeder;
        this.agentVariableService = agentVariableService;
        this.agentToolRepository = agentToolRepository;
        this.callRepository = callRepository;
        this.bookingRepository = bookingRepository;
        this.scheduledCallRepository = scheduledCallRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.events = events;
        this.billingLedger = billingLedger;
        this.provisioningPolicies = provisioningPolicies;
    }

    @Transactional(readOnly = true)
    public List<Agent> list(UUID tenantId) {
        return agentRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public Agent get(UUID tenantId, UUID agentId) {
        return agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found"));
    }

    @Transactional
    public Agent create(UUID tenantId, AgentRequest request) {
        validateLanguages(request.defaultLanguage(), request.supportedLanguages());
        validateTelnyxVoice(request.ttsVoiceId());
        validateAvailability(request);
        var tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        var timezone = validateTimezone(request.timezone() == null || request.timezone().isBlank()
                ? tenant.getTimezone()
                : request.timezone());
        var bookingDuration = request.defaultBookingDurationMinutes() == null
                ? tenant.getDefaultBookingDurationMinutes()
                : request.defaultBookingDurationMinutes();
        var agent = new Agent(tenant, request.name(), request.greetingMessage(), request.systemPrompt());
        agent.update(
                request.name(),
                request.description(),
                request.greetingMessage(),
                request.systemPrompt(),
                request.defaultLanguage(),
                request.supportedLanguages(),
                request.ttsVoiceId(),
                request.humanTransferNumber(),
                request.escalationPhrases(),
                request.bookingEnabled(),
                timezone,
                knowledgeBaseService.normalize(request.knowledgeBase()),
                request.operatingHours(),
                request.maxCallDurationSeconds(),
                request.saveTranscript(),
                request.recordCalls()
        );
        agent.configureAvailability(request.operatingHours(), request.afterHoursBehavior(), request.afterHoursMessage());
        applyCallBehavior(agent, request);
        agent.configureBookingWorkflow(
                bookingDuration,
                request.bookingRequiredFields(),
                request.bookingNotificationChannels(),
                request.bookingNotificationRecipient()
        );
        agent.configureLlmTier(request.llmTier());
        var saved = agentRepository.save(agent);
        defaultToolSeeder.seedDefaults(saved);
        events.publishEvent(new AgentConfigurationChanged(tenantId, saved.getId()));
        return saved;
    }

    @Transactional
    public Agent update(UUID tenantId, UUID agentId, AgentRequest request) {
        validateLanguages(request.defaultLanguage(), request.supportedLanguages());
        validateTelnyxVoice(request.ttsVoiceId());
        validateAvailability(request);
        var timezone = validateTimezone(request.timezone());
        var agent = get(tenantId, agentId);
        agent.update(
                request.name(),
                request.description(),
                request.greetingMessage(),
                request.systemPrompt(),
                request.defaultLanguage(),
                request.supportedLanguages(),
                request.ttsVoiceId(),
                request.humanTransferNumber(),
                request.escalationPhrases(),
                request.bookingEnabled(),
                timezone,
                knowledgeBaseService.normalize(request.knowledgeBase()),
                request.operatingHours(),
                request.maxCallDurationSeconds(),
                request.saveTranscript(),
                request.recordCalls()
        );
        agent.configureAvailability(request.operatingHours(), request.afterHoursBehavior(), request.afterHoursMessage());
        applyCallBehavior(agent, request);
        agent.configureBookingWorkflow(
                request.defaultBookingDurationMinutes(),
                request.bookingRequiredFields(),
                request.bookingNotificationChannels(),
                request.bookingNotificationRecipient()
        );
        agent.configureLlmTier(request.llmTier());
        defaultToolSeeder.synchronizeCapabilities(agent);
        events.publishEvent(new AgentConfigurationChanged(tenantId, agent.getId()));
        return agent;
    }

    @Transactional
    public Agent updateTimezone(UUID tenantId, UUID agentId, String timezone) {
        var agent = get(tenantId, agentId);
        agent.updateTimezone(validateTimezone(timezone));
        return agent;
    }

    private String validateTimezone(String timezone) {
        var normalized = timezone == null || timezone.isBlank() ? "UTC" : timezone.trim();
        try {
            return java.time.ZoneId.of(normalized).getId();
        } catch (java.time.DateTimeException exception) {
            throw new IllegalArgumentException("Unsupported timezone: " + normalized);
        }
    }

    private void validateTelnyxVoice(String voiceId) {
        if (voiceId == null || voiceId.isBlank()) {
            return;
        }
        if (!voiceId.trim().toLowerCase(java.util.Locale.ROOT).startsWith("telnyx.")) {
            throw new IllegalArgumentException("Select a Telnyx voice from the available voice catalog");
        }
    }

    private void validateAvailability(AgentRequest request) {
        OperatingHoursSchedule.validate(request.operatingHours());
        var behavior = request.afterHoursBehavior() == null || request.afterHoursBehavior().isBlank()
                ? "answer"
                : request.afterHoursBehavior();
        if (!java.util.Set.of("answer", "take_message", "closed").contains(behavior)) {
            throw new IllegalArgumentException("Unsupported after-hours behavior: " + behavior);
        }
    }

    @Transactional
    public Agent activate(UUID tenantId, UUID agentId) {
        var agent = get(tenantId, agentId);
        boolean phoneConfigured = agent.getTwilioPhoneNumber() != null && !agent.getTwilioPhoneNumber().isBlank();
        boolean whatsappConfigured = agent.isWhatsappEnabled()
                && agent.getWhatsappPhoneNumberId() != null
                && !agent.getWhatsappPhoneNumberId().isBlank();
        if (!phoneConfigured && !agent.isWebVoiceEnabled() && !whatsappConfigured) {
            throw new IllegalArgumentException("Enable Web Voice, connect WhatsApp, or assign a phone number before activating this agent");
        }
        if (phoneConfigured) provisioningPolicies.authorize(tenantId, "live_calling");
        if (whatsappConfigured) provisioningPolicies.authorize(tenantId, "whatsapp");
        var missingVariables = agentVariableService.missingRequired(agentId);
        if (!missingVariables.isEmpty()) {
            throw new IllegalArgumentException(
                    "Complete required business details before activation: " + String.join(", ", missingVariables)
            );
        }
        agent.activate();
        return agent;
    }

    @Transactional
    public Agent deactivate(UUID tenantId, UUID agentId) {
        var agent = get(tenantId, agentId);
        agent.deactivate();
        return agent;
    }

    @Transactional
    public void delete(UUID tenantId, UUID agentId) {
        var agent = get(tenantId, agentId);
        if (callRepository.existsByAgent_Id(agentId)
                || bookingRepository.existsByAgent_Id(agentId)
                || scheduledCallRepository.existsByAgent_Id(agentId)) {
            throw new IllegalArgumentException(
                    "This agent has call or booking history and cannot be permanently deleted"
            );
        }
        agentRepository.delete(agent);
    }

    @Transactional(readOnly = true)
    public List<TelephonyProvider.AvailablePhoneNumber> searchAvailableNumbers(
            UUID tenantId,
            UUID agentId,
            String countryCode,
            int limit
    ) {
        var agent = get(tenantId, agentId);
        var country = countryCode == null || countryCode.isBlank()
                ? agent.getTenant().getCountryCode()
                : countryCode;
        return telephonyProvider.searchAvailableNumbers(country, limit);
    }

    @Transactional
    public Agent provisionNumber(
            UUID tenantId,
            UUID agentId,
            String requestedPhoneNumber,
            boolean replaceExisting,
            boolean providerChargesConfirmed
    ) {
        if (!providerChargesConfirmed) {
            throw new IllegalArgumentException(
                    "Confirm the provider's upfront and recurring phone-number charges before purchasing"
            );
        }
        var agent = get(tenantId, agentId);
        if (requestedPhoneNumber == null || requestedPhoneNumber.isBlank()) {
            throw new IllegalArgumentException("Select an available phone number before purchasing");
        }
        if (agent.getTwilioPhoneNumber() != null && !agent.getTwilioPhoneNumber().isBlank()) {
            if (!replaceExisting) {
                throw new IllegalArgumentException("This agent already has a phone number");
            }
        }
        var quote = telephonyProvider.quotePhoneNumber(agent.getTenant().getCountryCode(), requestedPhoneNumber);
        provisioningPolicies.authorize(tenantId, "phone_numbers", quote.initialEstimatedCost(), quote.currency());
        billingLedger.authorizePaidResource(tenantId, quote.initialEstimatedCost(), quote.currency());
        var provisioning = telephonyProvider.provisionPhoneNumber(
                agent.getTenant().getCountryCode(),
                requestedPhoneNumber
        );
        agent.attachPhoneNumber(provisioning);
        var reference = provisioning.providerReference() == null || provisioning.providerReference().isBlank()
                ? requestedPhoneNumber : provisioning.providerReference();
        billingLedger.recordDebit(
                tenantId,
                "phone_number_purchase",
                java.math.BigDecimal.ONE,
                "number",
                quote.initialEstimatedCost(),
                quote.currency(),
                "phone-number-order:" + reference,
                requestedPhoneNumber,
                "Phone number purchase and first estimated monthly rental",
                java.util.Map.of(
                        "phoneNumber", requestedPhoneNumber,
                        "provider", provisioning.provider(),
                        "providerReference", reference,
                        "upfrontCost", quote.upfrontCost(),
                        "monthlyCost", quote.monthlyCost()
                )
        );
        return agent;
    }

    @Transactional
    public Agent refreshPhoneNumber(UUID tenantId, UUID agentId) {
        var agent = get(tenantId, agentId);
        var refreshed = telephonyProvider.refreshPhoneNumber(agent.getPhoneNumberOrderId());
        if (refreshed != null) agent.updatePhoneNumberProvisioning(refreshed);
        return agent;
    }

    private void validateLanguages(String defaultLanguage, List<String> supportedLanguages) {
        var languageCode = java.util.regex.Pattern.compile("(?i)^[a-z]{2,3}(?:-[a-z0-9]{2,8})*$");
        if (defaultLanguage == null || !languageCode.matcher(defaultLanguage).matches()) {
            throw new IllegalArgumentException("Default language must be a valid BCP 47 language code");
        }
        if (supportedLanguages == null || supportedLanguages.isEmpty()
                || supportedLanguages.stream().anyMatch(language -> language == null || !languageCode.matcher(language).matches())) {
            throw new IllegalArgumentException("Supported languages must use valid BCP 47 language codes");
        }
        if (!supportedLanguages.contains(defaultLanguage)) {
            throw new IllegalArgumentException("Default language must be supported");
        }
    }

    private void applyCallBehavior(Agent agent, AgentRequest request) {
        agent.updateCallBehavior(
                request.bargeInSensitivity(), request.bargeInGraceMs(), request.endCallOnSilenceSeconds(),
                request.reminderAfterSilenceSeconds(), request.maxReminders(), request.detectVoicemail(),
                request.handleCallScreening(), request.dtmfEnabled(), request.sttEndpointingMs(),
                request.sttVocabularyDomain(), request.sttBoostedKeywords(), request.safetyGuardrails(),
                request.postCallExtractionFields()
        );
        agent.configureDtmf(
                request.dtmfTerminationKey(),
                request.dtmfInputTimeoutSeconds(),
                request.dtmfMaxDigits(),
                request.dtmfDigitMappings()
        );
        agent.configureWebVoice(
                request.webVoiceEnabled(),
                request.webVoiceAllowedOrigins(),
                request.webVoiceRequireConsent()
        );
        agent.configureWhatsApp(request.whatsappEnabled(), request.whatsappPhoneNumberId());
    }
}
