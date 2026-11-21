package com.sauti.agent;

import com.sauti.shared.Auditable;
import com.sauti.tenant.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
    @Embedded
    private WebVoiceChannel webVoice = WebVoiceChannel.newChannel();
    @Embedded
    private WhatsAppChannel whatsapp = WhatsAppChannel.off();

    @Embedded
    private CallBehaviour callBehaviour = CallBehaviour.defaults();
    @Embedded
    private CallRecording recording = CallRecording.defaults();
    @Embedded
    private KeypadInput keypad = KeypadInput.defaults();
    @Embedded
    private SpeechRecognition speech = SpeechRecognition.defaults();
    @Embedded
    private BookingWorkflow booking = BookingWorkflow.defaults();
    private String safetyGuardrails;
    private String postCallExtractionFields;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(nullable = false)
    private boolean isActive = false;

    protected Agent() {
    }

    public Agent(Tenant tenant, String name, String greetingMessage, String systemPrompt) {
        this.id = UUID.randomUUID();
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
        booking.enable(bookingEnabled);
        this.timezone = timezone == null || timezone.isBlank() ? "UTC" : timezone;
        this.knowledgeBase = knowledgeBase;
        this.operatingHours = operatingHours;
        callBehaviour.limitDuration(maxCallDurationSeconds);
        recording.configure(saveTranscript, recordCalls);
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

    public void updateTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void activate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    /** The number was given back to the carrier; the agent can no longer receive calls on it. */
    public void releasePhoneNumber() {
        this.isActive = false;
        this.twilioPhoneNumber = null;
        this.twilioPhoneNumberSid = null;
        this.phoneNumberStatus = "released";
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

    public boolean isBookingEnabled() { return booking.isEnabled(); }
    public int getDefaultBookingDurationMinutes() { return booking.getDefaultDurationMinutes(); }
    public List<String> getBookingRequiredFields() { return booking.getRequiredFields(); }
    public List<String> getBookingNotificationChannels() { return booking.getNotificationChannels(); }
    public String getBookingNotificationRecipient() { return booking.getNotificationRecipient(); }

    /** Applies after {@link #update}, so the core booking fields follow the current on/off switch. */
    public void configureBookingWorkflow(
            Integer durationMinutes,
            List<String> requiredFields,
            List<String> notificationChannels,
            String notificationRecipient
    ) {
        booking.configure(durationMinutes, requiredFields, notificationChannels, notificationRecipient);
    }

    public void configureBookingWorkflow(
            List<String> requiredFields,
            List<String> notificationChannels,
            String notificationRecipient
    ) {
        configureBookingWorkflow(null, requiredFields, notificationChannels, notificationRecipient);
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

    public int getMaxCallDurationSeconds() { return callBehaviour.getMaxCallDurationSeconds(); }
    public boolean isSaveTranscript() { return recording.isSaveTranscript(); }
    public boolean isRecordCalls() { return recording.isRecordCalls(); }

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
    public WebVoiceChannel getWebVoice() { return webVoice; }
    public WhatsAppChannel getWhatsapp() { return whatsapp; }
    public KeypadInput getKeypad() { return keypad; }
    public CallBehaviour getCallBehaviour() { return callBehaviour; }
    public CallRecording getRecording() { return recording; }
    public SpeechRecognition getSpeech() { return speech; }
    public BookingWorkflow getBooking() { return booking; }
    public boolean isWebVoiceEnabled() { return webVoice.isEnabled(); }
    public String getWebVoicePublicId() { return webVoice.getPublicId(); }
    public List<String> getWebVoiceAllowedOrigins() { return webVoice.getAllowedOrigins(); }
    public boolean isWebVoiceRequireConsent() { return webVoice.isRequireConsent(); }
    public boolean isWhatsappEnabled() { return whatsapp.isEnabled(); }
    public String getWhatsappPhoneNumberId() { return whatsapp.getPhoneNumberId(); }

    public void configureWebVoice(Boolean enabled, List<String> allowedOrigins, Boolean requireConsent) {
        webVoice.configure(enabled, allowedOrigins, requireConsent);
    }

    public void configureWhatsApp(Boolean enabled, String phoneNumberId) {
        whatsapp.configure(enabled, phoneNumberId);
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
        callBehaviour.configure(bargeInSensitivity, bargeInGraceMs, endCallOnSilenceSeconds,
                reminderAfterSilenceSeconds, maxReminders, detectVoicemail, handleCallScreening);
        keypad.enable(dtmfEnabled);
        speech.configure(sttEndpointingMs, sttVocabularyDomain, sttBoostedKeywords);
        this.safetyGuardrails = Ranges.join(safetyGuardrails);
        this.postCallExtractionFields = Ranges.join(postCallExtractionFields);
    }

    private String join(List<String> values) {
        return Ranges.join(values);
    }

    private List<String> split(String value) {
        return Ranges.split(value);
    }

    public double getBargeInSensitivity() { return callBehaviour.getBargeInSensitivity(); }
    public int getBargeInGraceMs() { return callBehaviour.getBargeInGraceMs(); }
    public int getEndCallOnSilenceSeconds() { return callBehaviour.getEndCallOnSilenceSeconds(); }
    public int getReminderAfterSilenceSeconds() { return callBehaviour.getReminderAfterSilenceSeconds(); }
    public int getMaxReminders() { return callBehaviour.getMaxReminders(); }
    public boolean isDetectVoicemail() { return callBehaviour.isDetectVoicemail(); }
    public boolean isHandleCallScreening() { return callBehaviour.isHandleCallScreening(); }
    public boolean isDtmfEnabled() { return keypad.isEnabled(); }
    public String getDtmfTerminationKey() { return keypad.getTerminationKey(); }
    public int getDtmfInputTimeoutSeconds() { return keypad.getInputTimeoutSeconds(); }
    public int getDtmfMaxDigits() { return keypad.getMaxDigits(); }
    public Map<String, String> getDtmfDigitMappings() { return keypad.getDigitMappings(); }

    public void configureDtmf(
            String terminationKey,
            Integer inputTimeoutSeconds,
            Integer maxDigits,
            Map<String, String> digitMappings
    ) {
        keypad.configure(terminationKey, inputTimeoutSeconds, maxDigits, digitMappings);
    }
    public int getSttEndpointingMs() { return speech.getEndpointingMs(); }
    public String getSttVocabularyDomain() { return speech.getVocabularyDomain(); }
    public String getSttBoostedKeywords() { return speech.getBoostedKeywords(); }
    public List<String> getSafetyGuardrails() { return split(safetyGuardrails); }
    public List<String> getPostCallExtractionFields() { return split(postCallExtractionFields); }

    public boolean isAvailableAt(OffsetDateTime instant) {
        return OperatingHoursSchedule.isOpen(
                OperatingHoursSchedule.effective(this),
                instant.atZoneSameInstant(ZoneId.of(timezone))
        );
    }
}
