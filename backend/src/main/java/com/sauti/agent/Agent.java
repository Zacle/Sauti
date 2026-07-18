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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "agents")
public class Agent extends Auditable {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    private String description;

    private String twilioPhoneNumber;
    private String twilioPhoneNumberSid;
    private String phoneNumberProvider;
    private String phoneNumberStatus;
    private String phoneNumberOrderId;
    private OffsetDateTime phoneNumberAssignedAt;

    @Column(nullable = false)
    private String defaultLanguage = "fr";

    @Column(nullable = false)
    private String supportedLanguages = "fr,en";

    @Column(nullable = false)
    private String systemPrompt;

    @Column(nullable = false)
    private String greetingMessage;

    private String ttsVoiceId;

    @Column(nullable = false)
    private String ttsProvider = "fake";

    @Column(nullable = false)
    private String llmProvider = "fake";

    @Column(nullable = false)
    private String llmTier = "standard";

    private String escalationPhrases;
    private String humanTransferNumber;
    private String operatingHours;
    @Column(nullable = false)
    private String afterHoursBehavior = "answer";
    private String afterHoursMessage;
    private String knowledgeBase;
    private String businessType;
    private String primaryUseCase;
    private String businessWebsite;
    private String bookableServices;
    private String calendarProvider;
    private String routingPolicy;
    private String voiceProfile;
    @Column(nullable = false)
    private boolean webVoiceEnabled;
    @Column(nullable = false, unique = true, length = 36)
    private String webVoicePublicId;
    private String webVoiceAllowedOrigins;
    @Column(nullable = false)
    private boolean webVoiceRequireConsent = true;
    @Column(nullable = false)
    private boolean whatsappEnabled;
    @Column(unique = true, length = 100)
    private String whatsappPhoneNumberId;

    @Column(nullable = false)
    private int maxCallDurationSeconds = 300;

    @Column(nullable = false)
    private boolean saveTranscript = true;

    @Column(nullable = false)
    private boolean recordCalls = false;

    @Column(nullable = false)
    private double bargeInSensitivity = 0.70;
    @Column(nullable = false)
    private int bargeInGraceMs = 300;
    @Column(nullable = false)
    private int endCallOnSilenceSeconds = 60;
    @Column(nullable = false)
    private int reminderAfterSilenceSeconds = 30;
    @Column(nullable = false)
    private int maxReminders = 1;
    @Column(nullable = false)
    private boolean detectVoicemail = true;
    @Column(nullable = false)
    private boolean handleCallScreening = true;
    @Column(nullable = false)
    private boolean dtmfEnabled;
    @Column(nullable = false)
    private String dtmfTerminationKey = "#";
    @Column(nullable = false)
    private int dtmfInputTimeoutSeconds = 5;
    @Column(nullable = false)
    private int dtmfMaxDigits = 8;
    private String dtmfDigitMappings;
    @Column(nullable = false)
    private int sttEndpointingMs = 300;
    private String sttVocabularyDomain;
    private String sttBoostedKeywords;
    private String safetyGuardrails;
    private String postCallExtractionFields;

    @Column(nullable = false)
    private boolean bookingEnabled = false;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bookingRequiredFields = "caller_name,caller_phone,service_type,appointment_at";

    @Column(nullable = false)
    private String bookingNotificationChannels = "dashboard,email";

    @Column(length = 320)
    private String bookingNotificationRecipient;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private boolean isActive = false;

    protected Agent() {
    }

    public Agent(Tenant tenant, String name, String greetingMessage, String systemPrompt) {
        this.id = UUID.randomUUID();
        this.webVoicePublicId = UUID.randomUUID().toString();
        this.tenant = tenant;
        this.name = name;
        this.greetingMessage = greetingMessage;
        this.systemPrompt = systemPrompt;
    }

    public void update(
            String name,
            String description,
            String greetingMessage,
            String systemPrompt,
            String defaultLanguage,
            List<String> supportedLanguages,
            String ttsVoiceId,
            String humanTransferNumber,
            List<String> escalationPhrases,
            boolean bookingEnabled,
            String timezone,
            String knowledgeBase,
            String operatingHours,
            Integer maxCallDurationSeconds,
            Boolean saveTranscript,
            Boolean recordCalls
    ) {
        var previousName = this.name;
        this.name = name;
        this.description = description == null || description.isBlank() ? summarize(systemPrompt) : description.trim();
        this.greetingMessage = synchronizeAgentName(greetingMessage, previousName, name);
        this.systemPrompt = systemPrompt;
        this.defaultLanguage = defaultLanguage;
        this.supportedLanguages = String.join(",", supportedLanguages);
        this.ttsVoiceId = ttsVoiceId;
        this.humanTransferNumber = humanTransferNumber;
        this.escalationPhrases = String.join(",", escalationPhrases == null || escalationPhrases.isEmpty()
                ? List.of("speak to a human", "talk to a human", "representative", "agent")
                : escalationPhrases);
        this.bookingEnabled = bookingEnabled;
        this.timezone = timezone == null || timezone.isBlank() ? "UTC" : timezone;
        this.knowledgeBase = knowledgeBase;
        this.operatingHours = operatingHours;
        this.maxCallDurationSeconds = maxCallDurationSeconds == null || maxCallDurationSeconds < 60
                ? 300
                : maxCallDurationSeconds;
        this.saveTranscript = saveTranscript == null || saveTranscript;
        this.recordCalls = recordCalls != null && recordCalls;
    }

    private String synchronizeAgentName(String text, String previousName, String currentName) {
        if (text == null || previousName == null || previousName.isBlank()
                || currentName == null || currentName.isBlank() || previousName.equalsIgnoreCase(currentName)) {
            return text;
        }
        return text.replaceAll(
                "(?i)" + java.util.regex.Pattern.quote(previousName.trim()),
                java.util.regex.Matcher.quoteReplacement(currentName.trim())
        );
    }

    public void update(
            String name,
            String greetingMessage,
            String systemPrompt,
            String defaultLanguage,
            List<String> supportedLanguages,
            String humanTransferNumber,
            List<String> escalationPhrases,
            boolean bookingEnabled,
            String timezone,
            String knowledgeBase
    ) {
        update(
                name, null, greetingMessage, systemPrompt, defaultLanguage, supportedLanguages, null,
                humanTransferNumber, escalationPhrases, bookingEnabled, timezone, knowledgeBase, null,
                300, true, false
        );
    }

    private String summarize(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "AI voice agent";
        }
        String firstLine = prompt.lines().filter(line -> !line.isBlank()).findFirst().orElse("AI voice agent");
        return firstLine.length() <= 500 ? firstLine : firstLine.substring(0, 500);
    }

    public void attachTwilioNumber(String number) {
        this.twilioPhoneNumber = number;
    }

    public void attachTwilioNumber(String number, String phoneNumberSid) {
        this.twilioPhoneNumber = number;
        this.twilioPhoneNumberSid = phoneNumberSid;
    }

    public void attachPhoneNumber(TelephonyProvider.PhoneNumberProvisioning provisioning) {
        this.twilioPhoneNumber = provisioning.phoneNumber();
        this.twilioPhoneNumberSid = provisioning.providerReference();
        this.phoneNumberProvider = provisioning.provider();
        this.phoneNumberStatus = provisioning.status();
        this.phoneNumberOrderId = provisioning.providerReference();
        this.phoneNumberAssignedAt = OffsetDateTime.now();
    }

    public void updatePhoneNumberProvisioning(TelephonyProvider.PhoneNumberProvisioning provisioning) {
        if (provisioning.phoneNumber() != null && !provisioning.phoneNumber().isBlank()) {
            this.twilioPhoneNumber = provisioning.phoneNumber();
        }
        this.phoneNumberStatus = provisioning.status();
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
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

    public String getTwilioPhoneNumber() {
        return twilioPhoneNumber;
    }

    public String getTwilioPhoneNumberSid() {
        return twilioPhoneNumberSid;
    }

    public String getPhoneNumberProvider() { return phoneNumberProvider; }
    public String getPhoneNumberStatus() { return phoneNumberStatus; }
    public String getPhoneNumberOrderId() { return phoneNumberOrderId; }
    public OffsetDateTime getPhoneNumberAssignedAt() { return phoneNumberAssignedAt; }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public List<String> getSupportedLanguages() {
        return Arrays.stream(supportedLanguages.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getGreetingMessage() {
        return greetingMessage;
    }

    public String getTtsVoiceId() {
        return ttsVoiceId;
    }

    public void updateTtsVoiceId(String ttsVoiceId) {
        this.ttsVoiceId = ttsVoiceId == null || ttsVoiceId.isBlank() ? null : ttsVoiceId.trim();
    }

    public boolean isActive() {
        return isActive;
    }

    public String getHumanTransferNumber() {
        return humanTransferNumber;
    }

    public List<String> getEscalationPhrases() {
        if (escalationPhrases == null || escalationPhrases.isBlank()) {
            return List.of("speak to a human", "talk to a human", "representative", "agent");
        }
        return Arrays.stream(escalationPhrases.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    public boolean isBookingEnabled() {
        return bookingEnabled;
    }

    public List<String> getBookingRequiredFields() { return split(bookingRequiredFields); }
    public List<String> getBookingNotificationChannels() { return split(bookingNotificationChannels); }
    public String getBookingNotificationRecipient() { return bookingNotificationRecipient; }

    public void configureBookingWorkflow(
            List<String> requiredFields,
            List<String> notificationChannels,
            String notificationRecipient
    ) {
        var configuredFields = requiredFields == null ? List.<String>of() : requiredFields.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .peek(value -> {
                    if (!value.matches("^[a-z][a-z0-9_]{0,63}$")) {
                        throw new IllegalArgumentException("Booking field keys must use lowercase snake_case");
                    }
                })
                .distinct()
                .toList();
        if (configuredFields.size() > 25) throw new IllegalArgumentException("An agent may require at most 25 booking fields");
        var fields = new java.util.ArrayList<String>();
        if (bookingEnabled) fields.addAll(List.of("caller_name", "caller_phone", "service_type", "appointment_at"));
        fields.addAll(configuredFields);
        this.bookingRequiredFields = String.join(",", fields.stream().distinct().toList());
        var channels = notificationChannels == null ? List.<String>of() : notificationChannels.stream()
                .map(value -> value.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(value -> !value.isBlank())
                .filter(value -> List.of("dashboard", "email").contains(value))
                .distinct()
                .toList();
        this.bookingNotificationChannels = String.join(",", channels.isEmpty()
                ? List.of("dashboard")
                : channels);
        this.bookingNotificationRecipient = notificationRecipient == null || notificationRecipient.isBlank()
                ? null
                : notificationRecipient.trim();
    }

    public String getTimezone() {
        return timezone;
    }

    public String getKnowledgeBase() {
        return knowledgeBase;
    }

    public String getOperatingHours() {
        return operatingHours;
    }

    public String getAfterHoursBehavior() { return afterHoursBehavior; }
    public String getAfterHoursMessage() { return afterHoursMessage; }

    public void configureAvailability(String operatingHours, String afterHoursBehavior, String afterHoursMessage) {
        this.operatingHours = operatingHours == null || operatingHours.isBlank() ? "always" : operatingHours;
        this.afterHoursBehavior = afterHoursBehavior == null || afterHoursBehavior.isBlank()
                ? "answer"
                : afterHoursBehavior;
        this.afterHoursMessage = afterHoursMessage == null || afterHoursMessage.isBlank()
                ? null
                : afterHoursMessage.trim();
    }

    public int getMaxCallDurationSeconds() {
        return maxCallDurationSeconds;
    }

    public boolean isSaveTranscript() {
        return saveTranscript;
    }

    public boolean isRecordCalls() {
        return recordCalls;
    }

    public void configureLlmTier(String tier) {
        this.llmTier = "advanced".equalsIgnoreCase(tier) ? "advanced" : "standard";
    }

    public String getLlmTier() { return llmTier; }

    public void configureOnboarding(
            String businessType,
            String primaryUseCase,
            String businessWebsite,
            List<String> bookableServices,
            String calendarProvider,
            String routingPolicy,
            String voiceProfile
    ) {
        this.businessType = businessType;
        this.primaryUseCase = primaryUseCase;
        this.businessWebsite = businessWebsite == null || businessWebsite.isBlank() ? null : businessWebsite.trim();
        this.bookableServices = join(bookableServices);
        this.calendarProvider = calendarProvider;
        this.routingPolicy = routingPolicy;
        this.voiceProfile = voiceProfile;
    }

    public String getBusinessType() { return businessType; }
    public String getPrimaryUseCase() { return primaryUseCase; }
    public String getBusinessWebsite() { return businessWebsite; }
    public List<String> getBookableServices() { return split(bookableServices); }
    public String getCalendarProvider() { return calendarProvider; }
    public String getRoutingPolicy() { return routingPolicy; }
    public String getVoiceProfile() { return voiceProfile; }
    public boolean isWebVoiceEnabled() { return webVoiceEnabled; }
    public String getWebVoicePublicId() { return webVoicePublicId; }
    public List<String> getWebVoiceAllowedOrigins() { return split(webVoiceAllowedOrigins); }
    public boolean isWebVoiceRequireConsent() { return webVoiceRequireConsent; }
    public boolean isWhatsappEnabled() { return whatsappEnabled; }
    public String getWhatsappPhoneNumberId() { return whatsappPhoneNumberId; }

    public void configureWebVoice(Boolean enabled, List<String> allowedOrigins, Boolean requireConsent) {
        this.webVoiceEnabled = enabled != null && enabled;
        this.webVoiceAllowedOrigins = join(allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .distinct()
                .toList());
        this.webVoiceRequireConsent = requireConsent == null || requireConsent;
        if (webVoicePublicId == null || webVoicePublicId.isBlank()) {
            webVoicePublicId = UUID.randomUUID().toString();
        }
    }

    public void configureWhatsApp(Boolean enabled, String phoneNumberId) {
        this.whatsappEnabled = enabled != null && enabled;
        this.whatsappPhoneNumberId = phoneNumberId == null || phoneNumberId.isBlank()
                ? null
                : phoneNumberId.trim();
    }

    public void updateCalendarProvider(String calendarProvider) {
        this.calendarProvider = calendarProvider;
    }

    public void updateRoutingPolicy(String routingPolicy) {
        this.routingPolicy = routingPolicy;
    }

    public void updateCallBehavior(
            Double bargeInSensitivity, Integer bargeInGraceMs, Integer endCallOnSilenceSeconds,
            Integer reminderAfterSilenceSeconds, Integer maxReminders, Boolean detectVoicemail,
            Boolean handleCallScreening, Boolean dtmfEnabled, Integer sttEndpointingMs,
            String sttVocabularyDomain, String sttBoostedKeywords, List<String> safetyGuardrails,
            List<String> postCallExtractionFields
    ) {
        this.bargeInSensitivity = clamp(bargeInSensitivity == null ? 0.70 : bargeInSensitivity, 0, 1);
        this.bargeInGraceMs = range(bargeInGraceMs, 0, 3000, 300);
        this.endCallOnSilenceSeconds = range(endCallOnSilenceSeconds, 60, 3600, 60);
        this.reminderAfterSilenceSeconds = range(reminderAfterSilenceSeconds, 30, 600, 30);
        this.maxReminders = range(maxReminders, 0, 10, 1);
        this.detectVoicemail = detectVoicemail == null || detectVoicemail;
        this.handleCallScreening = handleCallScreening == null || handleCallScreening;
        this.dtmfEnabled = dtmfEnabled != null && dtmfEnabled;
        this.sttEndpointingMs = range(sttEndpointingMs, 100, 3000, 300);
        this.sttVocabularyDomain = sttVocabularyDomain;
        this.sttBoostedKeywords = sttBoostedKeywords;
        this.safetyGuardrails = join(safetyGuardrails);
        this.postCallExtractionFields = join(postCallExtractionFields);
    }

    private int range(Integer value, int minimum, int maximum, int fallback) {
        return value == null ? fallback : Math.max(minimum, Math.min(maximum, value));
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private String join(List<String> values) {
        return values == null || values.isEmpty() ? null : String.join(",", values);
    }

    private List<String> split(String value) {
        return value == null || value.isBlank() ? List.of()
                : Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    public double getBargeInSensitivity() { return bargeInSensitivity; }
    public int getBargeInGraceMs() { return bargeInGraceMs; }
    public int getEndCallOnSilenceSeconds() { return endCallOnSilenceSeconds; }
    public int getReminderAfterSilenceSeconds() { return reminderAfterSilenceSeconds; }
    public int getMaxReminders() { return maxReminders; }
    public boolean isDetectVoicemail() { return detectVoicemail; }
    public boolean isHandleCallScreening() { return handleCallScreening; }
    public boolean isDtmfEnabled() { return dtmfEnabled; }
    public String getDtmfTerminationKey() { return dtmfTerminationKey; }
    public int getDtmfInputTimeoutSeconds() { return dtmfInputTimeoutSeconds; }
    public int getDtmfMaxDigits() { return dtmfMaxDigits; }
    public Map<String, String> getDtmfDigitMappings() {
        var mappings = new LinkedHashMap<String, String>();
        if (dtmfDigitMappings == null || dtmfDigitMappings.isBlank()) return mappings;
        dtmfDigitMappings.lines().forEach(line -> {
            var separator = line.indexOf('=');
            if (separator <= 0) return;
            var digits = line.substring(0, separator).trim();
            var meaning = line.substring(separator + 1).trim();
            if (!digits.isBlank() && !meaning.isBlank()) mappings.put(digits, meaning);
        });
        return mappings;
    }

    public void configureDtmf(
            String terminationKey,
            Integer inputTimeoutSeconds,
            Integer maxDigits,
            Map<String, String> digitMappings
    ) {
        this.dtmfTerminationKey = "#".equals(terminationKey) || "*".equals(terminationKey)
                ? terminationKey
                : "#";
        this.dtmfInputTimeoutSeconds = range(inputTimeoutSeconds, 1, 30, 5);
        this.dtmfMaxDigits = range(maxDigits, 1, 32, 8);
        if (digitMappings == null || digitMappings.isEmpty()) {
            this.dtmfDigitMappings = null;
            return;
        }
        this.dtmfDigitMappings = digitMappings.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> Map.entry(entry.getKey().trim(), entry.getValue().trim()))
                .filter(entry -> entry.getKey().matches("[0-9*#]{1,32}") && !entry.getValue().isBlank())
                .map(entry -> entry.getKey() + "=" + entry.getValue().replace("\r", " ").replace("\n", " "))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }
    public int getSttEndpointingMs() { return sttEndpointingMs; }
    public String getSttVocabularyDomain() { return sttVocabularyDomain; }
    public String getSttBoostedKeywords() { return sttBoostedKeywords; }
    public List<String> getSafetyGuardrails() { return split(safetyGuardrails); }
    public List<String> getPostCallExtractionFields() { return split(postCallExtractionFields); }

    public boolean isAvailableAt(OffsetDateTime instant) {
        return OperatingHoursSchedule.isOpen(
                OperatingHoursSchedule.effective(this),
                instant.atZoneSameInstant(ZoneId.of(timezone))
        );
    }
}
